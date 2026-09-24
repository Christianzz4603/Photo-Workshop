package com.photo.workspace.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.model.*
import com.photo.workspace.core.ui.EditorTool
import com.photo.workspace.ui.theme.*
import com.photo.workspace.core.util.BitmapCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CanvasView(
    page: ArtboardPage,
    selectedLayerId: String?,
    activeTool: EditorTool,
    showGrid: Boolean,
    snapToGuides: Boolean,
    onSelectLayer: (String?) -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit,
    onResizeLayer: (String, Float, Float) -> Unit,
    onAddBrushStroke: (BrushStroke) -> Unit,
    onCommitTransform: () -> Unit = {},
    penAnchors: List<PathAnchor> = emptyList(),
    onAddPenAnchor: (Float, Float, Float?, Float?) -> Unit = { _, _, _, _ -> },
    onFinishPenPath: (Boolean) -> Unit = {},
    onPickColor: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Canvas container state
    var containerWidth by remember { mutableStateOf(1000f) }
    var containerHeight by remember { mutableStateOf(1000f) }

    // Calculate scale to fit artboard neatly within available container bounds
    val scaleFactor = remember(containerWidth, containerHeight, page.width, page.height) {
        val sx = (containerWidth - 32f) / page.width.toFloat()
        val sy = (containerHeight - 32f) / page.height.toFloat()
        minOf(sx, sy, 0.95f).coerceAtLeast(0.1f)
    }

    // Pre-warm the bitmap cache off the UI thread for every image layer on this page, so the
    // synchronous BitmapCache.decodeSampled() call inside the Canvas draw phase below (which
    // cannot itself suspend) is normally a cache hit instead of blocking the UI thread with a
    // disk read + bitmap decode the first time a page is shown or an image layer is added.
    val imageLayerSizes = remember(page.layers, scaleFactor) {
        page.layers.filter { it.type == LayerType.IMAGE }
            .mapNotNull { layer ->
                val img = layer.imageData
                img?.imagePath?.takeIf { it.isNotEmpty() }?.let { path ->
                    // Must match the request size drawImagePlaceholder actually decodes at
                    // (which grows to compensate for a crop window), or this warm-up would
                    // populate a cache bucket the draw phase never reads from.
                    val cropSpanX = (img.cropRight - img.cropLeft).coerceAtLeast(0.05f)
                    val cropSpanY = (img.cropBottom - img.cropTop).coerceAtLeast(0.05f)
                    val w = ((layer.width * scaleFactor) / cropSpanX).toInt().coerceAtLeast(1)
                    val h = ((layer.height * scaleFactor) / cropSpanY).toInt().coerceAtLeast(1)
                    Triple(path, w, h)
                }
            }
    }
    LaunchedEffect(imageLayerSizes) {
        withContext(Dispatchers.Default) {
            imageLayerSizes.forEach { (path, w, h) -> BitmapCache.warm(path, w, h) }
        }
    }

    // Active brush stroke during freehand draw
    var activeBrushPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    // Pen tool: live press position + drag position while placing/curving an anchor
    var pendingPenDown by remember { mutableStateOf<Offset?>(null) }
    var pendingPenDrag by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Artboard Canvas Box
        val artboardDisplayWidth = (page.width * scaleFactor).dp
        val artboardDisplayHeight = (page.height * scaleFactor).dp

        Box(
            modifier = Modifier
                .size(artboardDisplayWidth, artboardDisplayHeight)
                .shadow(16.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Color(page.backgroundColor))
                .pointerInput(page.id, activeTool, selectedLayerId, penAnchors.size) {
                    when (activeTool) {
                        EditorTool.EYEDROPPER -> {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    onPickColor(tapOffset.x / scaleFactor, tapOffset.y / scaleFactor)
                                }
                            )
                        }
                        EditorTool.PEN -> {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                pendingPenDown = down.position
                                pendingPenDrag = down.position
                                val pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || !change.pressed) break
                                    change.consume()
                                    pendingPenDrag = change.position
                                }
                                val startPos = pendingPenDown ?: down.position
                                val endPos = pendingPenDrag ?: startPos
                                val dragVec = endPos - startPos
                                val anchorX = startPos.x / scaleFactor
                                val anchorY = startPos.y / scaleFactor
                                if (dragVec.getDistance() > 14f) {
                                    val handleOutX = anchorX + dragVec.x / scaleFactor
                                    val handleOutY = anchorY + dragVec.y / scaleFactor
                                    onAddPenAnchor(anchorX, anchorY, handleOutX, handleOutY)
                                } else {
                                    onAddPenAnchor(anchorX, anchorY, null, null)
                                }
                                pendingPenDown = null
                                pendingPenDrag = null
                            }
                        }
                        EditorTool.BRUSH -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val artboardX = offset.x / scaleFactor
                                    val artboardY = offset.y / scaleFactor
                                    activeBrushPoints = listOf(StrokePoint(artboardX, artboardY))
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val artboardX = change.position.x / scaleFactor
                                    val artboardY = change.position.y / scaleFactor
                                    activeBrushPoints = activeBrushPoints + StrokePoint(artboardX, artboardY)
                                },
                                onDragEnd = {
                                    if (activeBrushPoints.size > 1) {
                                        onAddBrushStroke(
                                            BrushStroke(
                                                points = activeBrushPoints,
                                                strokeColor = 0xFF38BDF8,
                                                strokeWidth = 10f
                                            )
                                        )
                                    }
                                    activeBrushPoints = emptyList()
                                },
                                onDragCancel = {
                                    activeBrushPoints = emptyList()
                                }
                            )
                        }
                        else -> {
                            // Select & Move gestures
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val artboardX = tapOffset.x / scaleFactor
                                    val artboardY = tapOffset.y / scaleFactor

                                    // Hit test layers from top to bottom
                                    val hitLayer = page.layers.reversed().firstOrNull { l ->
                                        l.visible && !l.locked &&
                                                artboardX >= l.x && artboardX <= (l.x + l.width) &&
                                                artboardY >= l.y && artboardY <= (l.y + l.height)
                                    }
                                    onSelectLayer(hitLayer?.id)
                                }
                            )
                        }
                    }
                }
                .pointerInput(selectedLayerId, activeTool) {
                    if (activeTool != EditorTool.BRUSH && selectedLayerId != null) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentLayer = page.layers.find { it.id == selectedLayerId }
                                if (currentLayer != null && !currentLayer.locked) {
                                    val dx = dragAmount.x / scaleFactor
                                    val dy = dragAmount.y / scaleFactor
                                    onMoveLayer(selectedLayerId, currentLayer.x + dx, currentLayer.y + dy)
                                }
                            },
                            onDragEnd = { onCommitTransform() },
                            onDragCancel = { onCommitTransform() }
                        )
                    }
                }
        ) {
            // Main Canvas rendering
            Canvas(modifier = Modifier.fillMaxSize()) {
                containerWidth = size.width / scaleFactor
                containerHeight = size.height / scaleFactor

                // 1. Draw Page Background Gradient if set
                if (page.backgroundGradientEnd != null) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(page.backgroundColor), Color(page.backgroundGradientEnd))
                        )
                    )
                }

                // 2. Grid lines if enabled
                if (showGrid) {
                    drawCanvasGrid(page.width, page.height, scaleFactor)
                }

                // 3. Draw All Layers
                page.layers.filter { it.visible }.forEach { layer ->
                    val isSelected = layer.id == selectedLayerId

                    val layerX = layer.x * scaleFactor
                    val layerY = layer.y * scaleFactor
                    val layerW = layer.width * scaleFactor
                    val layerH = layer.height * scaleFactor

                    rotate(degrees = layer.rotation, pivot = Offset(layerX + layerW / 2f, layerY + layerH / 2f)) {
                        scale(
                            scaleX = if (layer.flipHorizontal) -1f else 1f,
                            scaleY = if (layer.flipVertical) -1f else 1f,
                            pivot = Offset(layerX + layerW / 2f, layerY + layerH / 2f)
                        ) {
                            when (layer.type) {
                                LayerType.SHAPE -> drawShape(layer, layerX, layerY, layerW, layerH)
                                LayerType.TEXT -> drawText(layer, layerX, layerY, layerW, layerH, scaleFactor)
                                LayerType.IMAGE -> drawImagePlaceholder(layer, layerX, layerY, layerW, layerH)
                                LayerType.BRUSH -> drawBrushStrokes(layer, scaleFactor)
                                LayerType.VECTOR_PATH -> drawVectorPath(layer, scaleFactor)
                                LayerType.GROUP -> {}
                            }
                        }
                    }

                    // 4. Draw Transform Bounding Box for Selected Layer
                    if (isSelected) {
                        drawTransformBoundingBox(layerX, layerY, layerW, layerH, layer.rotation)
                    }
                }

                // 5. Draw currently active brush stroke
                if (activeBrushPoints.size > 1) {
                    val path = Path()
                    path.moveTo(activeBrushPoints[0].x * scaleFactor, activeBrushPoints[0].y * scaleFactor)
                    for (i in 1 until activeBrushPoints.size) {
                        val pt = activeBrushPoints[i]
                        path.lineTo(pt.x * scaleFactor, pt.y * scaleFactor)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 8f * scaleFactor, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 6. Draw in-progress pen tool anchors + connecting lines
                if (penAnchors.isNotEmpty()) {
                    drawPenPathPreview(penAnchors, scaleFactor)
                }

                // 7. Live rubber-band line while dragging out a curve handle for the next anchor
                val penDown = pendingPenDown
                val penDrag = pendingPenDrag
                if (penDown != null && penDrag != null) {
                    drawLine(
                        color = Color(0xFFEC4899),
                        start = penDown,
                        end = penDrag,
                        strokeWidth = 2f
                    )
                    drawCircle(Color(0xFFEC4899), radius = 5f, center = penDrag)
                    drawCircle(Color.White, radius = 7f, center = penDown, style = Stroke(width = 1.5f))
                }
            }

            // Interactive Resize Handles overlay if a layer is selected
            val selectedLayer = page.layers.find { it.id == selectedLayerId }
            if (selectedLayer != null && !selectedLayer.locked && activeTool == EditorTool.SELECT) {
                ResizeHandleOverlay(
                    layer = selectedLayer,
                    scaleFactor = scaleFactor,
                    onResize = { nw, nh ->
                        onResizeLayer(selectedLayer.id, nw, nh)
                    },
                    onResizeEnd = onCommitTransform
                )
            }
        }
    }
}

@Composable
private fun ResizeHandleOverlay(
    layer: Layer,
    scaleFactor: Float,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit = {}
) {
    val handleSize = 18.dp
    val layerX = (layer.x * scaleFactor).dp
    val layerY = (layer.y * scaleFactor).dp
    val layerW = (layer.width * scaleFactor).dp
    val layerH = (layer.height * scaleFactor).dp

    // Bottom-Right Corner Resize Handle
    Box(
        modifier = Modifier
            .offset(x = layerX + layerW - (handleSize / 2), y = layerY + layerH - (handleSize / 2))
            .size(handleSize)
            .shadow(4.dp, CircleShape)
            .background(CyanAccent, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .pointerInput(layer.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val nw = layer.width + (dragAmount.x / scaleFactor)
                        val nh = layer.height + (dragAmount.y / scaleFactor)
                        onResize(nw, nh)
                    },
                    onDragEnd = { onResizeEnd() },
                    onDragCancel = { onResizeEnd() }
                )
            }
    )
}

private fun DrawScope.drawCanvasGrid(pageWidth: Int, pageHeight: Int, scale: Float) {
    val step = 100f * scale
    val gridColor = Color(0x22FFFFFF)
    var x = step
    while (x < pageWidth * scale) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, pageHeight * scale), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < pageHeight * scale) {
        drawLine(gridColor, Offset(0f, y), Offset(pageWidth * scale, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawShape(layer: Layer, x: Float, y: Float, w: Float, h: Float) {
    val shape = layer.shapeData ?: return
    val fill = Color(shape.fillColor).copy(alpha = layer.opacity)
    val strokeColor = Color(shape.strokeColor).copy(alpha = layer.opacity)
    val strokeWidth = shape.strokeWidth

    when (shape.shapeType) {
        ShapeType.RECTANGLE -> {
            drawRect(fill, Offset(x, y), Size(w, h))
            if (strokeWidth > 0f) {
                drawRect(strokeColor, Offset(x, y), Size(w, h), style = Stroke(strokeWidth))
            }
        }
        ShapeType.ROUNDED_RECT -> {
            val r = androidx.compose.ui.geometry.CornerRadius(shape.cornerRadius)
            drawRoundRect(fill, Offset(x, y), Size(w, h), r)
            if (strokeWidth > 0f) {
                drawRoundRect(strokeColor, Offset(x, y), Size(w, h), r, style = Stroke(strokeWidth))
            }
        }
        ShapeType.CIRCLE -> {
            drawOval(fill, Offset(x, y), Size(w, h))
            if (strokeWidth > 0f) {
                drawOval(strokeColor, Offset(x, y), Size(w, h), style = Stroke(strokeWidth))
            }
        }
        ShapeType.STAR -> {
            val path = Path()
            val cx = x + w / 2f
            val cy = y + h / 2f
            val outerR = w / 2f
            val innerR = w / 4f
            val points = shape.starPoints.coerceIn(3, 12)
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
            drawPath(path, fill)
            if (strokeWidth > 0f) {
                drawPath(path, strokeColor, style = Stroke(strokeWidth))
            }
        }
        ShapeType.TRIANGLE -> {
            val path = Path().apply {
                moveTo(x + w / 2f, y)
                lineTo(x + w, y + h)
                lineTo(x, y + h)
                close()
            }
            drawPath(path, fill)
            if (strokeWidth > 0f) {
                drawPath(path, strokeColor, style = Stroke(strokeWidth))
            }
        }
        ShapeType.HEART -> {
            val path = Path().apply {
                moveTo(x + w / 2f, y + h * 0.85f)
                cubicTo(x + w * 0.1f, y + h * 0.6f, x, y + h * 0.25f, x + w * 0.25f, y + h * 0.1f)
                cubicTo(x + w * 0.45f, y, x + w / 2f, y + h * 0.25f, x + w / 2f, y + h * 0.25f)
                cubicTo(x + w / 2f, y + h * 0.25f, x + w * 0.55f, y, x + w * 0.75f, y + h * 0.1f)
                cubicTo(x + w, y + h * 0.25f, x + w * 0.9f, y + h * 0.6f, x + w / 2f, y + h * 0.85f)
                close()
            }
            drawPath(path, fill)
            if (strokeWidth > 0f) {
                drawPath(path, strokeColor, style = Stroke(strokeWidth))
            }
        }
        ShapeType.ARROW -> {
            val headW = w * 0.35f
            val shaftH = h * 0.35f
            val shaftY = y + (h - shaftH) / 2f
            val path = Path().apply {
                moveTo(x, shaftY)
                lineTo(x + w - headW, shaftY)
                lineTo(x + w - headW, y)
                lineTo(x + w, y + h / 2f)
                lineTo(x + w - headW, y + h)
                lineTo(x + w - headW, shaftY + shaftH)
                lineTo(x, shaftY + shaftH)
                close()
            }
            drawPath(path, fill)
            if (strokeWidth > 0f) {
                drawPath(path, strokeColor, style = Stroke(strokeWidth))
            }
        }
        else -> {
            drawRoundRect(fill, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(16f))
        }
    }
}

private fun DrawScope.drawText(layer: Layer, x: Float, y: Float, w: Float, h: Float, scale: Float) {
    val textData = layer.textData ?: return
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = textData.fontSize * scale
            color = textData.fontColor.toInt()
            alpha = (layer.opacity * 255).toInt()
            isFakeBoldText = textData.isBold
            textSkewX = if (textData.isItalic) -0.25f else 0f
            isUnderlineText = textData.isUnderline
            textAlign = when (textData.alignment) {
                "LEFT" -> android.graphics.Paint.Align.LEFT
                "RIGHT" -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.CENTER
            }
            if (textData.shadowRadius > 0f) {
                setShadowLayer(textData.shadowRadius * scale, 2f, 4f, textData.shadowColor.toInt())
            }
        }

        val posX = when (textData.alignment) {
            "LEFT" -> x
            "RIGHT" -> x + w
            else -> x + w / 2f
        }
        val posY = y + h / 2f - (paint.descent() + paint.ascent()) / 2f

        nativeCanvas.drawText(textData.text, posX, posY, paint)
    }
}

/**
 * Maps an ImageData's normalized (0..1) crop window onto actual bitmap pixel coordinates.
 * Falls back to the full bitmap on a degenerate/inverted rect rather than passing
 * Canvas.drawBitmap a zero-area or inverted Rect, which throws.
 */
private fun imageCropRect(img: ImageData?, bmpWidth: Int, bmpHeight: Int): android.graphics.Rect {
    if (img == null) return android.graphics.Rect(0, 0, bmpWidth, bmpHeight)
    val left = (img.cropLeft * bmpWidth).toInt().coerceIn(0, bmpWidth)
    val top = (img.cropTop * bmpHeight).toInt().coerceIn(0, bmpHeight)
    val right = (img.cropRight * bmpWidth).toInt().coerceIn(0, bmpWidth)
    val bottom = (img.cropBottom * bmpHeight).toInt().coerceIn(0, bmpHeight)
    return if (right > left && bottom > top) {
        android.graphics.Rect(left, top, right, bottom)
    } else {
        android.graphics.Rect(0, 0, bmpWidth, bmpHeight)
    }
}

private fun DrawScope.drawImagePlaceholder(layer: Layer, x: Float, y: Float, w: Float, h: Float) {
    val img = layer.imageData
    var bmp: Bitmap? = null
    if (img != null && img.imagePath.isNotEmpty()) {
        // If cropped, we're only ever showing a fraction of the source image in this box, so
        // request a proportionally larger decode -- otherwise the cropped-in sub-rect of an
        // already-downsampled bitmap would look blurry/blocky at the crop's effective zoom.
        val cropSpanX = (img.cropRight - img.cropLeft).coerceAtLeast(0.05f)
        val cropSpanY = (img.cropBottom - img.cropTop).coerceAtLeast(0.05f)
        val reqW = (w / cropSpanX).toInt().coerceAtLeast(1)
        val reqH = (h / cropSpanY).toInt().coerceAtLeast(1)
        bmp = BitmapCache.decodeSampled(img.imagePath, reqW, reqH)
    }

    if (bmp != null) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                alpha = (layer.opacity * 255).toInt()
            }
            val src = imageCropRect(img, bmp.width, bmp.height)
            val dst = android.graphics.RectF(x, y, x + w, y + h)
            nativeCanvas.drawBitmap(bmp, src, dst, paint)
        }
    } else {
        // Modern decorative image placeholder
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6)),
                start = Offset(x, y),
                end = Offset(x + w, y + h)
            ),
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f),
            alpha = layer.opacity
        )

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = (h * 0.15f).coerceIn(16f, 32f)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.nativeCanvas.drawText("Photo Layer", x + w / 2f, y + h / 2f + 10f, paint)
        }
    }
}

private fun DrawScope.drawBrushStrokes(layer: Layer, scale: Float) {
    val brush = layer.brushData ?: return
    brush.strokes.forEach { st ->
        if (st.points.size > 1) {
            val path = Path()
            path.moveTo(st.points[0].x * scale, st.points[0].y * scale)
            for (i in 1 until st.points.size) {
                val pt = st.points[i]
                path.lineTo(pt.x * scale, pt.y * scale)
            }
            drawPath(
                path = path,
                color = Color(st.strokeColor).copy(alpha = layer.opacity),
                style = Stroke(
                    width = st.strokeWidth * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun buildVectorPath(anchors: List<PathAnchor>, scale: Float, closed: Boolean): Path {
    val path = Path()
    if (anchors.isEmpty()) return path
    val first = anchors.first()
    path.moveTo(first.x * scale, first.y * scale)
    for (i in 1 until anchors.size) {
        val prev = anchors[i - 1]
        val curr = anchors[i]
        if (prev.handleOutX != null && curr.handleInX != null) {
            path.cubicTo(
                prev.handleOutX * scale, (prev.handleOutY ?: prev.y) * scale,
                curr.handleInX * scale, (curr.handleInY ?: curr.y) * scale,
                curr.x * scale, curr.y * scale
            )
        } else {
            path.lineTo(curr.x * scale, curr.y * scale)
        }
    }
    if (closed) path.close()
    return path
}

private fun DrawScope.drawPenPathPreview(anchors: List<PathAnchor>, scale: Float) {
    val path = buildVectorPath(anchors, scale, closed = false)
    drawPath(
        path = path,
        color = CyanAccent,
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
    )
    anchors.forEachIndexed { index, a ->
        val center = Offset(a.x * scale, a.y * scale)
        // Handle lines (Illustrator-style) for anchors that have curve handles
        if (a.handleOutX != null) {
            val h = Offset(a.handleOutX * scale, (a.handleOutY ?: a.y) * scale)
            drawLine(CyanAccent.copy(alpha = 0.6f), center, h, strokeWidth = 1.5f)
            drawCircle(CyanAccent, radius = 4f, center = h)
        }
        if (a.handleInX != null) {
            val h = Offset(a.handleInX * scale, (a.handleInY ?: a.y) * scale)
            drawLine(CyanAccent.copy(alpha = 0.6f), center, h, strokeWidth = 1.5f)
            drawCircle(CyanAccent, radius = 4f, center = h)
        }
        drawCircle(Color.White, radius = 7f, center = center)
        drawCircle(
            if (index == 0) Color(0xFFEC4899) else CyanAccent,
            radius = 5f,
            center = center
        )
    }
}

private fun DrawScope.drawVectorPath(layer: Layer, scale: Float) {
    val vec = layer.vectorPathData ?: return
    if (vec.anchors.isEmpty()) return

    val path = buildVectorPath(vec.anchors, scale, closed = vec.isClosed)
    if (vec.isClosed) {
        drawPath(path, Color(vec.fillColor).copy(alpha = layer.opacity))
    }
    if (vec.strokeWidth > 0f) {
        drawPath(
            path,
            Color(vec.strokeColor).copy(alpha = layer.opacity),
            style = Stroke(vec.strokeWidth * scale)
        )
    }
}

private fun DrawScope.drawTransformBoundingBox(x: Float, y: Float, w: Float, h: Float, rotation: Float) {
    // Selection box outline with subtle glowing stroke
    drawRect(
        color = CyanAccent,
        topLeft = Offset(x, y),
        size = Size(w, h),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
    )

    // 4 Corner indicators
    val dotR = 5f
    drawCircle(Color.White, dotR, Offset(x, y))
    drawCircle(CyanAccent, dotR - 1f, Offset(x, y))

    drawCircle(Color.White, dotR, Offset(x + w, y))
    drawCircle(CyanAccent, dotR - 1f, Offset(x + w, y))

    drawCircle(Color.White, dotR, Offset(x, y + h))
    drawCircle(CyanAccent, dotR - 1f, Offset(x, y + h))

    drawCircle(Color.White, dotR, Offset(x + w, y + h))
    drawCircle(CyanAccent, dotR - 1f, Offset(x + w, y + h))
}
