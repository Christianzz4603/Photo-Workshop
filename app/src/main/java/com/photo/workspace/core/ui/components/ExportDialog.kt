package com.photo.workspace.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.ui.theme.*

@Composable
fun ExportDialog(
    onExportImage: (Bitmap.CompressFormat, Int) -> Unit,
    onExportPdf: () -> Unit,
    onExportSvg: () -> Unit,
    onSaveMwProject: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = VioletPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Photo Workspace Artwork", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Exports are rendered 100% locally and saved to:\n/storage/emulated/0/Download/PhotoWorkspace/Exports/",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExportFormatRow(
                    icon = Icons.Default.Image,
                    title = "PNG Image (.png)",
                    subtitle = "High quality lossless image",
                    onClick = {
                        onExportImage(Bitmap.CompressFormat.PNG, 100)
                        onDismiss()
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.PhotoCamera,
                    title = "JPEG Image (.jpg)",
                    subtitle = "Optimized for social media and sharing",
                    onClick = {
                        onExportImage(Bitmap.CompressFormat.JPEG, 95)
                        onDismiss()
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.PictureAsPdf,
                    title = "PDF Document (.pdf)",
                    subtitle = "Multi-page artboard document, fully offline",
                    onClick = {
                        onExportPdf()
                        onDismiss()
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.Polyline,
                    title = "SVG Vector (.svg)",
                    subtitle = "Scalable vector paths, shapes & text",
                    onClick = {
                        onExportSvg()
                        onDismiss()
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.Inventory2,
                    title = "Photo Workspace Project (.mwproject)",
                    subtitle = "Portable editable project with layers & history",
                    onClick = {
                        onSaveMwProject()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CyanAccent)
            }
        }
    )
}

@Composable
private fun ExportFormatRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StudioCard)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = CyanAccent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = TextMuted, fontSize = 10.sp)
        }
    }
}
