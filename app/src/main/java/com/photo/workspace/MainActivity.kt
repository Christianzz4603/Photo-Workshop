package com.photo.workspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.photo.workspace.core.ui.AppIntegrityGate
import com.photo.workspace.core.ui.PhotoWorkspaceApp
import com.photo.workspace.core.ui.StoragePermissionGate
import com.photo.workspace.ui.theme.PhotoWorkspaceTheme
import com.photo.workspace.ui.theme.StudioObsidian

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoWorkspaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioObsidian
                ) {
                    AppIntegrityGate {
                        StoragePermissionGate {
                            PhotoWorkspaceApp()
                        }
                    }
                }
            }
        }
    }
}
