package com.photo.workspace.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.photo.workspace.core.plugin.PluginPanel
import com.photo.workspace.ui.theme.StudioObsidian
import com.photo.workspace.ui.theme.StudioSurface
import com.photo.workspace.ui.theme.TextPrimary

/**
 * Renders a [PluginPanel]'s plain-View content (e.g. a plugin's WebView-based browser) inside
 * the app's normal navigation, via Compose's View-interop. This is what lets a plugin add
 * something as open-ended as a custom in-app browser without the host needing to know
 * anything about WebView specifically — the plugin owns the View, the host just gives it a
 * full-screen frame.
 */
@Composable
fun PluginPanelHost(panel: PluginPanel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = StudioObsidian,
            topBar = {
                TopAppBar(
                    title = { Text(panel.title, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioSurface)
                )
            }
        ) { padding ->
            AndroidView(
                factory = { ctx -> panel.contentFactory(ctx) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}
