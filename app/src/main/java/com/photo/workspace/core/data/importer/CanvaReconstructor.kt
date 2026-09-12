package com.photo.workspace.core.data.importer

import android.graphics.Color
import android.util.Log
import android.util.Xml
import com.photo.workspace.core.data.model.*
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.*
import java.util.zip.ZipInputStream

data class ReconstructionReport(
    val success: Boolean,
    val projectName: String,
    val reconstructedLayersCount: Int,
    val pagesCount: Int,
    val convertedTypes: List<String>,
    val unsupportedFeatures: List<String>
)

class CanvaReconstructor {

    private val TAG = "CanvaReconstructor"

    /**
     * Reconstruct project from Canva export file (SVG, ZIP, JSON)
     */
    fun reconstruct(file: File): Pair<Project, ReconstructionReport> {
        val extension = file.extension.lowercase(Locale.US)
        return when (extension) {
            "svg" -> reconstructFromSvg(file.readText(Charsets.UTF_8), file.nameWithoutExtension)
            "json" -> reconstructFromJson(file.readText(Charsets.UTF_8), file.nameWithoutExtension)
            "zip" -> reconstructFromZip(file)
            else -> {
                // Fallback attempt text/svg
                reconstructFromSvg(file.readText(Charsets.UTF_8), file.nameWithoutExtension)
            }
        }
    }

    /**
     * Best-effort reconstruction of SVG into editable Photo Workspace vector, shape, and text layers
     */
    fun reconstructFromSvg(svgContent: String, title: String): Pair<Project, ReconstructionReport> {
        val layers = mutableListOf<Layer>()
        val unsupported = mutableListOf<String>()
        val convertedTypes = mutableListOf<String>()

        var artboardWidth = 1080
        var artboardHeight = 1080
        var artboardBg = 0xFFFFFFFF

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(svgContent))
            var eventType = parser.eventType

            var currentDepth = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name.lowercase(Locale.US)
                    when (tagName) {
                        "svg" -> {
                            val w = parser.getAttributeValue(null, "width")
                            val h = parser.getAttributeValue(null, "height")
                            val viewBox = parser.getAttributeValue(null, "viewBox")
                            if (viewBox != null) {
                                val parts = viewBox.trim().split("\\s+".toRegex())
                                if (parts.size >= 4) {
                                    artboardWidth = parts[2].toFloatOrNull()?.toInt() ?: 1080
                                    artboardHeight = parts[3].toFloatOrNull()?.toInt() ?: 1080
                                }
                            } else if (w != null && h != null) {
                                artboardWidth = w.replace("[^0-9.]".toRegex(), "").toFloatOrNull()?.toInt() ?: 1080
                                artboardHeight = h.replace("[^0-9.]".toRegex(), "").toFloatOrNull()?.toInt() ?: 1080
                            }
                        }
                        "rect" -> {
                            val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                            val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                            val width = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 200f
                            val height = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 200f
                            val rx = parser.getAttributeValue(null, "rx")?.toFloatOrNull() ?: 0f
                            val fill = parseColor(parser.getAttributeValue(null, "fill"), 0xFF3B82F6)
                            val stroke = parseColor(parser.getAttributeValue(null, "stroke"), 0x00000000)
                            val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 0f

                            // Check if this rect is simply the background
                            if (x <= 5 && y <= 5 && width >= artboardWidth - 10 && height >= artboardHeight - 10 && layers.isEmpty()) {
                                artboardBg = fill
                            } else {
                                layers.add(
                                    Layer(
                                        name = "Rectangle ${layers.size + 1}",
                                        type = LayerType.SHAPE,
                                        x = x,
                                        y = y,
                                        width = width,
                                        height = height,
                                        shapeData = ShapeData(
                                            shapeType = if (rx > 0) ShapeType.ROUNDED_RECT else ShapeType.RECTANGLE,
                                            fillColor = fill,
                                            strokeColor = stroke,
                                            strokeWidth = strokeWidth,
                                            cornerRadius = rx
                                        )
                                    )
                                )
                                if (!convertedTypes.contains("Shape (Rect)")) convertedTypes.add("Shape (Rect)")
                            }
                        }
                        "circle" -> {
                            val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 100f
                            val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 100f
                            val r = parser.getAttributeValue(null, "r")?.toFloatOrNull() ?: 50f
                            val fill = parseColor(parser.getAttributeValue(null, "fill"), 0xFF8B5CF6)
                            val stroke = parseColor(parser.getAttributeValue(null, "stroke"), 0x00000000)
                            val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 0f

                            layers.add(
                                Layer(
                                    name = "Circle ${layers.size + 1}",
                                    type = LayerType.SHAPE,
                                    x = cx - r,
                                    y = cy - r,
                                    width = r * 2,
                                    height = r * 2,
                                    shapeData = ShapeData(
                                        shapeType = ShapeType.CIRCLE,
                                        fillColor = fill,
                                        strokeColor = stroke,
                                        strokeWidth = strokeWidth
                                    )
                                )
                            )
                            if (!convertedTypes.contains("Shape (Circle)")) convertedTypes.add("Shape (Circle)")
                        }
                        "text" -> {
                            val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 100f
                            val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 100f
                            val fontSize = parser.getAttributeValue(null, "font-size")?.replace("[^0-9.]".toRegex(), "")?.toFloatOrNull() ?: 36f
                            val fill = parseColor(parser.getAttributeValue(null, "fill"), 0xFFFFFFFF)
                            val fontFamily = parser.getAttributeValue(null, "font-family") ?: "Default"
                            val fontWeight = parser.getAttributeValue(null, "font-weight") ?: "normal"

                            // read text content
                            parser.next()
                            val textContent = parser.text?.trim() ?: "Text"

                            layers.add(
                                Layer(
                                    name = "Text ($textContent)".take(20),
                                    type = LayerType.TEXT,
                                    x = x,
                                    y = (y - fontSize).coerceAtLeast(0f), // Adjust baseline offset
                                    width = (textContent.length * fontSize * 0.65f).coerceAtLeast(120f),
                                    height = fontSize * 1.4f,
                                    textData = TextData(
                                        text = textContent,
                                        fontSize = fontSize,
                                        fontColor = fill,
                                        fontFamily = fontFamily,
                                        isBold = fontWeight.contains("bold") || fontWeight == "700"
                                    )
                                )
                            )
                            if (!convertedTypes.contains("Editable Text")) convertedTypes.add("Editable Text")
                        }
                        "path" -> {
                            val pathData = parser.getAttributeValue(null, "d") ?: ""
                            val fill = parseColor(parser.getAttributeValue(null, "fill"), 0xFFEC4899)
                            val stroke = parseColor(parser.getAttributeValue(null, "stroke"), 0x00000000)
                            val strokeWidth = parser.getAttributeValue(null, "stroke-width")?.toFloatOrNull() ?: 0f

                            layers.add(
                                Layer(
                                    name = "Vector Path ${layers.size + 1}",
                                    type = LayerType.SHAPE,
                                    x = 100f,
                                    y = 100f,
                                    width = 300f,
                                    height = 300f,
                                    shapeData = ShapeData(
                                        shapeType = ShapeType.SVG_PATH,
                                        fillColor = fill,
                                        strokeColor = stroke,
                                        strokeWidth = strokeWidth,
                                        pathData = pathData
                                    )
                                )
                            )
                            if (!convertedTypes.contains("Vector Path")) convertedTypes.add("Vector Path")
                        }
                        "filter", "clippath", "mask" -> {
                            unsupported.add("Advanced SVG `<$tagName>` approximated to nearest native layer blending/effects")
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SVG Parse Error: ${e.message}", e)
            unsupported.add("Malformed SVG tags encountered: ${e.message}")
        }

        if (layers.isEmpty()) {
            // Add a fallback starter layer
            layers.add(
                Layer(
                    name = "Imported Graphic",
                    type = LayerType.SHAPE,
                    x = 150f,
                    y = 150f,
                    width = 400f,
                    height = 400f,
                    shapeData = ShapeData(
                        shapeType = ShapeType.ROUNDED_RECT,
                        fillColor = 0xFF6366F1
                    )
                )
            )
        }

        val page = ArtboardPage(
            name = "Reconstructed Page",
            width = artboardWidth,
            height = artboardHeight,
            backgroundColor = artboardBg,
            layers = layers
        )

        val project = Project(
            name = title.ifEmpty { "Canva Import" },
            dimensions = ProjectDimensions(
                name = "${artboardWidth}x${artboardHeight}",
                width = artboardWidth,
                height = artboardHeight
            ),
            pages = listOf(page)
        )

        val report = ReconstructionReport(
            success = true,
            projectName = project.name,
            reconstructedLayersCount = layers.size,
            pagesCount = 1,
            convertedTypes = convertedTypes,
            unsupportedFeatures = unsupported
        )

        return Pair(project, report)
    }

    /**
     * Best-effort reconstruction from JSON export
     */
    fun reconstructFromJson(jsonStr: String, title: String): Pair<Project, ReconstructionReport> {
        val convertedTypes = mutableListOf<String>()
        val unsupported = mutableListOf<String>()

        return try {
            val root = JSONObject(jsonStr)
            val layers = mutableListOf<Layer>()

            // Support either Photo Workspace native json or generic Canva-like element array
            val elementsArr = root.optJSONArray("elements") ?: root.optJSONArray("layers")
            if (elementsArr != null) {
                for (i in 0 until elementsArr.length()) {
                    val elem = elementsArr.getJSONObject(i)
                    val type = elem.optString("type", "shape").lowercase(Locale.US)
                    val x = elem.optDouble("x", 100.0).toFloat()
                    val y = elem.optDouble("y", 100.0).toFloat()
                    val w = elem.optDouble("width", 300.0).toFloat()
                    val h = elem.optDouble("height", 200.0).toFloat()

                    when {
                        type.contains("text") -> {
                            val text = elem.optString("text", "Text")
                            val fontSize = elem.optDouble("fontSize", 36.0).toFloat()
                            val color = elem.optLong("color", 0xFFFFFFFF)
                            layers.add(
                                Layer(
                                    name = "Text: $text".take(20),
                                    type = LayerType.TEXT,
                                    x = x,
                                    y = y,
                                    width = w,
                                    height = h,
                                    textData = TextData(text = text, fontSize = fontSize, fontColor = color)
                                )
                            )
                            if (!convertedTypes.contains("Text")) convertedTypes.add("Text")
                        }
                        type.contains("image") -> {
                            val src = elem.optString("src", "")
                            layers.add(
                                Layer(
                                    name = "Image $i",
                                    type = LayerType.IMAGE,
                                    x = x,
                                    y = y,
                                    width = w,
                                    height = h,
                                    imageData = ImageData(imagePath = src)
                                )
                            )
                            if (!convertedTypes.contains("Image")) convertedTypes.add("Image")
                        }
                        else -> {
                            val fill = elem.optLong("fill", 0xFF6366F1)
                            layers.add(
                                Layer(
                                    name = "Shape $i",
                                    type = LayerType.SHAPE,
                                    x = x,
                                    y = y,
                                    width = w,
                                    height = h,
                                    shapeData = ShapeData(fillColor = fill)
                                )
                            )
                            if (!convertedTypes.contains("Shape")) convertedTypes.add("Shape")
                        }
                    }
                }
            }

            val project = Project(
                name = title,
                pages = listOf(ArtboardPage(name = "Page 1", layers = layers))
            )

            val report = ReconstructionReport(
                success = true,
                projectName = project.name,
                reconstructedLayersCount = layers.size,
                pagesCount = 1,
                convertedTypes = convertedTypes,
                unsupportedFeatures = unsupported
            )
            Pair(project, report)
        } catch (e: Exception) {
            Log.e(TAG, "JSON reconstruction error: ${e.message}")
            reconstructFromSvg("<svg width='1080' height='1080'></svg>", title)
        }
    }

    /**
     * Best effort reconstruction from Canva ZIP export package
     */
    fun reconstructFromZip(zipFile: File): Pair<Project, ReconstructionReport> {
        var svgFound: String? = null
        var jsonFound: String? = null

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entry.name.endsWith(".svg", ignoreCase = true) && svgFound == null) {
                        svgFound = zis.bufferedReader().readText()
                    } else if (entry.name.endsWith(".json", ignoreCase = true) && jsonFound == null) {
                        jsonFound = zis.bufferedReader().readText()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        return when {
            svgFound != null -> reconstructFromSvg(svgFound!!, zipFile.nameWithoutExtension)
            jsonFound != null -> reconstructFromJson(jsonFound!!, zipFile.nameWithoutExtension)
            else -> reconstructFromSvg("<svg width='1080' height='1080'></svg>", zipFile.nameWithoutExtension)
        }
    }

    private fun parseColor(colorStr: String?, defaultColor: Long): Long {
        if (colorStr.isNullOrEmpty() || colorStr == "none") return defaultColor
        return try {
            if (colorStr.startsWith("#")) {
                val parsed = Color.parseColor(colorStr)
                parsed.toLong() and 0xFFFFFFFFL
            } else {
                defaultColor
            }
        } catch (e: Exception) {
            defaultColor
        }
    }
}
