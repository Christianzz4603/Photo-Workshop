package com.photo.workspace.core.plugin

import android.content.Context
import android.view.View

/**
 * Contract every plugin's main class implements (named by `mainClass` in its plugin.json).
 * Must have a public no-arg constructor — it's instantiated via reflection after signature
 * verification passes.
 */
interface PhotoWorkspacePlugin {
    /** Called once, right after your code is loaded and signature-verified. Register everything here. */
    fun onLoad(host: PluginHost)

    /** Called when the plugin is disabled/removed. Release anything you hold onto (listeners, threads, etc). */
    fun onUnload()
}

/**
 * The capability surface a loaded plugin gets. Deliberately a registration API — "tell the
 * host what you want to add" — rather than handing plugins the ability to reach into and
 * rewrite arbitrary existing screens. Even with every plugin coming from you, keeping the
 * surface explicit means: a plugin that crashes only takes down the thing it registered (each
 * registered action/panel is invoked in its own try/catch by PluginManager), and the app's
 * core screens stay predictable instead of depending on which plugins happen to be installed.
 *
 * [appContext] is the one deliberately broad hook — a plugin with a legitimate reason to touch
 * the filesystem, show a WebView, etc. needs a real Context. That's the trust you're extending
 * by signing it; everything else here exists to keep that access *purposeful* rather than
 * "patch anything."
 */
interface PluginHost {

    val appContext: Context

    /** Adds an entry to the editor's toolbar overflow menu. */
    fun registerToolbarAction(action: PluginToolbarAction)

    /**
     * Adds a full-screen panel reachable from the Plugins sheet. [contentFactory] builds a
     * plain Android View — this is intentionally the classic View system, not Compose, so a
     * plugin can host anything from a simple layout to a full WebView-based browser without
     * needing Compose on its own classpath. The host renders it via Compose's AndroidView
     * interop, so it still lives inside the app's normal navigation.
     */
    fun registerPanel(panel: PluginPanel)

    /** Brief, non-blocking user-facing message (reuses the same status bar the rest of the app uses). */
    fun showStatusMessage(message: String)
}

data class PluginToolbarAction(
    val id: String,
    val label: String,
    val onInvoke: () -> Unit
)

data class PluginPanel(
    val id: String,
    val title: String,
    val contentFactory: (Context) -> View
)
