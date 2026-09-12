package com.photo.workspace.core.plugin

import org.json.JSONObject

/**
 * Metadata every plugin jar must ship as a `plugin.json` entry at its root.
 *
 * Example `plugin.json`:
 * ```json
 * {
 *   "id": "com.photo.workspace.plugins.mybrowser",
 *   "name": "Reference Browser",
 *   "version": "1.0.0",
 *   "mainClass": "com.example.myplugin.BrowserPlugin",
 *   "minHostVersion": 1
 * }
 * ```
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val mainClass: String,
    val minHostVersion: Int
) {
    companion object {
        /** Bump this whenever PluginHost's API surface changes in a way old plugins can't handle. */
        const val HOST_API_VERSION = 1

        fun parse(json: String): PluginManifest {
            val obj = JSONObject(json)
            return PluginManifest(
                id = obj.getString("id"),
                name = obj.getString("name"),
                version = obj.optString("version", "1.0.0"),
                mainClass = obj.getString("mainClass"),
                minHostVersion = obj.optInt("minHostVersion", 1)
            )
        }
    }
}
