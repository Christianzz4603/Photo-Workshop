package com.photo.workspace.core.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photo.workspace.core.data.exporter.CanvasExporter
import com.photo.workspace.core.data.importer.CanvaReconstructor
import com.photo.workspace.core.data.importer.ReconstructionReport
import com.photo.workspace.core.data.model.*
import com.photo.workspace.core.data.storage.PackManager
import com.photo.workspace.core.data.storage.ProjectStorage
import com.photo.workspace.core.data.storage.WorkspaceManager
import com.photo.workspace.core.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

enum class EditorTool {
    SELECT,
    TEXT,
    SHAPE,
    BRUSH,
    PEN,
    IMAGE,
    ADJUSTMENTS,
    ANIMATION
}

enum class CanvasAlignment {
    LEFT,
    CENTER_HORIZONTAL,
    RIGHT,
    TOP,
    CENTER_VERTICAL,
    BOTTOM
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "EditorViewModel"

    val workspaceManager = WorkspaceManager(application)
    val projectStorage = ProjectStorage(workspaceManager)
    val packManager = PackManager(application, workspaceManager)
    val canvasExporter = CanvasExporter(application, workspaceManager)
    val canvaReconstructor = CanvaReconstructor()
    val pluginManager = PluginManager(application, workspaceManager)

    // Active project
    private val _project = MutableStateFlow<Project>(createDefaultStarterProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    // Selected layer
    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    // Active Tool
    private val _activeTool = MutableStateFlow(EditorTool.SELECT)
    val activeTool: StateFlow<EditorTool> = _activeTool.asStateFlow()

    // Undo / Redo stacks
    private val undoStack = LinkedList<Project>()
    private val redoStack = LinkedList<Project>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Status message for user feedback (export, import, autosave)
    private val _statusMessage = MutableStateFlow<String?>("Photo Workspace Ready • Local Workspace Active")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Canva reconstruction report
    private val _reconstructionReport = MutableStateFlow<ReconstructionReport?>(null)
    val reconstructionReport: StateFlow<ReconstructionReport?> = _reconstructionReport.asStateFlow()

    // Grid & Snapping
    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _snapToGuides = MutableStateFlow(true)
    val snapToGuides: StateFlow<Boolean> = _snapToGuides.asStateFlow()

    // Animation preview state
    private val _isAnimating = MutableStateFlow(false)
    val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()

    private val _animationProgress = MutableStateFlow(1f) // 0f to 1f
    val animationProgress: StateFlow<Float> = _animationProgress.asStateFlow()

    // Pen tool: anchors for the vector path currently being drawn (not yet committed as a layer)
    private val _penAnchors = MutableStateFlow<List<PathAnchor>>(emptyList())
    val penAnchors: StateFlow<List<PathAnchor>> = _penAnchors.asStateFlow()

    init {
        // Initialize folders and sample assets in background
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.initializeWorkspace()
            packManager.ensureStarterAssets()
            pluginManager.initialize()
            // Auto-load most recent project or keep starter
            val existing = workspaceManager.listProjects()
            if (existing.isNotEmpty()) {
                try {
                    val loaded = projectStorage.loadProject(existing.first())
                    _project.value = loaded
                    _statusMessage.value = "Loaded ${loaded.name}"
                } catch (e: Exception) {
                    Log.w(TAG, "Failed loading recent project: ${e.message}")
                }
            }
        }
        viewModelScope.launch {
            pluginManager.statusMessage.collect { message ->
                if (message != null) _statusMessage.value = message
            }
        }
    }

    fun importPluginFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = pluginManager.importPlugin(file)
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (result.isSuccess) {
                    "Plugin '${result.getOrNull()?.manifest?.name}' installed."
                } else {
                    "Plugin rejected: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    private fun pushHistory() {
        undoStack.push(_project.value.copy())
        if (undoStack.size > 30) undoStack.removeLast()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    // Some edits arrive as a continuous stream of calls from a single user gesture —
    // dragging a layer, dragging a resize handle, or dragging a Slider thumb all fire
    // dozens of updates per second. Calling pushHistory() from inside those update
    // functions (as this file used to for resize/opacity/text/shape/adjustments, and
    // never did at all for move/rotate) meant either every intermediate frame became
    // its own undo step — filling the 30-entry undo stack and evicting real history
    // after a single drag — or the gesture was never undoable at all.
    //
    // ensureHistoryCheckpoint() pushes exactly one snapshot per gesture (the state
    // *before* the gesture started) and ignores further calls until commitPendingChange()
    // is invoked once the gesture ends, at which point the next gesture can checkpoint
    // again. Discrete, single-shot actions (toggling visibility, duplicating a layer,
    // picking a blend mode, etc.) are unaffected and keep calling pushHistory() directly.
    private var historyCheckpointPending = false

    private fun ensureHistoryCheckpoint() {
        if (!historyCheckpointPending) {
            pushHistory()
            historyCheckpointPending = true
        }
    }

    /** Call once when a continuous drag/slider gesture ends, so the next gesture can checkpoint again. */
    fun commitPendingChange() {
        historyCheckpointPending = false
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.push(_project.value.copy())
            val prev = undoStack.pop()
            _project.value = prev
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            _statusMessage.value = "Undo performed"
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.push(_project.value.copy())
            val next = redoStack.pop()
            _project.value = next
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            _statusMessage.value = "Redo performed"
        }
    }

    fun setActiveTool(tool: EditorTool) {
        if (tool != EditorTool.PEN && _penAnchors.value.isNotEmpty()) {
            _penAnchors.value = emptyList()
        }
        _activeTool.value = tool
    }

    fun selectLayer(id: String?) {
        _selectedLayerId.value = id
    }

    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
    }

    fun toggleSnapping() {
        _snapToGuides.value = !_snapToGuides.value
    }

    fun getActivePage(): ArtboardPage {
        val proj = _project.value
        val index = proj.activePageIndex.coerceIn(0, proj.pages.size - 1)
        return proj.pages[index]
    }

    fun getSelectedLayer(): Layer? {
        val selId = _selectedLayerId.value ?: return null
        return getActivePage().layers.find { it.id == selId }
    }

    // Layer manipulations
    fun updateLayerPosition(layerId: String, newX: Float, newY: Float) {
        val page = getActivePage()
        val index = page.layers.indexOfFirst { it.id == layerId }
        if (index == -1) return

        val layer = page.layers[index]
        if (layer.locked) return

        ensureHistoryCheckpoint()

        var targetX = newX
        var targetY = newY

        // Smart snapping
        if (_snapToGuides.value) {
            val centerX = page.width / 2f
            val centerY = page.height / 2f
            val layerCenterX = newX + layer.width / 2f
            val layerCenterY = newY + layer.height / 2f

            // Snap to horizontal center
            if (Math.abs(layerCenterX - centerX) < 20f) {
                targetX = centerX - layer.width / 2f
            }
            // Snap to vertical center
            if (Math.abs(layerCenterY - centerY) < 20f) {
                targetY = centerY - layer.height / 2f
            }
        }

        val updated = layer.copy(x = targetX, y = targetY)
        updateLayerInPage(updated)
    }

    fun updateLayerDimensions(layerId: String, newWidth: Float, newHeight: Float) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        if (layer.locked) return

        ensureHistoryCheckpoint()
        val updated = layer.copy(
            width = newWidth.coerceAtLeast(30f),
            height = newHeight.coerceAtLeast(30f)
        )
        updateLayerInPage(updated)
    }

    fun updateLayerRotation(layerId: String, rotation: Float) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        if (layer.locked) return

        ensureHistoryCheckpoint()
        val updated = layer.copy(rotation = (rotation % 360f))
        updateLayerInPage(updated)
    }

    fun updateLayerOpacity(layerId: String, opacity: Float) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        ensureHistoryCheckpoint()
        updateLayerInPage(layer.copy(opacity = opacity.coerceIn(0f, 1f)))
    }

    fun updateLayerBlendMode(layerId: String, blendMode: BlendModeType) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(blendMode = blendMode))
    }

    fun toggleLayerVisibility(layerId: String) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(visible = !layer.visible))
    }

    fun toggleLayerLock(layerId: String) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(locked = !layer.locked))
    }

    fun flipLayerHorizontal(layerId: String) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(flipHorizontal = !layer.flipHorizontal))
    }

    fun flipLayerVertical(layerId: String) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(flipVertical = !layer.flipVertical))
    }

    fun updateTextData(layerId: String, textData: TextData) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        ensureHistoryCheckpoint()
        updateLayerInPage(layer.copy(textData = textData))
    }

    fun updateShapeData(layerId: String, shapeData: ShapeData) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        ensureHistoryCheckpoint()
        updateLayerInPage(layer.copy(shapeData = shapeData))
    }

    fun updateImageAdjustments(layerId: String, adjustments: ImageAdjustments) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        val currentImg = layer.imageData ?: ImageData()
        ensureHistoryCheckpoint()
        updateLayerInPage(layer.copy(imageData = currentImg.copy(adjustments = adjustments)))
    }

    fun updateAnimation(layerId: String, animation: ElementAnimation) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        updateLayerInPage(layer.copy(animation = animation))
    }

    fun alignLayer(layerId: String, alignment: CanvasAlignment) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        if (layer.locked) return

        pushHistory()
        val newLayer = when (alignment) {
            CanvasAlignment.LEFT -> layer.copy(x = 0f)
            CanvasAlignment.CENTER_HORIZONTAL -> layer.copy(x = (page.width - layer.width) / 2f)
            CanvasAlignment.RIGHT -> layer.copy(x = page.width - layer.width)
            CanvasAlignment.TOP -> layer.copy(y = 0f)
            CanvasAlignment.CENTER_VERTICAL -> layer.copy(y = (page.height - layer.height) / 2f)
            CanvasAlignment.BOTTOM -> layer.copy(y = page.height - layer.height)
        }
        updateLayerInPage(newLayer)
    }

    fun duplicateLayer(layerId: String) {
        val page = getActivePage()
        val layer = page.layers.find { it.id == layerId } ?: return
        pushHistory()
        val copy = layer.copy(
            id = UUID.randomUUID().toString(),
            name = "${layer.name} (Copy)",
            x = layer.x + 30f,
            y = layer.y + 30f
        )
        val newLayers = page.layers.toMutableList().apply { add(copy) }
        updatePageLayers(newLayers)
        _selectedLayerId.value = copy.id
    }

    fun deleteLayer(layerId: String) {
        val page = getActivePage()
        pushHistory()
        val newLayers = page.layers.filter { it.id != layerId }
        updatePageLayers(newLayers)
        if (_selectedLayerId.value == layerId) {
            _selectedLayerId.value = null
        }
    }

    fun moveLayerUp(layerId: String) {
        val page = getActivePage()
        val index = page.layers.indexOfFirst { it.id == layerId }
        if (index < page.layers.size - 1 && index >= 0) {
            pushHistory()
            val list = page.layers.toMutableList()
            Collections.swap(list, index, index + 1)
            updatePageLayers(list)
        }
    }

    fun moveLayerDown(layerId: String) {
        val page = getActivePage()
        val index = page.layers.indexOfFirst { it.id == layerId }
        if (index > 0) {
            pushHistory()
            val list = page.layers.toMutableList()
            Collections.swap(list, index, index - 1)
            updatePageLayers(list)
        }
    }

    // Add elements
    fun addTextLayer(text: String = "Heading Text") {
        pushHistory()
        val page = getActivePage()
        val layer = Layer(
            name = "Text: $text".take(20),
            type = LayerType.TEXT,
            x = (page.width - 400f) / 2f,
            y = (page.height - 120f) / 2f,
            width = 400f,
            height = 120f,
            textData = TextData(
                text = text,
                fontSize = 48f,
                fontColor = 0xFFFFFFFF,
                isBold = true
            )
        )
        val newLayers = page.layers.toMutableList().apply { add(layer) }
        updatePageLayers(newLayers)
        _selectedLayerId.value = layer.id
        _activeTool.value = EditorTool.SELECT
    }

    fun addShapeLayer(shapeType: ShapeType, color: Long = 0xFF8B5CF6) {
        pushHistory()
        val page = getActivePage()
        val size = 260f
        val layer = Layer(
            name = "Shape (${shapeType.name})",
            type = LayerType.SHAPE,
            x = (page.width - size) / 2f,
            y = (page.height - size) / 2f,
            width = size,
            height = size,
            shapeData = ShapeData(
                shapeType = shapeType,
                fillColor = color,
                cornerRadius = if (shapeType == ShapeType.ROUNDED_RECT) 32f else 0f
            )
        )
        val newLayers = page.layers.toMutableList().apply { add(layer) }
        updatePageLayers(newLayers)
        _selectedLayerId.value = layer.id
        _activeTool.value = EditorTool.SELECT
    }

    fun addImageLayer(imagePath: String) {
        pushHistory()
        val page = getActivePage()
        val layer = Layer(
            name = "Photo Layer",
            type = LayerType.IMAGE,
            x = (page.width - 500f) / 2f,
            y = (page.height - 380f) / 2f,
            width = 500f,
            height = 380f,
            imageData = ImageData(imagePath = imagePath)
        )
        val newLayers = page.layers.toMutableList().apply { add(layer) }
        updatePageLayers(newLayers)
        _selectedLayerId.value = layer.id
        _activeTool.value = EditorTool.SELECT
    }

    fun addBrushStroke(stroke: BrushStroke) {
        val page = getActivePage()
        // Find existing top brush layer or create one
        var brushLayer = page.layers.lastOrNull { it.type == LayerType.BRUSH }
        val newLayers = page.layers.toMutableList()

        if (brushLayer == null) {
            brushLayer = Layer(
                name = "Paint & Brush Layer",
                type = LayerType.BRUSH,
                x = 0f,
                y = 0f,
                width = page.width.toFloat(),
                height = page.height.toFloat(),
                brushData = BrushData(strokes = listOf(stroke))
            )
            newLayers.add(brushLayer)
        } else {
            val currStrokes = brushLayer.brushData?.strokes ?: emptyList()
            val updatedLayer = brushLayer.copy(
                brushData = BrushData(strokes = currStrokes + stroke)
            )
            val idx = newLayers.indexOfFirst { it.id == brushLayer!!.id }
            newLayers[idx] = updatedLayer
        }

        updatePageLayers(newLayers)
    }

    // Pen tool: click-to-place vector path anchors, committed as a VECTOR_PATH layer.
    fun addPenAnchor(x: Float, y: Float, handleOutX: Float? = null, handleOutY: Float? = null) {
        val anchor = if (handleOutX != null && handleOutY != null) {
            // Mirror the dragged-out handle to the opposite side for a smooth curve through this anchor
            PathAnchor(
                x = x, y = y,
                handleOutX = handleOutX, handleOutY = handleOutY,
                handleInX = 2 * x - handleOutX, handleInY = 2 * y - handleOutY
            )
        } else {
            PathAnchor(x = x, y = y)
        }
        _penAnchors.value = _penAnchors.value + anchor
    }

    fun undoPenAnchor() {
        val current = _penAnchors.value
        if (current.isNotEmpty()) {
            _penAnchors.value = current.dropLast(1)
        }
    }

    fun cancelPenPath() {
        _penAnchors.value = emptyList()
    }

    fun finishPenPath(closed: Boolean) {
        val anchors = _penAnchors.value
        if (anchors.size < 2) {
            _penAnchors.value = emptyList()
            return
        }
        pushHistory()
        val minX = anchors.minOf { it.x }
        val minY = anchors.minOf { it.y }
        val maxX = anchors.maxOf { it.x }
        val maxY = anchors.maxOf { it.y }
        val newLayer = Layer(
            name = "Vector Path",
            type = LayerType.VECTOR_PATH,
            x = minX,
            y = minY,
            width = (maxX - minX).coerceAtLeast(1f),
            height = (maxY - minY).coerceAtLeast(1f),
            vectorPathData = VectorPathData(
                anchors = anchors,
                isClosed = closed
            )
        )
        val page = getActivePage()
        updatePageLayers(page.layers + newLayer)
        _selectedLayerId.value = newLayer.id
        _penAnchors.value = emptyList()
        _activeTool.value = EditorTool.SELECT
        _statusMessage.value = "Vector path added"
    }

    // Multi-page Artboard management
    fun addPage(name: String? = null) {
        pushHistory()
        val proj = _project.value
        val newPage = ArtboardPage(
            name = name ?: "Page ${proj.pages.size + 1}",
            width = proj.dimensions.width,
            height = proj.dimensions.height,
            backgroundColor = 0xFF181824
        )
        val newPages = proj.pages + newPage
        _project.value = proj.copy(
            pages = newPages,
            activePageIndex = newPages.size - 1
        )
        _selectedLayerId.value = null
        _statusMessage.value = "Added ${newPage.name}"
    }

    fun setActivePage(index: Int) {
        val proj = _project.value
        if (index in proj.pages.indices) {
            _project.value = proj.copy(activePageIndex = index)
            _selectedLayerId.value = null
        }
    }

    fun duplicateActivePage() {
        pushHistory()
        val proj = _project.value
        val active = getActivePage()
        val copy = active.copy(
            id = UUID.randomUUID().toString(),
            name = "${active.name} (Copy)",
            layers = active.layers.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        val newPages = proj.pages.toMutableList().apply { add(proj.activePageIndex + 1, copy) }
        _project.value = proj.copy(
            pages = newPages,
            activePageIndex = proj.activePageIndex + 1
        )
        _statusMessage.value = "Duplicated page"
    }

    fun deleteActivePage() {
        val proj = _project.value
        if (proj.pages.size <= 1) {
            _statusMessage.value = "Cannot delete the only page"
            return
        }
        pushHistory()
        val newPages = proj.pages.toMutableList().apply { removeAt(proj.activePageIndex) }
        val newIdx = (proj.activePageIndex).coerceAtMost(newPages.size - 1)
        _project.value = proj.copy(
            pages = newPages,
            activePageIndex = newIdx
        )
        _selectedLayerId.value = null
        _statusMessage.value = "Page deleted"
    }

    // Save & Load
    fun saveProject(customName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedFile = projectStorage.saveProject(_project.value, customName)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Saved to ${savedFile.name} in PhotoWorkspace/Projects"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Save error: ${e.message}"
                }
            }
        }
    }

    fun loadProject(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = projectStorage.loadProject(file)
                withContext(Dispatchers.Main) {
                    pushHistory()
                    _project.value = loaded
                    _selectedLayerId.value = null
                    _statusMessage.value = "Loaded ${loaded.name}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to load project: ${e.message}"
                }
            }
        }
    }

    fun createNewProject(title: String, dimensions: ProjectDimensions) {
        pushHistory()
        val newProj = Project(
            name = title.ifEmpty { "Untitled Studio" },
            dimensions = dimensions,
            pages = listOf(
                ArtboardPage(
                    name = "Page 1",
                    width = dimensions.width,
                    height = dimensions.height,
                    backgroundColor = 0xFF141522
                )
            )
        )
        _project.value = newProj
        _selectedLayerId.value = null
        _statusMessage.value = "Created new project: $title"
    }

    // Export formats
    fun exportImage(format: Bitmap.CompressFormat, quality: Int = 100) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = canvasExporter.exportToImage(getActivePage(), format, quality, _project.value.name)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Exported: ${file.name} to PhotoWorkspace/Exports"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Export error: ${e.message}"
                }
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = canvasExporter.exportToPdf(_project.value)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Exported multi-page PDF to PhotoWorkspace/Exports"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "PDF Export error: ${e.message}"
                }
            }
        }
    }

    fun exportSvg() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = canvasExporter.exportToSvg(getActivePage(), _project.value.name)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Exported SVG to PhotoWorkspace/Exports"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "SVG export error: ${e.message}"
                }
            }
        }
    }

    // Import Canva reconstructor
    fun importCanvaFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (reconstructed, report) = canvaReconstructor.reconstruct(file)
                withContext(Dispatchers.Main) {
                    pushHistory()
                    _project.value = reconstructed
                    _reconstructionReport.value = report
                    _statusMessage.value = "Reconstructed ${report.reconstructedLayersCount} editable layers"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Canva import error: ${e.message}"
                }
            }
        }
    }

    // Import a pack (.mwpack)
    fun importMwPack(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = packManager.importPack(file) { progress, status ->
                viewModelScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Pack: ${(progress * 100).toInt()}% - $status"
                }
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val manifest = result.getOrNull()
                    _statusMessage.value = "Pack '${manifest?.name}' imported to local library!"
                } else {
                    _statusMessage.value = "Pack import failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun dismissReport() {
        _reconstructionReport.value = null
    }

    private fun updateLayerInPage(updatedLayer: Layer) {
        val proj = _project.value
        val page = getActivePage()
        val newLayers = page.layers.map { if (it.id == updatedLayer.id) updatedLayer else it }
        val newPage = page.copy(layers = newLayers)
        val newPages = proj.pages.toMutableList().apply { set(proj.activePageIndex, newPage) }
        _project.value = proj.copy(pages = newPages)
    }

    private fun updatePageLayers(newLayers: List<Layer>) {
        val proj = _project.value
        val page = getActivePage()
        val newPage = page.copy(layers = newLayers)
        val newPages = proj.pages.toMutableList().apply { set(proj.activePageIndex, newPage) }
        _project.value = proj.copy(pages = newPages)
    }

    private fun createDefaultStarterProject(): Project {
        val page1 = ArtboardPage(
            name = "Poster Artboard",
            width = 1080,
            height = 1080,
            backgroundColor = 0xFF0D0E15,
            layers = listOf(
                Layer(
                    name = "Geometric Backdrop",
                    type = LayerType.SHAPE,
                    x = 100f,
                    y = 100f,
                    width = 880f,
                    height = 880f,
                    opacity = 0.9f,
                    shapeData = ShapeData(
                        shapeType = ShapeType.ROUNDED_RECT,
                        fillColor = 0xFF181A28,
                        strokeColor = 0xFF2E334D,
                        strokeWidth = 4f,
                        cornerRadius = 48f
                    )
                ),
                Layer(
                    name = "Neon Gradient Sphere",
                    type = LayerType.SHAPE,
                    x = 620f,
                    y = 160f,
                    width = 300f,
                    height = 300f,
                    opacity = 0.85f,
                    blendMode = BlendModeType.SCREEN,
                    shapeData = ShapeData(
                        shapeType = ShapeType.CIRCLE,
                        fillColor = 0xFF8B5CF6
                    )
                ),
                Layer(
                    name = "Cyan Accent Star",
                    type = LayerType.SHAPE,
                    x = 180f,
                    y = 200f,
                    width = 160f,
                    height = 160f,
                    opacity = 0.95f,
                    shapeData = ShapeData(
                        shapeType = ShapeType.STAR,
                        fillColor = 0xFF06B6D4,
                        starPoints = 8
                    )
                ),
                Layer(
                    name = "Headline Text",
                    type = LayerType.TEXT,
                    x = 140f,
                    y = 420f,
                    width = 800f,
                    height = 140f,
                    textData = TextData(
                        text = "PHOTO WORKSPACE",
                        fontSize = 58f,
                        fontColor = 0xFFF8FAFC,
                        isBold = true,
                        letterSpacing = 0.08f
                    )
                ),
                Layer(
                    name = "Subtitle Text",
                    type = LayerType.TEXT,
                    x = 140f,
                    y = 570f,
                    width = 800f,
                    height = 90f,
                    textData = TextData(
                        text = "100% Offline • Local-First Vector & Creative Studio",
                        fontSize = 28f,
                        fontColor = 0xFF94A3B8,
                        isBold = false
                    )
                ),
                Layer(
                    name = "Badge CTA",
                    type = LayerType.SHAPE,
                    x = 360f,
                    y = 700f,
                    width = 360f,
                    height = 80f,
                    shapeData = ShapeData(
                        shapeType = ShapeType.ROUNDED_RECT,
                        fillColor = 0xFFEC4899,
                        cornerRadius = 40f
                    )
                ),
                Layer(
                    name = "Badge Label",
                    type = LayerType.TEXT,
                    x = 360f,
                    y = 700f,
                    width = 360f,
                    height = 80f,
                    textData = TextData(
                        text = "EXPLORE CREATIVITY",
                        fontSize = 22f,
                        fontColor = 0xFFFFFFFF,
                        isBold = true
                    )
                )
            )
        )

        return Project(
            name = "Photo Workspace Creative Poster",
            dimensions = ProjectDimensions("Square (1:1)", 1080, 1080),
            pages = listOf(page1)
        )
    }
}
