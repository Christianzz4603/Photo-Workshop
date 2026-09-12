package com.photo.workspace.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photo.workspace.core.plugin.InstalledPlugin
import com.photo.workspace.core.plugin.PluginPanel
import com.photo.workspace.ui.theme.*

@Composable
fun PluginsBottomSheet(
    installedPlugins: List<InstalledPlugin>,
    panels: List<PluginPanel>,
    onImportClick: () -> Unit,
    onSetEnabled: (id: String, enabled: Boolean) -> Unit,
    onRemove: (id: String) -> Unit,
    onOpenPanel: (PluginPanel) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = VioletPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Plugins", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                TextButton(onClick = onDismiss) {
                    Text("Done", color = CyanAccent)
                }
            }

            Text(
                "Only install plugins from a source you trust — a plugin runs with the same " +
                    "access as the app itself.",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Button(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Plugin (.mwplugin)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (installedPlugins.isEmpty()) {
                Text(
                    "No plugins installed yet.",
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(installedPlugins, key = { it.manifest.id }) { plugin ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioCard)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plugin.manifest.name, color = TextPrimary, fontSize = 14.sp)
                                    Text(
                                        "v${plugin.manifest.version} • ${plugin.manifest.id}",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = plugin.enabled,
                                    onCheckedChange = { onSetEnabled(plugin.manifest.id, it) }
                                )
                                IconButton(onClick = { onRemove(plugin.manifest.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextMuted)
                                }
                            }
                            plugin.loadError?.let { error ->
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            val pluginPanels = panels
            if (pluginPanels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Plugin Panels", style = MaterialTheme.typography.titleSmall, color = CyanAccent)
                Spacer(modifier = Modifier.height(8.dp))
                pluginPanels.forEach { panel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioCard)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(panel.title, color = TextPrimary, fontSize = 14.sp)
                        IconButton(onClick = { onOpenPanel(panel) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = CyanAccent)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
