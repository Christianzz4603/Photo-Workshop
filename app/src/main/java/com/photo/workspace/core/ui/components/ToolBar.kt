package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.ui.EditorTool
import com.photo.workspace.ui.theme.*

@Composable
fun TopStudioBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onShowDocs: () -> Unit,
    onOpenPlugins: () -> Unit,
    onToggleGrid: () -> Unit,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Project title & docs
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VioletPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Photo Workspace",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "100% Offline • Local-First Studio",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontSize = 10.sp
                    )
                }
            }

            // Right: Actions (Undo, Redo, Grid, Save, Export, Help)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) TextPrimary else TextMuted
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) TextPrimary else TextMuted
                    )
                }

                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Grid",
                        tint = if (showGrid) CyanAccent else TextSecondary
                    )
                }

                IconButton(
                    onClick = onShowDocs,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Technical Documentation & Roadmap",
                        tint = TextSecondary
                    )
                }

                IconButton(
                    onClick = onOpenPlugins,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Plugins",
                        tint = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCard),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp),
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontSize = 13.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BottomToolBar(
    activeTool: EditorTool,
    layerCount: Int,
    pageCount: Int,
    activePageIndex: Int,
    onSelectTool: (EditorTool) -> Unit,
    onOpenLayers: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddText: () -> Unit,
    onAddShape: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Select / Pointer Tool
            ToolButton(
                icon = Icons.Default.NearMe,
                label = "Select",
                isSelected = activeTool == EditorTool.SELECT,
                onClick = { onSelectTool(EditorTool.SELECT) }
            )

            // Add Text
            ToolButton(
                icon = Icons.Default.TextFields,
                label = "Text",
                isSelected = activeTool == EditorTool.TEXT,
                onClick = {
                    onSelectTool(EditorTool.TEXT)
                    onAddText()
                }
            )

            // Add Shape
            ToolButton(
                icon = Icons.Default.Category,
                label = "Shapes",
                isSelected = activeTool == EditorTool.SHAPE,
                onClick = {
                    onSelectTool(EditorTool.SHAPE)
                    onAddShape()
                }
            )

            // Brush / Draw
            ToolButton(
                icon = Icons.Default.Brush,
                label = "Brush",
                isSelected = activeTool == EditorTool.BRUSH,
                onClick = { onSelectTool(EditorTool.BRUSH) }
            )

            // Adjustments & Filters (Photoshop style)
            ToolButton(
                icon = Icons.Default.Tune,
                label = "Adjust",
                isSelected = activeTool == EditorTool.ADJUSTMENTS,
                onClick = { onSelectTool(EditorTool.ADJUSTMENTS) }
            )

            // Pen / Vector Path Tool
            ToolButton(
                icon = Icons.Default.Timeline,
                label = "Pen",
                isSelected = activeTool == EditorTool.PEN,
                onClick = { onSelectTool(EditorTool.PEN) }
            )

            Divider(
                modifier = Modifier
                    .height(28.dp)
                    .width(1.dp)
                    .padding(horizontal = 4.dp),
                color = StudioBorder
            )

            // Local Asset Library (Canva templates, elements, stickers, packs)
            ToolButton(
                icon = Icons.Default.GridView,
                label = "Library",
                isSelected = false,
                onClick = onOpenLibrary,
                badge = null
            )

            // Layers Panel Button with layer count badge
            ToolButton(
                icon = Icons.Default.Layers,
                label = "Layers",
                isSelected = false,
                onClick = onOpenLayers,
                badge = layerCount.toString()
            )

            // Multi-page Artboard Switcher
            ToolButton(
                icon = Icons.Default.AutoStories,
                label = "Pages",
                isSelected = false,
                onClick = onOpenPages,
                badge = "${activePageIndex + 1}/$pageCount"
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: String? = null
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) VioletPrimary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) CyanAccent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-4).dp)
                        .background(VioletPrimary, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isSelected) CyanAccent else TextMuted
        )
    }
}
