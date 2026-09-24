package com.photo.workspace.core.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photo.workspace.core.data.model.ShapeType
import com.photo.workspace.core.data.storage.WorkspaceDir
import com.photo.workspace.core.ui.components.*
import com.photo.workspace.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PhotoWorkspaceApp(
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val project by viewModel.project.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val reconstructionReport by viewModel.reconstructionReport.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()
    val snapToGuides by viewModel.snapToGuides.collectAsState()
    val penAnchors by viewModel.penAnchors.collectAsState()

    // Sheet / Dialog visibility states
    var showLayersSheet by remember { mutableStateOf(false) }
    var showPropertiesSheet by remember { mutableStateOf(false) }
    var showLibrarySheet by remember { mutableStateOf(false) }
    var showPagesSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDocsDialog by remember { mutableStateOf(false) }
    var showPluginsSheet by remember { mutableStateOf(false) }
    var openPluginPanel by remember { mutableStateOf<com.photo.workspace.core.plugin.PluginPanel?>(null) }

    val installedPlugins by viewModel.pluginManager.installedPlugins.collectAsState()
    val pluginPanels by viewModel.pluginManager.panels.collectAsState()

    // Installed packs & saved projects lists for library
    var installedPacks by remember { mutableStateOf<List<File>>(emptyList()) }
    var savedProjects by remember { mutableStateOf<List<File>>(emptyList()) }

    fun refreshStorageLists() {
        coroutineScope.launch(Dispatchers.IO) {
            val packs = viewModel.workspaceManager.listPacks()
            val projs = viewModel.workspaceManager.listProjects()
            withContext(Dispatchers.Main) {
                installedPacks = packs
                savedProjects = projs
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageLists()
    }

    // File pickers
    // 1. Image picker to insert photo layer
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val imagesDir = viewModel.workspaceManager.getDirectory(WorkspaceDir.IMAGES)
                    val destFile = File(imagesDir, "img_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(it)?.use { inStream ->
                        FileOutputStream(destFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.addImageLayer(destFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 2. Pack (.mwpack) bundle file picker
    val packPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val temp = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.mwpack")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    viewModel.importMwPack(temp)
                    refreshStorageLists()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 3. Canva export file picker (.svg, .json, .zip)
    val canvaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val temp = File(context.cacheDir, "canva_import_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    viewModel.importCanvaFile(temp)
                    refreshStorageLists()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 4. Plugin (.mwplugin) file picker
    val pluginPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val temp = File(context.cacheDir, "plugin_import_${System.currentTimeMillis()}.mwplugin")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    viewModel.importPluginFile(temp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val activePage = viewModel.getActivePage()
    val selectedLayer = viewModel.getSelectedLayer()

    Scaffold(
        topBar = {
            TopStudioBar(
                projectName = project.name,
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onSave = {
                    viewModel.saveProject()
                    refreshStorageLists()
                },
                onExport = { showExportDialog = true },
                onShowDocs = { showDocsDialog = true },
                onOpenPlugins = { showPluginsSheet = true },
                onToggleGrid = { viewModel.toggleGrid() },
                showGrid = showGrid
            )
        },
        bottomBar = {
            BottomToolBar(
                activeTool = activeTool,
                layerCount = activePage.layers.size,
                pageCount = project.pages.size,
                activePageIndex = project.activePageIndex,
                onSelectTool = { tool ->
                    viewModel.setActiveTool(tool)
                    if (tool == EditorTool.ADJUSTMENTS) {
                        showPropertiesSheet = true
                    }
                },
                onOpenLayers = { showLayersSheet = true },
                onOpenPages = { showPagesSheet = true },
                onOpenLibrary = {
                    refreshStorageLists()
                    showLibrarySheet = true
                },
                onAddText = { viewModel.addTextLayer("Tap to edit text") },
                onAddShape = { viewModel.addShapeLayer(ShapeType.ROUNDED_RECT) }
            )
        },
        containerColor = StudioObsidian
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Canvas
            CanvasView(
                page = activePage,
                selectedLayerId = selectedLayerId,
                activeTool = activeTool,
                showGrid = showGrid,
                snapToGuides = snapToGuides,
                onSelectLayer = { layerId ->
                    viewModel.selectLayer(layerId)
                    if (layerId != null && activeTool == EditorTool.ADJUSTMENTS) {
                        showPropertiesSheet = true
                    }
                },
                onMoveLayer = { id, x, y ->
                    viewModel.updateLayerPosition(id, x, y)
                },
                onResizeLayer = { id, nw, nh ->
                    viewModel.updateLayerDimensions(id, nw, nh)
                },
                onAddBrushStroke = { stroke ->
                    viewModel.addBrushStroke(stroke)
                },
                onCommitTransform = { viewModel.commitPendingChange() },
                penAnchors = penAnchors,
                onAddPenAnchor = { x, y, hx, hy -> viewModel.addPenAnchor(x, y, hx, hy) },
                onFinishPenPath = { closed -> viewModel.finishPenPath(closed) },
                onPickColor = { x, y -> viewModel.pickColorAndApply(x, y) },
                modifier = Modifier.fillMaxSize()
            )

            // Status message toast chip
            statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StudioSurface.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = CyanAccent
                    )
                }
            }

            // Pen tool in-progress controls
            if (activeTool == EditorTool.PEN && penAnchors.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StudioSurface.copy(alpha = 0.95f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.cancelPenPath() }) {
                        Text("Cancel", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { viewModel.undoPenAnchor() },
                        enabled = penAnchors.isNotEmpty()
                    ) {
                        Text("Undo point", color = CyanAccent, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.finishPenPath(false) },
                        enabled = penAnchors.size >= 2,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                    ) {
                        Text("Finish", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { viewModel.finishPenPath(true) },
                        enabled = penAnchors.size >= 3,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                    ) {
                        Text("Close path", fontSize = 12.sp)
                    }
                }
            }

            // Quick floating Inspector Trigger button if a layer is selected
            if (selectedLayer != null) {
                ExtendedFloatingActionButton(
                    onClick = { showPropertiesSheet = true },
                    containerColor = VioletPrimary,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Inspect", fontSize = 12.sp) }
                )
            }
        }
    }

    // Bottom Sheets & Dialogs

    // 1. Layers Panel
    if (showLayersSheet) {
        LayersBottomSheet(
            layers = activePage.layers,
            selectedLayerId = selectedLayerId,
            onSelectLayer = { viewModel.selectLayer(it) },
            onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
            onToggleLock = { viewModel.toggleLayerLock(it) },
            onMoveUp = { viewModel.moveLayerUp(it) },
            onMoveDown = { viewModel.moveLayerDown(it) },
            onDuplicate = { viewModel.duplicateLayer(it) },
            onDelete = { viewModel.deleteLayer(it) },
            onUpdateOpacity = { id, op -> viewModel.updateLayerOpacity(id, op) },
            onUpdateBlendMode = { id, bm -> viewModel.updateLayerBlendMode(id, bm) },
            onFlipH = { viewModel.flipLayerHorizontal(it) },
            onFlipV = { viewModel.flipLayerVertical(it) },
            onDismiss = { showLayersSheet = false },
            onCommitOpacity = { viewModel.commitPendingChange() }
        )
    }

    // 2. Properties Inspector
    if (showPropertiesSheet && selectedLayer != null) {
        PropertiesBottomSheet(
            layer = selectedLayer,
            onUpdateText = { viewModel.updateTextData(selectedLayer.id, it) },
            onUpdateShape = { viewModel.updateShapeData(selectedLayer.id, it) },
            onUpdateAdjustments = { viewModel.updateImageAdjustments(selectedLayer.id, it) },
            onUpdateCrop = { l, t, r, b -> viewModel.updateImageCrop(selectedLayer.id, l, t, r, b) },
            onUpdateAnimation = { viewModel.updateAnimation(selectedLayer.id, it) },
            onAlign = { viewModel.alignLayer(selectedLayer.id, it) },
            onDismiss = { showPropertiesSheet = false },
            onCommitChange = { viewModel.commitPendingChange() }
        )
    }

    // 3. Asset Library Sheet
    if (showLibrarySheet) {
        AssetLibrarySheet(
            installedPacks = installedPacks,
            savedProjects = savedProjects,
            onSelectTemplate = { title, dim ->
                viewModel.createNewProject(title, dim)
                showLibrarySheet = false
            },
            onAddShape = { shapeType, color ->
                viewModel.addShapeLayer(shapeType, color)
                showLibrarySheet = false
            },
            onAddText = { txt ->
                viewModel.addTextLayer(txt)
                showLibrarySheet = false
            },
            onLoadProject = { file ->
                viewModel.loadProject(file)
                showLibrarySheet = false
            },
            onImportMwPackClick = {
                packPickerLauncher.launch("*/*")
            },
            onImportCanvaClick = {
                canvaPickerLauncher.launch("*/*")
            },
            onDismiss = { showLibrarySheet = false }
        )
    }

    // 4. Pages Sheet
    if (showPagesSheet) {
        PagesBottomSheet(
            pages = project.pages,
            activePageIndex = project.activePageIndex,
            onSelectPage = { viewModel.setActivePage(it) },
            onAddPage = { viewModel.addPage() },
            onDuplicatePage = { viewModel.duplicateActivePage() },
            onDeletePage = { viewModel.deleteActivePage() },
            onDismiss = { showPagesSheet = false }
        )
    }

    // 5. Export Dialog
    if (showExportDialog) {
        ExportDialog(
            onExportImage = { format, quality ->
                viewModel.exportImage(format, quality)
            },
            onExportPdf = {
                viewModel.exportPdf()
            },
            onExportSvg = {
                viewModel.exportSvg()
            },
            onSaveMwProject = {
                viewModel.saveProject()
                refreshStorageLists()
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // 6. Docs & Roadmap Dialog
    if (showDocsDialog) {
        DocFormatDialog(
            onDismiss = { showDocsDialog = false }
        )
    }

    // 6b. Plugins Sheet
    if (showPluginsSheet) {
        PluginsBottomSheet(
            installedPlugins = installedPlugins,
            panels = pluginPanels,
            onImportClick = { pluginPickerLauncher.launch("*/*") },
            onSetEnabled = { id, enabled -> viewModel.pluginManager.setEnabled(id, enabled) },
            onRemove = { id -> viewModel.pluginManager.removePlugin(id) },
            onOpenPanel = { panel ->
                showPluginsSheet = false
                openPluginPanel = panel
            },
            onDismiss = { showPluginsSheet = false }
        )
    }

    // 6c. Plugin-contributed panel host
    openPluginPanel?.let { panel ->
        PluginPanelHost(
            panel = panel,
            onDismiss = { openPluginPanel = null }
        )
    }

    // 7. Canva Reconstruction Report Dialog
    reconstructionReport?.let { report ->
        ReconstructionReportDialog(
            report = report,
            onDismiss = { viewModel.dismissReport() }
        )
    }
}
