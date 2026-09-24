package com.photo.workspace.core.data.storage

import android.content.Context
import android.util.Log
import com.photo.workspace.core.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ProjectStorage(private val workspaceManager: WorkspaceManager) {

    private val TAG = "ProjectStorage"

    // Convert Project to JSONObject
    fun projectToJson(project: Project): JSONObject {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("version", project.version)
        root.put("createdAt", project.createdAt)
        root.put("updatedAt", project.updatedAt)
        root.put("author", project.author)
        root.put("activePageIndex", project.activePageIndex)

        val dimObj = JSONObject()
        dimObj.put("name", project.dimensions.name)
        dimObj.put("width", project.dimensions.width)
        dimObj.put("height", project.dimensions.height)
        root.put("dimensions", dimObj)

        val pagesArray = JSONArray()
        project.pages.forEach { page ->
            val pageObj = JSONObject()
            pageObj.put("id", page.id)
            pageObj.put("name", page.name)
            pageObj.put("width", page.width)
            pageObj.put("height", page.height)
            pageObj.put("backgroundColor", page.backgroundColor)
            if (page.backgroundGradientEnd != null) {
                pageObj.put("backgroundGradientEnd", page.backgroundGradientEnd)
            }

            val layersArray = JSONArray()
            page.layers.forEach { layer ->
                val layerObj = layerToJson(layer)
                layersArray.put(layerObj)
            }
            pageObj.put("layers", layersArray)
            pagesArray.put(pageObj)
        }
        root.put("pages", pagesArray)

        val metaObj = JSONObject()
        project.metadata.forEach { (k, v) -> metaObj.put(k, v) }
        root.put("metadata", metaObj)

        return root
    }

    private fun layerToJson(layer: Layer): JSONObject {
        val obj = JSONObject()
        obj.put("id", layer.id)
        obj.put("name", layer.name)
        obj.put("type", layer.type.name)
        obj.put("visible", layer.visible)
        obj.put("locked", layer.locked)
        obj.put("opacity", layer.opacity.toDouble())
        obj.put("blendMode", layer.blendMode.name)
        obj.put("x", layer.x.toDouble())
        obj.put("y", layer.y.toDouble())
        obj.put("width", layer.width.toDouble())
        obj.put("height", layer.height.toDouble())
        obj.put("rotation", layer.rotation.toDouble())
        obj.put("flipHorizontal", layer.flipHorizontal)
        obj.put("flipVertical", layer.flipVertical)

        // Text Data
        layer.textData?.let { td ->
            val textObj = JSONObject()
            textObj.put("text", td.text)
            textObj.put("fontSize", td.fontSize.toDouble())
            textObj.put("fontColor", td.fontColor)
            textObj.put("fontFamily", td.fontFamily)
            textObj.put("letterSpacing", td.letterSpacing.toDouble())
            textObj.put("lineHeight", td.lineHeight.toDouble())
            textObj.put("isBold", td.isBold)
            textObj.put("isItalic", td.isItalic)
            textObj.put("isUnderline", td.isUnderline)
            textObj.put("alignment", td.alignment)
            textObj.put("isCurved", td.isCurved)
            textObj.put("curveRadius", td.curveRadius.toDouble())
            textObj.put("shadowColor", td.shadowColor)
            textObj.put("shadowRadius", td.shadowRadius.toDouble())
            textObj.put("strokeColor", td.strokeColor)
            textObj.put("strokeWidth", td.strokeWidth.toDouble())
            obj.put("textData", textObj)
        }

        // Shape Data
        layer.shapeData?.let { sd ->
            val shapeObj = JSONObject()
            shapeObj.put("shapeType", sd.shapeType.name)
            shapeObj.put("fillColor", sd.fillColor)
            shapeObj.put("strokeColor", sd.strokeColor)
            shapeObj.put("strokeWidth", sd.strokeWidth.toDouble())
            shapeObj.put("cornerRadius", sd.cornerRadius.toDouble())
            shapeObj.put("starPoints", sd.starPoints)
            shapeObj.put("pathData", sd.pathData)
            obj.put("shapeData", shapeObj)
        }

        // Image Data
        layer.imageData?.let { id ->
            val imgObj = JSONObject()
            imgObj.put("imagePath", id.imagePath)
            id.base64Data?.let { imgObj.put("base64Data", it) }
            imgObj.put("aspectRatio", id.aspectRatio.toDouble())
            imgObj.put("cropLeft", id.cropLeft.toDouble())
            imgObj.put("cropTop", id.cropTop.toDouble())
            imgObj.put("cropRight", id.cropRight.toDouble())
            imgObj.put("cropBottom", id.cropBottom.toDouble())

            val adjObj = JSONObject()
            val adj = id.adjustments
            adjObj.put("brightness", adj.brightness.toDouble())
            adjObj.put("contrast", adj.contrast.toDouble())
            adjObj.put("saturation", adj.saturation.toDouble())
            adjObj.put("hue", adj.hue.toDouble())
            adjObj.put("exposure", adj.exposure.toDouble())
            adjObj.put("blurRadius", adj.blurRadius.toDouble())
            adjObj.put("grayscale", adj.grayscale)
            adjObj.put("sepia", adj.sepia)
            adjObj.put("invert", adj.invert)
            adjObj.put("vignette", adj.vignette.toDouble())
            imgObj.put("adjustments", adjObj)
            obj.put("imageData", imgObj)
        }

        // Brush Data
        layer.brushData?.let { bd ->
            val brushObj = JSONObject()
            val strokesArr = JSONArray()
            bd.strokes.forEach { st ->
                val stObj = JSONObject()
                stObj.put("strokeColor", st.strokeColor)
                stObj.put("strokeWidth", st.strokeWidth.toDouble())
                stObj.put("isEraser", st.isEraser)
                val ptsArr = JSONArray()
                st.points.forEach { pt ->
                    val pObj = JSONObject()
                    pObj.put("x", pt.x.toDouble())
                    pObj.put("y", pt.y.toDouble())
                    pObj.put("p", pt.pressure.toDouble())
                    ptsArr.put(pObj)
                }
                stObj.put("points", ptsArr)
                strokesArr.put(stObj)
            }
            brushObj.put("strokes", strokesArr)
            obj.put("brushData", brushObj)
        }

        // Vector Path Data
        layer.vectorPathData?.let { vd ->
            val vecObj = JSONObject()
            vecObj.put("isClosed", vd.isClosed)
            vecObj.put("fillColor", vd.fillColor)
            vecObj.put("strokeColor", vd.strokeColor)
            vecObj.put("strokeWidth", vd.strokeWidth.toDouble())
            val anchorsArr = JSONArray()
            vd.anchors.forEach { a ->
                val aObj = JSONObject()
                aObj.put("x", a.x.toDouble())
                aObj.put("y", a.y.toDouble())
                a.handleInX?.let { aObj.put("hix", it.toDouble()) }
                a.handleInY?.let { aObj.put("hiy", it.toDouble()) }
                a.handleOutX?.let { aObj.put("hox", it.toDouble()) }
                a.handleOutY?.let { aObj.put("hoy", it.toDouble()) }
                anchorsArr.put(aObj)
            }
            vecObj.put("anchors", anchorsArr)
            obj.put("vectorPathData", vecObj)
        }

        // Animation
        val animObj = JSONObject()
        animObj.put("preset", layer.animation.preset.name)
        animObj.put("durationMs", layer.animation.durationMs)
        animObj.put("delayMs", layer.animation.delayMs)
        animObj.put("isLooping", layer.animation.isLooping)
        obj.put("animation", animObj)

        return obj
    }

    // Parse Project from JSONObject
    fun projectFromJson(json: JSONObject): Project {
        val id = json.optString("id", UUID.randomUUID().toString())
        val name = json.optString("name", "Untitled Project")
        val version = json.optString("version", "1.0.0")
        val createdAt = json.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        val author = json.optString("author", "Photo Workspace Artist")
        val activePageIndex = json.optInt("activePageIndex", 0)

        val dimObj = json.optJSONObject("dimensions")
        val dimensions = if (dimObj != null) {
            ProjectDimensions(
                name = dimObj.optString("name", "Square (1:1)"),
                width = dimObj.optInt("width", 1080),
                height = dimObj.optInt("height", 1080)
            )
        } else {
            ProjectDimensions()
        }

        val pagesList = mutableListOf<ArtboardPage>()
        val pagesArray = json.optJSONArray("pages")
        if (pagesArray != null) {
            for (i in 0 until pagesArray.length()) {
                val pageObj = pagesArray.getJSONObject(i)
                val pageId = pageObj.optString("id", UUID.randomUUID().toString())
                val pageName = pageObj.optString("name", "Page ${i + 1}")
                val pageWidth = pageObj.optInt("width", dimensions.width)
                val pageHeight = pageObj.optInt("height", dimensions.height)
                val pageBg = pageObj.optLong("backgroundColor", 0xFF181824)
                val pageGradient = if (pageObj.has("backgroundGradientEnd")) pageObj.getLong("backgroundGradientEnd") else null

                val layersList = mutableListOf<Layer>()
                val layersArr = pageObj.optJSONArray("layers")
                if (layersArr != null) {
                    for (j in 0 until layersArr.length()) {
                        val layerObj = layersArr.getJSONObject(j)
                        layersList.add(layerFromJson(layerObj))
                    }
                }

                pagesList.add(
                    ArtboardPage(
                        id = pageId,
                        name = pageName,
                        width = pageWidth,
                        height = pageHeight,
                        backgroundColor = pageBg,
                        backgroundGradientEnd = pageGradient,
                        layers = layersList
                    )
                )
            }
        }

        if (pagesList.isEmpty()) {
            pagesList.add(ArtboardPage())
        }

        val metaMap = mutableMapOf<String, String>()
        val metaObj = json.optJSONObject("metadata")
        if (metaObj != null) {
            val keys = metaObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                metaMap[k] = metaObj.optString(k)
            }
        }

        return Project(
            id = id,
            name = name,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
            author = author,
            dimensions = dimensions,
            pages = pagesList,
            activePageIndex = activePageIndex.coerceIn(0, pagesList.size - 1),
            metadata = metaMap
        )
    }

    private fun layerFromJson(obj: JSONObject): Layer {
        val id = obj.optString("id", UUID.randomUUID().toString())
        val name = obj.optString("name", "Layer")
        val type = try {
            LayerType.valueOf(obj.optString("type", "SHAPE"))
        } catch (e: Exception) {
            LayerType.SHAPE
        }
        val visible = obj.optBoolean("visible", true)
        val locked = obj.optBoolean("locked", false)
        val opacity = obj.optDouble("opacity", 1.0).toFloat()
        val blendMode = try {
            BlendModeType.valueOf(obj.optString("blendMode", "NORMAL"))
        } catch (e: Exception) {
            BlendModeType.NORMAL
        }
        val x = obj.optDouble("x", 100.0).toFloat()
        val y = obj.optDouble("y", 100.0).toFloat()
        val width = obj.optDouble("width", 300.0).toFloat()
        val height = obj.optDouble("height", 200.0).toFloat()
        val rotation = obj.optDouble("rotation", 0.0).toFloat()
        val flipHorizontal = obj.optBoolean("flipHorizontal", false)
        val flipVertical = obj.optBoolean("flipVertical", false)

        var textData: TextData? = null
        val textObj = obj.optJSONObject("textData")
        if (textObj != null) {
            textData = TextData(
                text = textObj.optString("text", "Text"),
                fontSize = textObj.optDouble("fontSize", 42.0).toFloat(),
                fontColor = textObj.optLong("fontColor", 0xFFFFFFFF),
                fontFamily = textObj.optString("fontFamily", "Default"),
                letterSpacing = textObj.optDouble("letterSpacing", 0.0).toFloat(),
                lineHeight = textObj.optDouble("lineHeight", 1.2).toFloat(),
                isBold = textObj.optBoolean("isBold", false),
                isItalic = textObj.optBoolean("isItalic", false),
                isUnderline = textObj.optBoolean("isUnderline", false),
                alignment = textObj.optString("alignment", "CENTER"),
                isCurved = textObj.optBoolean("isCurved", false),
                curveRadius = textObj.optDouble("curveRadius", 150.0).toFloat(),
                shadowColor = textObj.optLong("shadowColor", 0x88000000),
                shadowRadius = textObj.optDouble("shadowRadius", 0.0).toFloat(),
                strokeColor = textObj.optLong("strokeColor", 0x00000000),
                strokeWidth = textObj.optDouble("strokeWidth", 0.0).toFloat()
            )
        }

        var shapeData: ShapeData? = null
        val shapeObj = obj.optJSONObject("shapeData")
        if (shapeObj != null) {
            val shapeType = try {
                ShapeType.valueOf(shapeObj.optString("shapeType", "ROUNDED_RECT"))
            } catch (e: Exception) {
                ShapeType.ROUNDED_RECT
            }
            shapeData = ShapeData(
                shapeType = shapeType,
                fillColor = shapeObj.optLong("fillColor", 0xFF6366F1),
                strokeColor = shapeObj.optLong("strokeColor", 0xFFFFFFFF),
                strokeWidth = shapeObj.optDouble("strokeWidth", 0.0).toFloat(),
                cornerRadius = shapeObj.optDouble("cornerRadius", 24.0).toFloat(),
                starPoints = shapeObj.optInt("starPoints", 5),
                pathData = shapeObj.optString("pathData", "")
            )
        }

        var imageData: ImageData? = null
        val imgObj = obj.optJSONObject("imageData")
        if (imgObj != null) {
            val adjObj = imgObj.optJSONObject("adjustments")
            val adjustments = if (adjObj != null) {
                ImageAdjustments(
                    brightness = adjObj.optDouble("brightness", 0.0).toFloat(),
                    contrast = adjObj.optDouble("contrast", 0.0).toFloat(),
                    saturation = adjObj.optDouble("saturation", 0.0).toFloat(),
                    hue = adjObj.optDouble("hue", 0.0).toFloat(),
                    exposure = adjObj.optDouble("exposure", 0.0).toFloat(),
                    blurRadius = adjObj.optDouble("blurRadius", 0.0).toFloat(),
                    grayscale = adjObj.optBoolean("grayscale", false),
                    sepia = adjObj.optBoolean("sepia", false),
                    invert = adjObj.optBoolean("invert", false),
                    vignette = adjObj.optDouble("vignette", 0.0).toFloat()
                )
            } else {
                ImageAdjustments()
            }
            imageData = ImageData(
                imagePath = imgObj.optString("imagePath", ""),
                base64Data = if (imgObj.has("base64Data")) imgObj.getString("base64Data") else null,
                adjustments = adjustments,
                aspectRatio = imgObj.optDouble("aspectRatio", 1.0).toFloat(),
                cropLeft = imgObj.optDouble("cropLeft", 0.0).toFloat(),
                cropTop = imgObj.optDouble("cropTop", 0.0).toFloat(),
                cropRight = imgObj.optDouble("cropRight", 1.0).toFloat(),
                cropBottom = imgObj.optDouble("cropBottom", 1.0).toFloat()
            )
        }

        var brushData: BrushData? = null
        val brushObj = obj.optJSONObject("brushData")
        if (brushObj != null) {
            val strokesList = mutableListOf<BrushStroke>()
            val strokesArr = brushObj.optJSONArray("strokes")
            if (strokesArr != null) {
                for (s in 0 until strokesArr.length()) {
                    val sObj = strokesArr.getJSONObject(s)
                    val sColor = sObj.optLong("strokeColor", 0xFF38BDF8)
                    val sWidth = sObj.optDouble("strokeWidth", 8.0).toFloat()
                    val isEraser = sObj.optBoolean("isEraser", false)
                    val ptsList = mutableListOf<StrokePoint>()
                    val ptsArr = sObj.optJSONArray("points")
                    if (ptsArr != null) {
                        for (p in 0 until ptsArr.length()) {
                            val pObj = ptsArr.getJSONObject(p)
                            ptsList.add(
                                StrokePoint(
                                    x = pObj.optDouble("x", 0.0).toFloat(),
                                    y = pObj.optDouble("y", 0.0).toFloat(),
                                    pressure = pObj.optDouble("p", 1.0).toFloat()
                                )
                            )
                        }
                    }
                    strokesList.add(BrushStroke(points = ptsList, strokeColor = sColor, strokeWidth = sWidth, isEraser = isEraser))
                }
            }
            brushData = BrushData(strokes = strokesList)
        }

        var vecData: VectorPathData? = null
        val vecObj = obj.optJSONObject("vectorPathData")
        if (vecObj != null) {
            val isClosed = vecObj.optBoolean("isClosed", true)
            val fillColor = vecObj.optLong("fillColor", 0xFFEC4899)
            val strokeColor = vecObj.optLong("strokeColor", 0xFFFFFFFF)
            val strokeWidth = vecObj.optDouble("strokeWidth", 3.0).toFloat()
            val anchorsList = mutableListOf<PathAnchor>()
            val aArr = vecObj.optJSONArray("anchors")
            if (aArr != null) {
                for (a in 0 until aArr.length()) {
                    val aObj = aArr.getJSONObject(a)
                    anchorsList.add(
                        PathAnchor(
                            x = aObj.optDouble("x", 0.0).toFloat(),
                            y = aObj.optDouble("y", 0.0).toFloat(),
                            handleInX = if (aObj.has("hix")) aObj.getDouble("hix").toFloat() else null,
                            handleInY = if (aObj.has("hiy")) aObj.getDouble("hiy").toFloat() else null,
                            handleOutX = if (aObj.has("hox")) aObj.getDouble("hox").toFloat() else null,
                            handleOutY = if (aObj.has("hoy")) aObj.getDouble("hoy").toFloat() else null
                        )
                    )
                }
            }
            vecData = VectorPathData(anchors = anchorsList, isClosed = isClosed, fillColor = fillColor, strokeColor = strokeColor, strokeWidth = strokeWidth)
        }

        val animObj = obj.optJSONObject("animation")
        val animation = if (animObj != null) {
            val preset = try {
                AnimationPreset.valueOf(animObj.optString("preset", "NONE"))
            } catch (e: Exception) {
                AnimationPreset.NONE
            }
            ElementAnimation(
                preset = preset,
                durationMs = animObj.optInt("durationMs", 800),
                delayMs = animObj.optInt("delayMs", 0),
                isLooping = animObj.optBoolean("isLooping", false)
            )
        } else {
            ElementAnimation()
        }

        return Layer(
            id = id,
            name = name,
            type = type,
            visible = visible,
            locked = locked,
            opacity = opacity,
            blendMode = blendMode,
            x = x,
            y = y,
            width = width,
            height = height,
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            textData = textData,
            shapeData = shapeData,
            imageData = imageData,
            brushData = brushData,
            vectorPathData = vecData,
            animation = animation
        )
    }

    /**
     * Save project to .mwproject file in /Projects/ directory
     */
    fun saveProject(project: Project, customFileName: String? = null): File {
        val sanitizedName = (customFileName ?: project.name)
            .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            .ifEmpty { "project_${System.currentTimeMillis()}" }
        val fileName = if (sanitizedName.endsWith(".mwproject")) sanitizedName else "$sanitizedName.mwproject"
        val projectFile = File(workspaceManager.getProjectsDir(), fileName)

        val json = projectToJson(project.copy(updatedAt = System.currentTimeMillis()))
        
        // Write atomic using a temporary file
        val tempFile = File(workspaceManager.getProjectsDir(), "$fileName.tmp")
        tempFile.writeText(json.toString(2), Charsets.UTF_8)
        if (tempFile.renameTo(projectFile)) {
            Log.d(TAG, "Project saved successfully to ${projectFile.absolutePath}")
        } else {
            // Fallback direct write
            projectFile.writeText(json.toString(2), Charsets.UTF_8)
            tempFile.delete()
        }

        // Also trigger an automatic backup snapshot
        createBackupSnapshot(project, sanitizedName)

        return projectFile
    }

    /**
     * Create backup copy in /Backups/
     */
    fun createBackupSnapshot(project: Project, baseName: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(workspaceManager.getBackupsDir(), "${baseName}_backup_$timestamp.mwproject")
            val json = projectToJson(project)
            backupFile.writeText(json.toString(2), Charsets.UTF_8)

            // Keep only the most recent 15 backups to optimize storage
            val allBackups = workspaceManager.listBackups()
            if (allBackups.size > 15) {
                allBackups.drop(15).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup: ${e.message}")
        }
    }

    /**
     * Load project from file
     */
    fun loadProject(file: File): Project {
        val content = file.readText(Charsets.UTF_8)
        val json = JSONObject(content)
        return projectFromJson(json)
    }
}
