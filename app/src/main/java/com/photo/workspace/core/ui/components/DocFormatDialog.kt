package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
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
fun DocFormatDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Photo Workspace Documentation & Roadmap", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Offline Assurance Card
                DocCard(
                    icon = Icons.Default.Security,
                    title = "100% Offline & Local-First",
                    content = "Photo Workspace does not require an account, cloud service, API key, or internet connection. All project rendering, saving, exporting, importing, and pack extraction occur strictly on your device."
                )

                // Workspace Directory Structure
                DocCard(
                    icon = Icons.Default.Folder,
                    title = "Local Storage Workspace",
                    content = "Canonical path:\n/storage/emulated/0/Download/PhotoWorkspace/\n\nSubdirectories:\n• Projects/ (.mwproject projects)\n• Templates/ (multi-page reusable templates)\n• Elements/ (SVGs, vector shapes, badges)\n• Fonts/ (local TTF/OTF fonts)\n• Packs/ (installed .mwpack bundles)\n• Exports/ (rendered PNG, JPG, PDF, SVG)\n• Backups/ (automatic crash recovery snapshots)\n• Presets/ (color schemes, brand kits)"
                )

                // .MWPROJECT Native Format
                DocCard(
                    icon = Icons.Default.Code,
                    title = ".MWPROJECT Specification",
                    content = "Photo Workspace's editable project format is a structured JSON manifest supporting:\n• Multiple Artboard Pages\n• Layers Stack (Z-order, visibility, locking, opacity, 8 blend modes)\n• Layer Types: Text (typography, curving, shadows), Shapes (bezier paths, radius, stars), Images (non-destructive adjustments), Brushes (smooth strokes), Vector Paths\n• Presentation Keyframe Animation Presets\n• Atomic write protection and automatic backup snapshots"
                )

                // .MWPACK Specification
                DocCard(
                    icon = Icons.Default.Code,
                    title = ".MWPACK Specification",
                    content = "Portable asset package (.mwpack) is a safe ZIP bundle containing:\n• manifest.json (metadata, categories, items, tags, license)\n• Assets (SVGs, PNGs, templates, fonts, presets)\n• Zip Slip traversal protection & safe size validation"
                )

                // Canva Reconstruction
                DocCard(
                    icon = Icons.Default.Info,
                    title = "Canva Project Reconstruction",
                    content = "When importing Canva-exported SVG, JSON, or ZIP packages, Photo Workspace inspects nested geometric nodes, typography, colors, and layout bounds to reconstruct native editable layers with full transparency reports."
                )

                // Roadmap
                DocCard(
                    icon = Icons.Default.Info,
                    title = "Architecture & Roadmap",
                    content = "• Stage 1 (Active): Complete local workspace, interactive multi-page canvas, layer panel, blend modes, vector shapes, typography, brush drawing, image adjustments, .mwproject & .mwpack engines, PDF/SVG/PNG offline export, autosave & backups.\n• Stage 2: Expanded Bezier anchor curve manipulation and layered PSD format parsing.\n• Stage 3: Sandboxed local offline plugin API for procedural brushes and generative vector patterns."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text("Got It")
            }
        }
    )
}

@Composable
private fun DocCard(icon: ImageVector, title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(content, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}
