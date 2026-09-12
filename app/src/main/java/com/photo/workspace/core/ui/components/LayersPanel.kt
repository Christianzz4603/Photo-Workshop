package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.model.BlendModeType
import com.photo.workspace.core.data.model.Layer
import com.photo.workspace.core.data.model.LayerType
import com.photo.workspace.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    layers: List<Layer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateOpacity: (String, Float) -> Unit,
    onUpdateBlendMode: (String, BlendModeType) -> Unit,
    onFlipH: (String) -> Unit,
    onFlipV: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommitOpacity: () -> Unit = {}
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
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Layers",
                        tint = VioletPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Layers Stack (${layers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Done", color = CyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected Layer Opacity & Blend Mode Bar if a layer is selected
            val selectedLayer = layers.find { it.id == selectedLayerId }
            if (selectedLayer != null) {
                LayerControlsBar(
                    layer = selectedLayer,
                    onUpdateOpacity = { onUpdateOpacity(selectedLayer.id, it) },
                    onUpdateBlendMode = { onUpdateBlendMode(selectedLayer.id, it) },
                    onFlipH = { onFlipH(selectedLayer.id) },
                    onFlipV = { onFlipV(selectedLayer.id) },
                    onDuplicate = { onDuplicate(selectedLayer.id) },
                    onDelete = { onDelete(selectedLayer.id) },
                    onCommitOpacity = onCommitOpacity
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Layer list (Reversed order so top layer is at top of UI list, matching Photoshop)
            val reversedLayers = remember(layers) { layers.reversed() }

            if (layers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No layers on this artboard", color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(reversedLayers, key = { _, l -> l.id }) { index, layer ->
                        LayerItemRow(
                            layer = layer,
                            isSelected = layer.id == selectedLayerId,
                            canMoveUp = index > 0, // In reversed list, moving up in z-order is moving down the visual reversed list
                            canMoveDown = index < reversedLayers.size - 1,
                            onSelect = { onSelectLayer(layer.id) },
                            onToggleVisibility = { onToggleVisibility(layer.id) },
                            onToggleLock = { onToggleLock(layer.id) },
                            onMoveUp = { onMoveUp(layer.id) },
                            onMoveDown = { onMoveDown(layer.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerControlsBar(
    layer: Layer,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateBlendMode: (BlendModeType) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCommitOpacity: () -> Unit = {}
) {
    var showBlendMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioCard)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Blend Mode Selector
            Box {
                OutlinedButton(
                    onClick = { showBlendMenu = true },
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Mode: ${layer.blendMode.name}",
                        fontSize = 11.sp,
                        color = CyanAccent
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Blend Modes",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showBlendMenu,
                    onDismissRequest = { showBlendMenu = false },
                    modifier = Modifier.background(StudioCard)
                ) {
                    BlendModeType.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name, color = TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                onUpdateBlendMode(mode)
                                showBlendMenu = false
                            }
                        )
                    }
                }
            }

            // Flip and Transform shortcuts
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onFlipH, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Flip Horizontal",
                        tint = if (layer.flipHorizontal) CyanAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = PinkAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Opacity Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Opacity: ${(layer.opacity * 100).toInt()}%",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.width(80.dp)
            )
            Slider(
                value = layer.opacity,
                onValueChange = onUpdateOpacity,
                onValueChangeFinished = onCommitOpacity,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = VioletPrimary,
                    inactiveTrackColor = StudioBorder
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LayerItemRow(
    layer: Layer,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else StudioBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) StudioCard else StudioSurface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visibility Eye Button
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Visibility",
                tint = if (layer.visible) TextPrimary else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        // Lock Button
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = if (layer.locked) GoldAccent else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        // Layer Type Icon
        val typeIcon = when (layer.type) {
            LayerType.TEXT -> Icons.Default.TextFields
            LayerType.SHAPE -> Icons.Default.Category
            LayerType.IMAGE -> Icons.Default.Image
            LayerType.BRUSH -> Icons.Default.Brush
            LayerType.VECTOR_PATH -> Icons.Default.Gesture
            LayerType.GROUP -> Icons.Default.Folder
        }

        Icon(
            imageVector = typeIcon,
            contentDescription = null,
            tint = if (isSelected) CyanAccent else VioletPrimary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Layer Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) TextPrimary else TextSecondary,
                maxLines = 1
            )
            Text(
                text = "${layer.type.name} • ${layer.width.toInt()}x${layer.height.toInt()} • ${(layer.opacity * 100).toInt()}%",
                fontSize = 10.sp,
                color = TextMuted
            )
        }

        // Order Buttons (Up / Down)
        IconButton(
            onClick = onMoveUp,
            enabled = true,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Move Layer Up",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = true,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Move Layer Down",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
