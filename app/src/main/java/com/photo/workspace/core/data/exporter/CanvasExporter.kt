package com.photo.workspace.core.data.exporter

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.photo.workspace.core.data.model.*
import com.photo.workspace.core.data.storage.WorkspaceManager
import com.photo.workspace.core.util.BitmapCache
import java.io.File
import java.io.FileOutputStream
import java.util.*

class CanvasExporter(
    private val context: Context,
    private val workspaceManager: WorkspaceManager
) {
    private val TAG = "CanvasExporter"

    /**
     * Render an artboard page to an Android Bitmap
     */
    fun renderPageToBitmap(page: ArtboardPage, scale: Float = 1.0f): Bitmap {
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Scale canvas coordinate system
        canvas.scale(scale, scale)

        // Draw background
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = page.backgroundColor.toInt()
            style = Paint.Style.FILL
        }

        if (page.backgroundGradientEnd != null) {
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, page.height.toFloat(),
                page.backgroundColor.toInt(), page.backgroundGradientEnd.toInt(),
                Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, page.width.toFloat(), page.height.toFloat(), bgPaint)

        // Draw each layer from bottom to top
        page.layers.filter { it.visible }.forEach { layer ->
            drawLayer(canvas, layer)
        }

        return bitmap
    }

    private fun drawLayer(canvas: Canvas, layer: Layer) {
        canvas.save()

        // Translation and transforms
        canvas.translate(layer.x, layer.y)
        val centerX = layer.width / 2f
        val centerY = layer.height / 2f

        if (layer.rotation != 0f) {
            canvas.rotate(layer.rotation, centerX, centerY)
        }

        if (layer.flipHorizontal || layer.flipVertical) {
            val sx = if (layer.flipHorizontal) -1f else 1f
            val sy = if (layer.flipVertical) -1f else 1f
            canvas.scale(sx, sy, centerX, centerY)
        }

        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()

        // Layer types
        when (layer.type) {
            LayerType.SHAPE -> drawShapeLayer(canvas, layer, alphaInt)
            LayerType.TEXT -> drawTextLayer(canvas, layer, alphaInt)
            LayerType.IMAGE -> drawImageLayer(canvas, layer, alphaInt)
            LayerType.BRUSH -> drawBrushLayer(canvas, layer, alphaInt)
            LayerType.VECTOR_PATH -> drawVectorPathLayer(canvas, layer, alphaInt)
            LayerType.GROUP -> {}
        }

        canvas.restore()
    }

    private fun drawShapeLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val shape = layer.shapeData ?: return
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = shape.fillColor.toInt()
            this.alpha = (Color.alpha(shape.fillColor.toInt()) * (alpha / 255f)).toInt()
            style = Paint.Style.FILL
            xfermode = getXfermode(layer.blendMode)
        }

        val strokePaint = if (shape.strokeWidth > 0f) {
            Paint().apply {
                isAntiAlias = true
                color = shape.strokeColor.toInt()
                this.alpha = (Color.alpha(shape.strokeColor.toInt()) * (alpha / 255f)).toInt()
                style = Paint.Style.STROKE
                strokeWidth = shape.strokeWidth
            }
        } else null

        val w = layer.width
        val h = layer.height

        when (shape.shapeType) {
            ShapeType.RECTANGLE -> {
                canvas.drawRect(0f, 0f, w, h, fillPaint)
                strokePaint?.let { canvas.drawRect(0f, 0f, w, h, it) }
            }
            ShapeType.ROUNDED_RECT -> {
                val r = shape.cornerRadius
                val rectF = RectF(0f, 0f, w, h)
                canvas.drawRoundRect(rectF, r, r, fillPaint)
                strokePaint?.let { canvas.drawRoundRect(rectF, r, r, it) }
            }
            ShapeType.CIRCLE -> {
                val rectF = RectF(0f, 0f, w, h)
                canvas.drawOval(rectF, fillPaint)
                strokePaint?.let { canvas.drawOval(rectF, it) }
            }
            ShapeType.STAR -> {
                val path = createStarPath(w / 2f, h / 2f, w / 2f, w / 4f, shape.starPoints.coerceIn(3, 12))
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.HEART -> {
                val path = createHeartPath(w, h)
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.ARROW -> {
                val path = createArrowPath(w, h)
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.SPEECH_BUBBLE -> {
                val path = createBubblePath(w, h)
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.POLYGON -> {
                val path = createPolygonPath(w / 2f, h / 2f, minOf(w, h) / 2f, 6)
                canvas.drawPath(path, fillPaint)
                strokePaint?.let { canvas.drawPath(path, it) }
            }
            ShapeType.SVG_PATH -> {
                val rectF = RectF(0f, 0f, w, h)
                canvas.drawRoundRect(rectF, 16f, 16f, fillPaint)
                strokePaint?.let { canvas.drawRoundRect(rectF, 16f, 16f, it) }
            }
        }
    }

    private fun drawTextLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val text = layer.textData ?: return
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = text.fontSize
            color = text.fontColor.toInt()
            this.alpha = (Color.alpha(text.fontColor.toInt()) * (alpha / 255f)).toInt()
            letterSpacing = text.letterSpacing
            isFakeBoldText = text.isBold
            textSkewX = if (text.isItalic) -0.25f else 0f
            isUnderlineText = text.isUnderline
            textAlign = when (text.alignment) {
                "LEFT" -> Paint.Align.LEFT
                "RIGHT" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            if (text.shadowRadius > 0f) {
                setShadowLayer(text.shadowRadius, 2f, 4f, text.shadowColor.toInt())
            }
            xfermode = getXfermode(layer.blendMode)
        }

        val posX = when (text.alignment) {
            "LEFT" -> 0f
            "RIGHT" -> layer.width
            else -> layer.width / 2f
        }
        val posY = layer.height / 2f - (paint.descent() + paint.ascent()) / 2f

        if (text.isCurved) {
            val path = Path().apply {
                val r = text.curveRadius
                addArc(RectF(posX - r, posY - r, posX + r, posY + r), 180f, 180f)
            }
            canvas.drawTextOnPath(text.text, path, 0f, 0f, paint)
        } else {
            canvas.drawText(text.text, posX, posY, paint)
        }

        // Draw outline if requested
        if (text.strokeWidth > 0f) {
            val strokePaint = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = text.strokeWidth
                color = text.strokeColor.toInt()
                clearShadowLayer()
            }
            if (!text.isCurved) {
                canvas.drawText(text.text, posX, posY, strokePaint)
            }
        }
    }

    /**
     * Maps an ImageData's normalized (0..1) crop window onto actual bitmap pixel coordinates,
     * guarding against a degenerate/inverted rect (e.g. corrupted project data) by falling back
     * to the full bitmap rather than passing Canvas.drawBitmap a zero-area or inverted Rect,
     * which throws.
     */
    private fun cropRectFor(img: ImageData, bmpWidth: Int, bmpHeight: Int): Rect {
        val left = (img.cropLeft * bmpWidth).toInt().coerceIn(0, bmpWidth)
        val top = (img.cropTop * bmpHeight).toInt().coerceIn(0, bmpHeight)
        val right = (img.cropRight * bmpWidth).toInt().coerceIn(0, bmpWidth)
        val bottom = (img.cropBottom * bmpHeight).toInt().coerceIn(0, bmpHeight)
        return if (right > left && bottom > top) {
            Rect(left, top, right, bottom)
        } else {
            Rect(0, 0, bmpWidth, bmpHeight)
        }
    }

    private fun drawImageLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val img = layer.imageData ?: return
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            this.alpha = alpha
            xfermode = getXfermode(layer.blendMode)
            colorFilter = buildColorMatrixFilter(img.adjustments)
        }

        // Full resolution for export quality; still cached so a multi-page export reusing
        // the same source image (e.g. a logo placed on every page) only decodes it once.
        val bmp: Bitmap? = if (img.imagePath.isNotEmpty()) {
            BitmapCache.decodeSampled(img.imagePath, Int.MAX_VALUE, Int.MAX_VALUE)
        } else null

        if (bmp != null) {
            val src = cropRectFor(img, bmp.width, bmp.height)
            val dst = RectF(0f, 0f, layer.width, layer.height)
            canvas.drawBitmap(bmp, src, dst, paint)
        } else {
            // Draw placeholder graphic with artistic gradient
            val placeholderPaint = Paint().apply {
                isAntiAlias = true
                shader = LinearGradient(
                    0f, 0f, layer.width, layer.height,
                    Color.rgb(56, 189, 248), Color.rgb(168, 85, 247),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, layer.width, layer.height), 20f, 20f, placeholderPaint)

            // Inner icon
            val iconPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Image Layer", layer.width / 2f, layer.height / 2f + 10f, iconPaint)
        }
    }

    private fun drawBrushLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val brush = layer.brushData ?: return
        brush.strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = stroke.strokeColor.toInt()
                    this.alpha = (Color.alpha(stroke.strokeColor.toInt()) * (alpha / 255f)).toInt()
                    strokeWidth = stroke.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    if (stroke.isEraser) {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    }
                }

                val path = Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    val midX = (prev.x + curr.x) / 2f
                    val midY = (prev.y + curr.y) / 2f
                    path.quadTo(prev.x, prev.y, midX, midY)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawVectorPathLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val vec = layer.vectorPathData ?: return
        if (vec.anchors.isEmpty()) return

        val path = Path()
        val first = vec.anchors.first()
        path.moveTo(first.x, first.y)

        for (i in 1 until vec.anchors.size) {
            val curr = vec.anchors[i]
            val prev = vec.anchors[i - 1]
            if (prev.handleOutX != null && curr.handleInX != null) {
                path.cubicTo(
                    prev.handleOutX, prev.handleOutY ?: prev.y,
                    curr.handleInX, curr.handleInY ?: curr.y,
                    curr.x, curr.y
                )
            } else {
                path.lineTo(curr.x, curr.y)
            }
        }

        if (vec.isClosed) {
            path.close()
            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = vec.fillColor.toInt()
                this.alpha = (Color.alpha(vec.fillColor.toInt()) * (alpha / 255f)).toInt()
            }
            canvas.drawPath(path, fillPaint)
        }

        if (vec.strokeWidth > 0f) {
            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = vec.strokeColor.toInt()
                strokeWidth = vec.strokeWidth
                this.alpha = (Color.alpha(vec.strokeColor.toInt()) * (alpha / 255f)).toInt()
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun buildColorMatrixFilter(adj: ImageAdjustments): ColorMatrixColorFilter {
        val matrix = ColorMatrix()

        // Saturation
        if (adj.grayscale) {
            matrix.setSaturation(0f)
        } else if (adj.saturation != 0f) {
            val satRatio = 1f + (adj.saturation / 100f)
            matrix.setSaturation(satRatio.coerceAtLeast(0f))
        }

        // Brightness & Contrast
        if (adj.brightness != 0f || adj.contrast != 0f) {
            val contrast = 1f + (adj.contrast / 100f)
            val brightness = adj.brightness * 1.5f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(cm)
        }

        // Invert
        if (adj.invert) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(invertMatrix)
        }

        // Sepia
        if (adj.sepia) {
            val sepiaMatrix = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(sepiaMatrix)
        }

        return ColorMatrixColorFilter(matrix)
    }

    private fun getXfermode(blendMode: BlendModeType): Xfermode? {
        return when (blendMode) {
            BlendModeType.NORMAL -> null
            BlendModeType.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendModeType.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            BlendModeType.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            BlendModeType.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendModeType.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            BlendModeType.COLOR_DODGE -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            BlendModeType.DIFFERENCE -> PorterDuffXfermode(PorterDuff.Mode.XOR)
        }
    }

    // Path generators
    private fun createStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float, points: Int): Path {
        val path = Path()
        val step = Math.PI / points
        var angle = -Math.PI / 2.0
        path.moveTo((cx + outerR * Math.cos(angle)).toFloat(), (cy + outerR * Math.sin(angle)).toFloat())

        for (i in 0 until points) {
            angle += step
            path.lineTo((cx + innerR * Math.cos(angle)).toFloat(), (cy + innerR * Math.sin(angle)).toFloat())
            angle += step
            path.lineTo((cx + outerR * Math.cos(angle)).toFloat(), (cy + outerR * Math.sin(angle)).toFloat())
        }
        path.close()
        return path
    }

    private fun createPolygonPath(cx: Float, cy: Float, r: Float, sides: Int): Path {
        val path = Path()
        val step = 2 * Math.PI / sides
        var angle = -Math.PI / 2.0
        path.moveTo((cx + r * Math.cos(angle)).toFloat(), (cy + r * Math.sin(angle)).toFloat())
        for (i in 1 until sides) {
            angle += step
            path.lineTo((cx + r * Math.cos(angle)).toFloat(), (cy + r * Math.sin(angle)).toFloat())
        }
        path.close()
        return path
    }

    private fun createHeartPath(w: Float, h: Float): Path {
        val path = Path()
        path.moveTo(w / 2f, h * 0.85f)
        path.cubicTo(w * 0.1f, h * 0.6f, 0f, h * 0.25f, w * 0.25f, h * 0.1f)
        path.cubicTo(w * 0.45f, 0f, w / 2f, h * 0.25f, w / 2f, h * 0.25f)
        path.cubicTo(w / 2f, h * 0.25f, w * 0.55f, 0f, w * 0.75f, h * 0.1f)
        path.cubicTo(w, h * 0.25f, w * 0.9f, h * 0.6f, w / 2f, h * 0.85f)
        path.close()
        return path
    }

    private fun createArrowPath(w: Float, h: Float): Path {
        val path = Path()
        val headW = w * 0.35f
        val shaftH = h * 0.35f
        val shaftY = (h - shaftH) / 2f
        path.moveTo(0f, shaftY)
        path.lineTo(w - headW, shaftY)
        path.lineTo(w - headW, 0f)
        path.lineTo(w, h / 2f)
        path.lineTo(w - headW, h)
        path.lineTo(w - headW, shaftY + shaftH)
        path.lineTo(0f, shaftY + shaftH)
        path.close()
        return path
    }

    private fun createBubblePath(w: Float, h: Float): Path {
        val path = Path()
        val r = 20f
        val bodyH = h * 0.75f
        path.addRoundRect(RectF(0f, 0f, w, bodyH), r, r, Path.Direction.CW)
        // Tail
        path.moveTo(w * 0.25f, bodyH)
        path.lineTo(w * 0.15f, h)
        path.lineTo(w * 0.45f, bodyH)
        return path
    }

    /**
     * Export page as Image (PNG, JPG, WebP)
     */
    fun exportToImage(page: ArtboardPage, format: Bitmap.CompressFormat, quality: Int = 100, customName: String? = null): File {
        val bitmap = renderPageToBitmap(page, 1.0f)
        val ext = when (format) {
            Bitmap.CompressFormat.PNG -> "png"
            Bitmap.CompressFormat.JPEG -> "jpg"
            else -> "webp"
        }
        val fileName = (customName ?: page.name.replace("\\s+".toRegex(), "_")) + "_${System.currentTimeMillis()}.$ext"
        val outFile = File(workspaceManager.getExportsDir(), fileName)

        FileOutputStream(outFile).use { fos ->
            bitmap.compress(format, quality, fos)
        }
        return outFile
    }

    /**
     * Export multi-page project to PDF using native Android PdfDocument
     */
    fun exportToPdf(project: Project, customName: String? = null): File {
        val pdfDoc = PdfDocument()
        val fileName = (customName ?: project.name.replace("\\s+".toRegex(), "_")) + "_${System.currentTimeMillis()}.pdf"
        val outFile = File(workspaceManager.getExportsDir(), fileName)

        try {
            project.pages.forEachIndexed { index, page ->
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Draw background
                val bgPaint = Paint().apply {
                    color = page.backgroundColor.toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, page.width.toFloat(), page.height.toFloat(), bgPaint)

                // Draw layers
                page.layers.filter { it.visible }.forEach { layer ->
                    drawLayer(canvas, layer)
                }

                pdfDoc.finishPage(pdfPage)
            }

            FileOutputStream(outFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
        } finally {
            pdfDoc.close()
        }

        return outFile
    }

    /**
     * Export SVG format string
     */
    fun exportToSvg(page: ArtboardPage, customName: String? = null): File {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${page.width}\" height=\"${page.height}\" viewBox=\"0 0 ${page.width} ${page.height}\">\n")
        
        // Background
        val hexBg = String.format("#%06X", (0xFFFFFF and page.backgroundColor.toInt()))
        sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"$hexBg\"/>\n")

        page.layers.filter { it.visible }.forEach { l ->
            when (l.type) {
                LayerType.SHAPE -> {
                    val s = l.shapeData
                    val fill = s?.fillColor?.let { String.format("#%06X", (0xFFFFFF and it.toInt())) } ?: "#6366F1"
                    when (s?.shapeType) {
                        ShapeType.CIRCLE -> {
                            val r = minOf(l.width, l.height) / 2f
                            sb.append("  <circle cx=\"${l.x + r}\" cy=\"${l.y + r}\" r=\"$r\" fill=\"$fill\" opacity=\"${l.opacity}\"/>\n")
                        }
                        ShapeType.ROUNDED_RECT -> {
                            sb.append("  <rect x=\"${l.x}\" y=\"${l.y}\" width=\"${l.width}\" height=\"${l.height}\" rx=\"${s.cornerRadius}\" fill=\"$fill\" opacity=\"${l.opacity}\"/>\n")
                        }
                        else -> {
                            sb.append("  <rect x=\"${l.x}\" y=\"${l.y}\" width=\"${l.width}\" height=\"${l.height}\" fill=\"$fill\" opacity=\"${l.opacity}\"/>\n")
                        }
                    }
                }
                LayerType.TEXT -> {
                    val t = l.textData
                    val fill = t?.fontColor?.let { String.format("#%06X", (0xFFFFFF and it.toInt())) } ?: "#FFFFFF"
                    sb.append("  <text x=\"${l.x + l.width / 2}\" y=\"${l.y + l.height / 2}\" font-size=\"${t?.fontSize ?: 36f}\" fill=\"$fill\" text-anchor=\"middle\" opacity=\"${l.opacity}\">${t?.text ?: ""}</text>\n")
                }
                else -> {}
            }
        }
        sb.append("</svg>\n")

        val fileName = (customName ?: page.name.replace("\\s+".toRegex(), "_")) + "_${System.currentTimeMillis()}.svg"
        val outFile = File(workspaceManager.getExportsDir(), fileName)
        outFile.writeText(sb.toString(), Charsets.UTF_8)
        return outFile
    }
}
