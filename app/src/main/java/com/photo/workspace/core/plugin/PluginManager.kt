package com.photo.workspace.core.plugin

import android.content.Context
import android.util.Log
import com.photo.workspace.core.data.storage.WorkspaceManager
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.jar.JarFile

data class InstalledPlugin(
    val manifest: PluginManifest,
    val file: File,
    val enabled: Boolean,
    val loadError: String? = null
)

/**
 * Owns the full plugin lifecycle: import -> signature verification -> manifest parsing ->
 * (if enabled) dex loading -> onLoad(). Nothing here ever loads a jar that fails
 * [PluginSignatureVerifier] — that check runs both at import time and again every time a
 * plugin is loaded, so a file that was somehow swapped out on disk after import doesn't get
 * a free pass.
 */
class PluginManager(
    private val context: Context,
    private val workspaceManager: WorkspaceManager
) {
    private val TAG = "PluginManager"

    private val registryFile: File
        get() = File(workspaceManager.getPluginsDir(), "installed.json")

    // Private, app-internal directory required by DexClassLoader for its optimized dex output.
    // Deliberately NOT under the public workspace — this is compiled code cache, not user content.
    private val dexOutputDir: File
        get() = File(context.codeCacheDir, "plugin_dex").apply { if (!exists()) mkdirs() }

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val _toolbarActions = MutableStateFlow<List<PluginToolbarAction>>(emptyList())
    val toolbarActions: StateFlow<List<PluginToolbarAction>> = _toolbarActions.asStateFlow()

    private val _panels = MutableStateFlow<List<PluginPanel>>(emptyList())
    val panels: StateFlow<List<PluginPanel>> = _panels.asStateFlow()

    private val loadedInstances = mutableMapOf<String, PhotoWorkspacePlugin>()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Call once at app startup (after workspace init) to load every previously-enabled plugin. */
    fun initialize() {
        val registry = readRegistry()
        _installedPlugins.value = registry
        registry.filter { it.enabled }.forEach { loadPlugin(it) }
    }

    /**
     * Copies [sourceFile] into the Plugins/ folder, verifies its signature, parses its
     * manifest, and (if everything checks out) enables and loads it immediately.
     */
    fun importPlugin(sourceFile: File): Result<InstalledPlugin> {
        val verification = PluginSignatureVerifier.verify(sourceFile)
        if (verification is PluginSignatureVerifier.VerificationResult.Rejected) {
            return Result.failure(SecurityException(verification.reason))
        }

        val manifest = try {
            readManifest(sourceFile)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Missing or invalid plugin.json: ${e.message}"))
        }

        val destFile = File(workspaceManager.getPluginsDir(), "${manifest.id}.mwplugin")
        sourceFile.copyTo(destFile, overwrite = true)

        var installed = InstalledPlugin(manifest = manifest, file = destFile, enabled = true)
        val loadError = loadPlugin(installed)
        if (loadError != null) installed = installed.copy(enabled = false, loadError = loadError)

        val updated = _installedPlugins.value.filterNot { it.manifest.id == manifest.id } + installed
        _installedPlugins.value = updated
        writeRegistry(updated)

        return Result.success(installed)
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val current = _installedPlugins.value.find { it.manifest.id == pluginId } ?: return
        if (!enabled) {
            loadedInstances.remove(pluginId)?.let { instance ->
                try {
                    instance.onUnload()
                } catch (e: Exception) {
                    Log.w(TAG, "Plugin ${pluginId} threw during onUnload: ${e.message}")
                }
            }
            _toolbarActions.value = _toolbarActions.value.filterNot { it.id.startsWith("$pluginId:") }
            _panels.value = _panels.value.filterNot { it.id.startsWith("$pluginId:") }
        }

        var updatedEntry = current.copy(enabled = enabled, loadError = null)
        if (enabled) {
            val loadError = loadPlugin(updatedEntry)
            if (loadError != null) updatedEntry = updatedEntry.copy(enabled = false, loadError = loadError)
        }

        val updated = _installedPlugins.value.map { if (it.manifest.id == pluginId) updatedEntry else it }
        _installedPlugins.value = updated
        writeRegistry(updated)
    }

    fun removePlugin(pluginId: String) {
        setEnabled(pluginId, enabled = false)
        val current = _installedPlugins.value.find { it.manifest.id == pluginId }
        current?.file?.delete()
        val updated = _installedPlugins.value.filterNot { it.manifest.id == pluginId }
        _installedPlugins.value = updated
        writeRegistry(updated)
    }

    /** Returns a user-facing error message on failure, or null on success. */
    private fun loadPlugin(installed: InstalledPlugin): String? {
        // Re-verify every time we actually load code, not just at import — the file on disk
        // could theoretically have been replaced since import.
        val verification = PluginSignatureVerifier.verify(installed.file)
        if (verification is PluginSignatureVerifier.VerificationResult.Rejected) {
            Log.w(TAG, "Refusing to load ${installed.manifest.id}: ${verification.reason}")
            return verification.reason
        }

        if (installed.manifest.minHostVersion > PluginManifest.HOST_API_VERSION) {
            return "This plugin needs a newer app version (host API ${installed.manifest.minHostVersion}, " +
                "app supports ${PluginManifest.HOST_API_VERSION})."
        }

        return try {
            val loader = DexClassLoader(
                installed.file.absolutePath,
                dexOutputDir.absolutePath,
                null,
                context.classLoader // parent = host app's classloader, so plugin code can see host classes
            )
            val pluginClass = loader.loadClass(installed.manifest.mainClass)
            val instance = pluginClass.getDeclaredConstructor().newInstance() as PhotoWorkspacePlugin

            val host = createHostFor(installed.manifest.id)
            instance.onLoad(host)

            loadedInstances[installed.manifest.id] = instance
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin ${installed.manifest.id}", e)
            "Plugin failed to load: ${e.message}"
        }
    }

    private fun createHostFor(pluginId: String): PluginHost {
        return object : PluginHost {
            override val appContext: Context = context.applicationContext

            override fun registerToolbarAction(action: PluginToolbarAction) {
                val namespaced = action.copy(id = "$pluginId:${action.id}")
                _toolbarActions.value = _toolbarActions.value.filterNot { it.id == namespaced.id } + namespaced
            }

            override fun registerPanel(panel: PluginPanel) {
                val namespaced = panel.copy(id = "$pluginId:${panel.id}")
                _panels.value = _panels.value.filterNot { it.id == namespaced.id } + namespaced
            }

            override fun showStatusMessage(message: String) {
                _statusMessage.value = message
            }
        }
    }

    private fun readManifest(jarFile: File): PluginManifest {
        JarFile(jarFile).use { jar ->
            val entry = jar.getEntry("plugin.json")
                ?: throw IllegalArgumentException("plugin.json not found at jar root")
            val json = jar.getInputStream(entry).bufferedReader().use { it.readText() }
            return PluginManifest.parse(json)
        }
    }

    private fun readRegistry(): List<InstalledPlugin> {
        if (!registryFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(registryFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val file = File(obj.getString("filePath"))
                if (!file.exists()) return@mapNotNull null
                val manifest = try {
                    readManifest(file)
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                InstalledPlugin(manifest = manifest, file = file, enabled = obj.optBoolean("enabled", true))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading plugin registry: ${e.message}")
            emptyList()
        }
    }

    private fun writeRegistry(plugins: List<InstalledPlugin>) {
        val arr = JSONArray()
        plugins.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("filePath", p.file.absolutePath)
                    put("enabled", p.enabled)
                }
            )
        }
        registryFile.writeText(arr.toString())
    }
}
