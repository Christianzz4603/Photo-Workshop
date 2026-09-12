package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.model.ArtboardPage
import com.photo.workspace.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesBottomSheet(
    pages: List<ArtboardPage>,
    activePageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onDeletePage: () -> Unit,
    onDismiss: () -> Unit
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
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "Pages",
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Artboard Pages (${pages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onDuplicatePage) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Page", tint = TextSecondary)
                    }
                    IconButton(onClick = onDeletePage) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = PinkAccent)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = CyanAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal thumbnail strip of artboards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    val isActive = index == activePageIndex
                    val borderColor = if (isActive) CyanAccent else StudioBorder

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(110.dp)
                            .clickable { onSelectPage(index) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(page.backgroundColor))
                                .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 20.sp,
                                    color = if (isActive) CyanAccent else TextMuted
                                )
                                Text(
                                    text = "${page.layers.size} layers",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = page.name,
                            fontSize = 11.sp,
                            color = if (isActive) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                // Add Page Button Card
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(100.dp)
                            .clickable { onAddPage() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioCard)
                                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Page",
                                tint = CyanAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Add Page", fontSize = 11.sp, color = CyanAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
