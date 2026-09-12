package com.photo.workspace.core.data.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

enum class WorkspaceDir(val folderName: String) {
    PROJECTS("Projects"),
    TEMPLATES("Templates"),
    ELEMENTS("Elements"),
    FONTS("Fonts"),
    IMAGES("Images"),
    AUDIO("Audio"),
    PACKS("Packs"),
    PLUGINS("Plugins"),
    EXPORTS("Exports"),
    BACKUPS("Backups"),
    CACHE("Cache"),
    THUMBNAILS("Thumbnails"),
    PRESETS("Presets")
}

class WorkspaceManager(private val context: Context) {

    private val TAG = "WorkspaceManager"

    // Primary path: /storage/emulated/0/Download/PhotoWorkspace/
    val canonicalPublicDir: File by lazy {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, "PhotoWorkspace")
    }

    // App-specific external fallback if scoped storage blocks root downloads
    val fallbackExternalDir: File by lazy {
        File(context.getExternalFilesDir(null), "PhotoWorkspace")
    }

    // Determine the active workspace directory that is accessible
    fun getWorkspaceRoot(): File {
        return try {
            if (!canonicalPublicDir.exists()) {
                canonicalPublicDir.mkdirs()
            }
            if (canonicalPublicDir.canWrite()) {
                canonicalPublicDir
            } else {
                fallbackExternalDir.apply { if (!exists()) mkdirs() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to app-specific external workspace: ${e.message}")
            fallbackExternalDir.apply { if (!exists()) mkdirs() }
        }
    }

    /**
     * Initializes all workspace directories required by Photo Workspace
     */
    fun initializeWorkspace() {
        val root = getWorkspaceRoot()
        WorkspaceDir.values().forEach { dirEnum ->
            val subFolder = File(root, dirEnum.folderName)
            if (!subFolder.exists()) {
                subFolder.mkdirs()
            }
        }

        // Also attempt to create them in the public folder if different
        try {
            if (root.absolutePath != canonicalPublicDir.absolutePath) {
                canonicalPublicDir.mkdirs()
                WorkspaceDir.values().forEach {
                    File(canonicalPublicDir, it.folderName).mkdirs()
                }
            }
        } catch (e: Exception) {
            // Ignore if restricted by system
        }
    }

    fun getDirectory(workspaceDir: WorkspaceDir): File {
        val dir = File(getWorkspaceRoot(), workspaceDir.folderName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getProjectsDir(): File = getDirectory(WorkspaceDir.PROJECTS)
    fun getBackupsDir(): File = getDirectory(WorkspaceDir.BACKUPS)
    fun getExportsDir(): File = getDirectory(WorkspaceDir.EXPORTS)
    fun getTemplatesDir(): File = getDirectory(WorkspaceDir.TEMPLATES)
    fun getPacksDir(): File = getDirectory(WorkspaceDir.PACKS)
    fun getPluginsDir(): File = getDirectory(WorkspaceDir.PLUGINS)
    fun getThumbnailsDir(): File = getDirectory(WorkspaceDir.THUMBNAILS)
    fun getElementsDir(): File = getDirectory(WorkspaceDir.ELEMENTS)
    fun getPresetsDir(): File = getDirectory(WorkspaceDir.PRESETS)
    fun getFontsDir(): File = getDirectory(WorkspaceDir.FONTS)

    fun listProjects(): List<File> {
        return getProjectsDir().listFiles { file ->
            file.isFile && (file.name.endsWith(".mwproject") || file.name.endsWith(".json"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listBackups(): List<File> {
        return getBackupsDir().listFiles { file ->
            file.isFile && (file.name.endsWith(".mwproject") || file.name.endsWith(".bak"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listExports(): List<File> {
        return getExportsDir().listFiles { file ->
            file.isFile && !file.name.startsWith(".")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listPacks(): List<File> {
        return getPacksDir().listFiles { file ->
            file.isFile && (file.name.endsWith(".mwpack") || file.name.endsWith(".zip"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun listPlugins(): List<File> {
        return getPluginsDir().listFiles { file ->
            file.isFile && (file.name.endsWith(".mwplugin") || file.name.endsWith(".jar"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
