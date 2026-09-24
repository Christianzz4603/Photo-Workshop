package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.model.*
import com.photo.workspace.core.ui.CanvasAlignment
import com.photo.workspace.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesBottomSheet(
    layer: Layer,
    onUpdateText: (TextData) -> Unit,
    onUpdateShape: (ShapeData) -> Unit,
    onUpdateAdjustments: (ImageAdjustments) -> Unit,
    onUpdateCrop: (Float, Float, Float, Float) -> Unit,
    onUpdateAnimation: (ElementAnimation) -> Unit,
    onAlign: (CanvasAlignment) -> Unit,
    onDismiss: () -> Unit,
    onCommitChange: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = StudioBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Properties: ${layer.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", color = CyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Alignment Controls
            AlignmentRow(onAlign = onAlign)

            Spacer(modifier = Modifier.height(16.dp))

            // Specific layer controls
            when (layer.type) {
                LayerType.TEXT -> {
                    layer.textData?.let { textData ->
                        TextPropertiesSection(
                            textData = textData,
                            onUpdate = onUpdateText,
                            onCommit = onCommitChange
                        )
                    }
                }
                LayerType.SHAPE -> {
                    layer.shapeData?.let { shapeData ->
                        ShapePropertiesSection(
                            shapeData = shapeData,
                            onUpdate = onUpdateShape,
                            onCommit = onCommitChange
                        )
                    }
                }
                LayerType.IMAGE -> {
                    val imageData = layer.imageData ?: ImageData()
                    ImageAdjustmentsSection(
                        adjustments = imageData.adjustments,
                        onUpdate = onUpdateAdjustments,
                        onCommit = onCommitChange
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CropSection(
                        imageData = imageData,
                        onUpdateCrop = onUpdateCrop,
                        onCommit = onCommitChange
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animation Presets (presentation reveal)
            AnimationSection(
                animation = layer.animation,
                onUpdate = onUpdateAnimation
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlignmentRow(onAlign: (CanvasAlignment) -> Unit) {
    Column {
        Text("Canvas Alignment", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AlignButton(Icons.Default.AlignHorizontalLeft, "Left") { onAlign(CanvasAlignment.LEFT) }
            AlignButton(Icons.Default.AlignHorizontalCenter, "Center H") { onAlign(CanvasAlignment.CENTER_HORIZONTAL) }
            AlignButton(Icons.Default.AlignHorizontalRight, "Right") { onAlign(CanvasAlignment.RIGHT) }
            AlignButton(Icons.Default.AlignVerticalTop, "Top") { onAlign(CanvasAlignment.TOP) }
            AlignButton(Icons.Default.AlignVerticalCenter, "Center V") { onAlign(CanvasAlignment.CENTER_VERTICAL) }
            AlignButton(Icons.Default.AlignVerticalBottom, "Bottom") { onAlign(CanvasAlignment.BOTTOM) }
        }
    }
}

@Composable
private fun AlignButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(StudioCard, RoundedCornerShape(8.dp))
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TextPropertiesSection(
    textData: TextData,
    onUpdate: (TextData) -> Unit,
    onCommit: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Typography & Styling", style = MaterialTheme.typography.titleSmall, color = CyanAccent)

        // Text content field
        OutlinedTextField(
            value = textData.text,
            onValueChange = { onUpdate(textData.copy(text = it)) },
            label = { Text("Content") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = StudioBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Font Size Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size: ${textData.fontSize.toInt()}sp", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(80.dp))
            Slider(
                value = textData.fontSize,
                onValueChange = { onUpdate(textData.copy(fontSize = it)) },
                onValueChangeFinished = onCommit,
                valueRange = 14f..120f,
                colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = VioletPrimary),
                modifier = Modifier.weight(1f)
            )
        }

        // Font style buttons (Bold, Italic, Underline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = textData.isBold,
                onClick = { onUpdate(textData.copy(isBold = !textData.isBold)) },
                label = { Text("Bold") }
            )
            FilterChip(
                selected = textData.isItalic,
                onClick = { onUpdate(textData.copy(isItalic = !textData.isItalic)) },
                label = { Text("Italic") }
            )
            FilterChip(
                selected = textData.isUnderline,
                onClick = { onUpdate(textData.copy(isUnderline = !textData.isUnderline)) },
                label = { Text("Underline") }
            )
        }

        // Color Palette
        Text("Font Color", fontSize = 12.sp, color = TextSecondary)
        ColorPalettePicker(
            selectedColor = textData.fontColor,
            onSelectColor = { onUpdate(textData.copy(fontColor = it)) }
        )

        // Curved Text toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Curved / Arc Text Effect", fontSize = 13.sp, color = TextPrimary)
            Switch(
                checked = textData.isCurved,
                onCheckedChange = { onUpdate(textData.copy(isCurved = it)) }
            )
        }
    }
}

@Composable
private fun ShapePropertiesSection(
    shapeData: ShapeData,
    onUpdate: (ShapeData) -> Unit,
    onCommit: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Vector Shape Attributes", style = MaterialTheme.typography.titleSmall, color = CyanAccent)

        // Shape Type selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ShapeType.ROUNDED_RECT to "Rounded",
                ShapeType.CIRCLE to "Circle",
                ShapeType.STAR to "Star",
                ShapeType.TRIANGLE to "Triangle",
                ShapeType.HEART to "Heart",
                ShapeType.ARROW to "Arrow"
            ).forEach { (st, name) ->
                FilterChip(
                    selected = shapeData.shapeType == st,
                    onClick = { onUpdate(shapeData.copy(shapeType = st)) },
                    label = { Text(name) }
                )
            }
        }

        // Fill Color
        Text("Fill Color", fontSize = 12.sp, color = TextSecondary)
        ColorPalettePicker(
            selectedColor = shapeData.fillColor,
            onSelectColor = { onUpdate(shapeData.copy(fillColor = it)) }
        )

        // Corner Radius if rect
        if (shapeData.shapeType == ShapeType.ROUNDED_RECT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radius: ${shapeData.cornerRadius.toInt()}dp", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
                Slider(
                    value = shapeData.cornerRadius,
                    onValueChange = { onUpdate(shapeData.copy(cornerRadius = it)) },
                    onValueChangeFinished = onCommit,
                    valueRange = 0f..80f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Stroke Width
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stroke: ${shapeData.strokeWidth.toInt()}dp", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
            Slider(
                value = shapeData.strokeWidth,
                onValueChange = { onUpdate(shapeData.copy(strokeWidth = it)) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..20f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ImageAdjustmentsSection(
    adjustments: ImageAdjustments,
    onUpdate: (ImageAdjustments) -> Unit,
    onCommit: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photoshop-Style Image Adjustments", style = MaterialTheme.typography.titleSmall, color = CyanAccent)

        // One-tap filter presets (Canva-style) — each sets several adjustment fields at once
        Text("Filter Presets", fontSize = 12.sp, color = TextSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "Vivid" to ImageAdjustments(contrast = 25f, saturation = 35f),
                "B&W" to ImageAdjustments(grayscale = true, contrast = 10f),
                "Vintage" to ImageAdjustments(sepia = true, vignette = 0.35f, contrast = -10f),
                "Cool" to ImageAdjustments(hue = -20f, brightness = 5f),
                "Warm" to ImageAdjustments(hue = 15f, saturation = 10f, brightness = 5f),
                "Fade" to ImageAdjustments(contrast = -25f, brightness = 10f, vignette = 0.15f),
                "Reset" to ImageAdjustments()
            )
            presets.forEach { (label, preset) ->
                AssistChip(
                    onClick = {
                        onUpdate(preset)
                        onCommit()
                    },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        // Brightness
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness: ${adjustments.brightness.toInt()}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = adjustments.brightness,
                onValueChange = { onUpdate(adjustments.copy(brightness = it)) },
                onValueChangeFinished = onCommit,
                valueRange = -100f..100f,
                modifier = Modifier.weight(1f)
            )
        }

        // Contrast
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Contrast: ${adjustments.contrast.toInt()}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = adjustments.contrast,
                onValueChange = { onUpdate(adjustments.copy(contrast = it)) },
                onValueChangeFinished = onCommit,
                valueRange = -100f..100f,
                modifier = Modifier.weight(1f)
            )
        }

        // Saturation
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saturation: ${adjustments.saturation.toInt()}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = adjustments.saturation,
                onValueChange = { onUpdate(adjustments.copy(saturation = it)) },
                onValueChangeFinished = onCommit,
                valueRange = -100f..100f,
                modifier = Modifier.weight(1f)
            )
        }

        // Creative Filter Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = adjustments.grayscale,
                onClick = { onUpdate(adjustments.copy(grayscale = !adjustments.grayscale)) },
                label = { Text("Grayscale") }
            )
            FilterChip(
                selected = adjustments.sepia,
                onClick = { onUpdate(adjustments.copy(sepia = !adjustments.sepia)) },
                label = { Text("Sepia") }
            )
            FilterChip(
                selected = adjustments.invert,
                onClick = { onUpdate(adjustments.copy(invert = !adjustments.invert)) },
                label = { Text("Invert") }
            )
        }
    }
}

/**
 * Trim-based crop UI: each slider trims a percentage off one edge of the source image,
 * rather than exposing the raw (potentially confusing) overlapping crop-window fractions
 * directly. Internally this maps onto ImageData's cropLeft/Top/Right/Bottom (0..1 fractions).
 * Each slider is capped at 45% so opposite edges can never cross into an inverted/empty rect.
 */
@Composable
private fun CropSection(
    imageData: ImageData,
    onUpdateCrop: (Float, Float, Float, Float) -> Unit,
    onCommit: () -> Unit = {}
) {
    val trimLeft = (imageData.cropLeft * 100f)
    val trimTop = (imageData.cropTop * 100f)
    val trimRight = ((1f - imageData.cropRight) * 100f)
    val trimBottom = ((1f - imageData.cropBottom) * 100f)

    fun apply(left: Float, top: Float, right: Float, bottom: Float) {
        onUpdateCrop(left / 100f, top / 100f, 1f - right / 100f, 1f - bottom / 100f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Crop", style = MaterialTheme.typography.titleSmall, color = CyanAccent)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trim Left: ${trimLeft.toInt()}%", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = trimLeft,
                onValueChange = { apply(it, trimTop, trimRight, trimBottom) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..45f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trim Top: ${trimTop.toInt()}%", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = trimTop,
                onValueChange = { apply(trimLeft, it, trimRight, trimBottom) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..45f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trim Right: ${trimRight.toInt()}%", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = trimRight,
                onValueChange = { apply(trimLeft, trimTop, it, trimBottom) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..45f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trim Bottom: ${trimBottom.toInt()}%", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(110.dp))
            Slider(
                value = trimBottom,
                onValueChange = { apply(trimLeft, trimTop, trimRight, it) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..45f,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = { apply(0f, 0f, 0f, 0f); onCommit() }) {
            Text("Reset crop", color = CyanAccent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AnimationSection(
    animation: ElementAnimation,
    onUpdate: (ElementAnimation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Presentation Reveal Animation", style = MaterialTheme.typography.titleSmall, color = GoldAccent)
        Text("Design-element reveals for presentations and slides", fontSize = 11.sp, color = TextMuted)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimationPreset.values().forEach { preset ->
                FilterChip(
                    selected = animation.preset == preset,
                    onClick = { onUpdate(animation.copy(preset = preset)) },
                    label = { Text(preset.name.replace("_", " ")) }
                )
            }
        }
    }
}

@Composable
private fun ColorPalettePicker(
    selectedColor: Long,
    onSelectColor: (Long) -> Unit
) {
    val colors = listOf(
        0xFFFFFFFF, // White
        0xFF0F172A, // Obsidian
        0xFF8B5CF6, // Violet
        0xFF06B6D4, // Cyan
        0xFFF59E0B, // Gold
        0xFFEC4899, // Pink
        0xFF10B981, // Emerald
        0xFFEF4444, // Crimson
        0xFF3B82F6, // Blue
        0xFFF97316  // Orange
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { c ->
            val isSelected = selectedColor == c
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) CyanAccent else StudioBorder,
                        shape = CircleShape
                    )
                    .clickable { onSelectColor(c) }
            )
        }
    }
}
