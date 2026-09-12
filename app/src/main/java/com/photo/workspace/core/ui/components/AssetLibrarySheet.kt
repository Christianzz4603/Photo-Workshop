package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.model.ProjectDimensions
import com.photo.workspace.core.data.model.ShapeType
import com.photo.workspace.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class LibraryTab {
    TEMPLATES,
    ELEMENTS,
    PRESETS,
    SAVED_PROJECTS,
    PACKS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetLibrarySheet(
    installedPacks: List<File>,
    savedProjects: List<File>,
    onSelectTemplate: (String, ProjectDimensions) -> Unit,
    onAddShape: (ShapeType, Long) -> Unit,
    onAddText: (String) -> Unit,
    onLoadProject: (File) -> Unit,
    onImportMwPackClick: () -> Unit,
    onImportCanvaClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(LibraryTab.TEMPLATES) }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = StudioBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Library",
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Offline Asset Studio",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Close", color = CyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search offline templates, elements, badges...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = StudioSurface,
                contentColor = CyanAccent,
                divider = { Divider(color = StudioBorder) }
            ) {
                LibraryTab.values().forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = {
                            Text(
                                text = when (tab) {
                                    LibraryTab.TEMPLATES -> "Templates"
                                    LibraryTab.ELEMENTS -> "Elements"
                                    LibraryTab.PRESETS -> "Presets"
                                    LibraryTab.SAVED_PROJECTS -> "Projects"
                                    LibraryTab.PACKS -> "Packs & Import"
                                },
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                when (activeTab) {
                    LibraryTab.TEMPLATES -> TemplatesTabContent(onSelectTemplate)
                    LibraryTab.ELEMENTS -> ElementsTabContent(onAddShape, onAddText)
                    LibraryTab.PRESETS -> PresetsTabContent(onAddShape)
                    LibraryTab.SAVED_PROJECTS -> SavedProjectsTabContent(savedProjects, onLoadProject)
                    LibraryTab.PACKS -> PacksTabContent(
                        installedPacks = installedPacks,
                        onImportPack = onImportMwPackClick,
                        onImportCanva = onImportCanvaClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesTabContent(
    onSelectTemplate: (String, ProjectDimensions) -> Unit
) {
    val templates = listOf(
        Triple("Instagram Post", ProjectDimensions("Square (1:1)", 1080, 1080), Color(0xFF6366F1)),
        Triple("Story & Reels", ProjectDimensions("Story (9:16)", 1080, 1920), Color(0xFFEC4899)),
        Triple("YouTube Thumbnail", ProjectDimensions("Banner (16:9)", 1280, 720), Color(0xFF06B6D4)),
        Triple("Poster / Portrait", ProjectDimensions("Portrait (4:5)", 1080, 1350), Color(0xFF8B5CF6)),
        Triple("Tech Launch Banner", ProjectDimensions("Landscape (1.91:1)", 1200, 630), Color(0xFF10B981)),
        Triple("Minimalist Card", ProjectDimensions("Card (3:2)", 1200, 800), Color(0xFFF59E0B))
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(templates) { (title, dim, accent) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTemplate(title, dim) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.25f))
                            .border(1.dp, accent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${dim.width} × ${dim.height}",
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(dim.name, fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ElementsTabContent(
    onAddShape: (ShapeType, Long) -> Unit,
    onAddText: (String) -> Unit
) {
    val items = listOf(
        Pair(ShapeType.ROUNDED_RECT, "Rounded Card"),
        Pair(ShapeType.CIRCLE, "Sphere Element"),
        Pair(ShapeType.STAR, "Cyber Star"),
        Pair(ShapeType.TRIANGLE, "Triangle Wedge"),
        Pair(ShapeType.HEART, "Heart Badge"),
        Pair(ShapeType.ARROW, "Directional Arrow"),
        Pair(ShapeType.SPEECH_BUBBLE, "Speech Bubble"),
        Pair(ShapeType.POLYGON, "Hexagon Frame")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { (shapeType, label) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioCard)
                    .clickable { onAddShape(shapeType, 0xFF8B5CF6) }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (shapeType) {
                            ShapeType.ROUNDED_RECT -> Icons.Default.CropSquare
                            ShapeType.CIRCLE -> Icons.Default.Circle
                            ShapeType.STAR -> Icons.Default.Star
                            ShapeType.TRIANGLE -> Icons.Default.ChangeHistory
                            ShapeType.HEART -> Icons.Default.Favorite
                            ShapeType.ARROW -> Icons.Default.ArrowForward
                            ShapeType.SPEECH_BUBBLE -> Icons.Default.ChatBubble
                            else -> Icons.Default.Hexagon
                        },
                        contentDescription = label,
                        tint = CyanAccent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PresetsTabContent(
    onAddShape: (ShapeType, Long) -> Unit
) {
    val palettes = listOf(
        Pair("Obsidian Cyber", 0xFF8B5CF6),
        Pair("Electric Cyan", 0xFF06B6D4),
        Pair("Sunset Amber", 0xFFF59E0B),
        Pair("Neon Blossom", 0xFFEC4899),
        Pair("Emerald Studio", 0xFF10B981),
        Pair("Midnight Blue", 0xFF3B82F6)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(palettes) { (name, colorVal) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCard),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { onAddShape(ShapeType.ROUNDED_RECT, colorVal) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(colorVal))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("Add colored card", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedProjectsTabContent(
    projects: List<File>,
    onLoadProject: (File) -> Unit
) {
    if (projects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No saved projects found in PhotoWorkspace/Projects/", color = TextMuted)
        }
    } else {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(projects, key = { file -> file.absolutePath }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioCard)
                        .clickable { onLoadProject(file) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Project",
                            tint = VioletPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(file.nameWithoutExtension, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(dateFormat.format(Date(file.lastModified())), color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { onLoadProject(file) },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioBorder),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Open", fontSize = 12.sp, color = CyanAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun PacksTabContent(
    installedPacks: List<File>,
    onImportPack: () -> Unit,
    onImportCanva: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Import Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImportPack,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Import Pack (.mwpack)", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Pack (.mwpack)", fontSize = 12.sp)
            }

            Button(
                onClick = onImportCanva,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCard),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Canva Reconstruct", tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Canva Project", fontSize = 12.sp, color = CyanAccent)
            }
        }

        Text("Installed Offline Asset Packs", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

        if (installedPacks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioCard),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Photo Workspace Core Starter Pack Installed", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("Place .mwpack files in PhotoWorkspace/Packs/", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(installedPacks, key = { packFile -> packFile.absolutePath }) { packFile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioCard)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Pack", tint = GoldAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(packFile.nameWithoutExtension, color = TextPrimary, fontSize = 13.sp)
                            Text("Local Asset Package", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
