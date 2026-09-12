package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.data.importer.ReconstructionReport
import com.photo.workspace.ui.theme.*

@Composable
fun ReconstructionReportDialog(
    report: ReconstructionReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Canva Project Reconstructed", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Project: ${report.projectName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanAccent
                )

                Text(
                    text = "• Reconstructed ${report.reconstructedLayersCount} editable layers across ${report.pagesCount} artboard page(s).\n• Converted types: ${report.convertedTypes.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = TextPrimary
                )

                if (report.unsupportedFeatures.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approximated / Unsupported Features", color = GoldAccent, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            report.unsupportedFeatures.forEach { note ->
                                Text("• $note", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                } else {
                    Text("All vector and typography elements successfully converted to native editable layers.", fontSize = 11.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Text("Open In Studio")
            }
        }
    )
}
