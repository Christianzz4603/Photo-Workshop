package com.photo.workspace.core.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photo.workspace.ui.theme.CyanAccent
import com.photo.workspace.ui.theme.StudioObsidian
import com.photo.workspace.ui.theme.TextPrimary
import com.photo.workspace.ui.theme.TextSecondary

/**
 * Gates [content] behind All Files Access on Android 11+ (API 30+).
 *
 * Without this, WorkspaceManager's raw File() reads/writes to the public
 * /storage/emulated/0/Download/PhotoWorkspace/ workspace silently fail on modern devices —
 * scoped storage is enforced by the OS regardless of what File.canWrite() reports —
 * and every save silently lands in the app-private fallback folder instead, which
 * breaks the "accessible through normal file managers" requirement. Below API 30
 * (where legacy full storage access already applies) this gate is a no-op.
 *
 * The user can decline and use the app anyway — Photo Workspace still works fully offline
 * in that case, it just stays confined to its private app folder.
 */
@Composable
fun StoragePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    var userDismissed by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        granted = hasAllFilesAccess()
    }

    if (granted || userDismissed) {
        content()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Allow Access to Downloads",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Photo Workspace saves every project directly into Download/PhotoWorkspace/ so your files " +
                "stay visible in any normal file manager — no cloud, no account. " +
                "Android requires \"All files access\" for an app to write there.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                settingsLauncher.launch(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
            Text("Open Settings", color = StudioObsidian)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = { userDismissed = true }) {
            Text("Continue without this (app-private storage only)", color = TextSecondary)
        }
    }
}
