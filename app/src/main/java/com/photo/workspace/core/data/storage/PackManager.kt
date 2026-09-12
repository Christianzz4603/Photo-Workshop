package com.photo.workspace.core.data.storage

import android.content.Context
import android.util.Log
import com.photo.workspace.core.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PackManager(
    private val context: Context,
    private val workspaceManager: WorkspaceManager
) {
    private val TAG = "PackManager"

    // Safe extraction limits
    private val MAX_ENTRIES = 5000
    private val MAX_TOTAL_SIZE = 100 * 1024 * 1024 // 100 MB safe limit per pack

    /**
     * Safely extract and import an .mwpack file into Photo Workspace offline library
     */
    fun importPack(
        packFile: File,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<PackManifest> {
        val packDirName = packFile.nameWithoutExtension
            .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val extractTargetDir = File(workspaceManager.getPacksDir(), packDirName)

        val createdFiles = mutableListOf<File>()

        try {
            onProgress(0.05f, "Validating package structure...")

            if (!extractTargetDir.exists()) {
                extractTargetDir.mkdirs()
            }

            var totalEntries = 0
            var totalBytesRead = 0L

            // 1. Safe extraction with Zip Slip defense
            ZipInputStream(FileInputStream(packFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ENTRIES) {
                        throw SecurityException("Security limit exceeded: Package contains more than $MAX_ENTRIES files.")
                    }

                    // Path traversal protection (Zip Slip)
                    val entryName = entry.name
                    val destinationFile = File(extractTargetDir, entryName)
                    val canonicalDest = destinationFile.canonicalPath
                    val canonicalTarget = extractTargetDir.canonicalPath

                    if (!canonicalDest.startsWith(canonicalTarget + File.separator) && canonicalDest != canonicalTarget) {
                        throw SecurityException("Path traversal attempt detected in entry: $entryName")
                    }

                    if (entry.isDirectory) {
                        destinationFile.mkdirs()
                    } else {
                        destinationFile.parentFile?.mkdirs()
                        FileOutputStream(destinationFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytesRead += len
                                if (totalBytesRead > MAX_TOTAL_SIZE) {
                                    throw SecurityException("Package exceeds safe uncompressed size limit ($MAX_TOTAL_SIZE bytes)")
                                }
                                fos.write(buffer, 0, len)
                            }
                        }
                        createdFiles.add(destinationFile)
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            onProgress(0.50f, "Parsing manifest & indexing assets...")

            // 2. Validate manifest.json
            val manifestFile = File(extractTargetDir, "manifest.json")
            if (!manifestFile.exists()) {
                throw IllegalArgumentException("Malformed pack (.mwpack): manifest.json is missing.")
            }

            val manifest = parseManifest(manifestFile.readText(Charsets.UTF_8))

            onProgress(0.80f, "Registering elements into local library...")

            // 3. Register items into specific categories
            manifest.items.forEach { item ->
                when (item.category) {
                    AssetCategory.TEMPLATE -> {
                        val src = File(extractTargetDir, item.relativePath)
                        if (src.exists()) {
                            val dest = File(workspaceManager.getTemplatesDir(), src.name)
                            if (!dest.exists()) src.copyTo(dest, overwrite = true)
                        }
                    }
                    AssetCategory.ELEMENT, AssetCategory.STICKER, AssetCategory.SHAPE -> {
                        val src = File(extractTargetDir, item.relativePath)
                        if (src.exists()) {
                            val dest = File(workspaceManager.getElementsDir(), src.name)
                            if (!dest.exists()) src.copyTo(dest, overwrite = true)
                        }
                    }
                    AssetCategory.FONT -> {
                        val src = File(extractTargetDir, item.relativePath)
                        if (src.exists()) {
                            val dest = File(workspaceManager.getFontsDir(), src.name)
                            if (!dest.exists()) src.copyTo(dest, overwrite = true)
                        }
                    }
                    AssetCategory.PRESET -> {
                        val src = File(extractTargetDir, item.relativePath)
                        if (src.exists()) {
                            val dest = File(workspaceManager.getPresetsDir(), src.name)
                            if (!dest.exists()) src.copyTo(dest, overwrite = true)
                        }
                    }
                    else -> {}
                }
            }

            onProgress(1.0f, "Package imported successfully!")
            return Result.success(manifest)

        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            // Rollback safe cleanup
            createdFiles.forEach { it.delete() }
            extractTargetDir.deleteRecursively()
            return Result.failure(e)
        }
    }

    /**
     * Parse manifest.json from string
     */
    fun parseManifest(jsonStr: String): PackManifest {
        val root = JSONObject(jsonStr)
        val id = root.optString("id", java.util.UUID.randomUUID().toString())
        val name = root.optString("name", "Unnamed Pack")
        val version = root.optString("version", "1.0.0")
        val description = root.optString("description", "")
        val author = root.optString("author", "Photo Workspace Creator")
        val license = root.optString("license", "CC0")

        val itemsList = mutableListOf<PackAssetItem>()
        val itemsArr = root.optJSONArray("items")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val cat = try {
                    AssetCategory.valueOf(obj.optString("category", "ELEMENT"))
                } catch (e: Exception) {
                    AssetCategory.ELEMENT
                }
                val tagsList = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(t))
                    }
                }

                itemsList.add(
                    PackAssetItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", "Asset $i"),
                        category = cat,
                        relativePath = obj.optString("relativePath", ""),
                        thumbnailPath = if (obj.has("thumbnailPath")) obj.getString("thumbnailPath") else null,
                        tags = tagsList,
                        license = obj.optString("license", license)
                    )
                )
            }
        }

        return PackManifest(
            id = id,
            name = name,
            version = version,
            description = description,
            author = author,
            license = license,
            items = itemsList
        )
    }

    /**
     * Create and export an .mwpack file
     */
    fun exportPack(
        manifest: PackManifest,
        filesToInclude: Map<String, File>,
        outputFile: File
    ): File {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // 1. Write manifest.json
            val manifestJson = JSONObject().apply {
                put("id", manifest.id)
                put("name", manifest.name)
                put("version", manifest.version)
                put("description", manifest.description)
                put("author", manifest.author)
                put("license", manifest.license)

                val arr = JSONArray()
                manifest.items.forEach { itm ->
                    val itmObj = JSONObject().apply {
                        put("id", itm.id)
                        put("title", itm.title)
                        put("category", itm.category.name)
                        put("relativePath", itm.relativePath)
                        itm.thumbnailPath?.let { put("thumbnailPath", it) }
                        put("license", itm.license)
                        val tagsArr = JSONArray()
                        itm.tags.forEach { tagsArr.put(it) }
                        put("tags", tagsArr)
                    }
                    arr.put(itmObj)
                }
                put("items", arr)
            }

            val manifestEntry = ZipEntry("manifest.json")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Write assets
            filesToInclude.forEach { (entryPath, file) ->
                if (file.exists() && file.isFile) {
                    val entry = ZipEntry(entryPath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        return outputFile
    }

    /**
     * Generate built-in starter templates and asset package if not present
     */
    fun ensureStarterAssets() {
        val starterPackFile = File(workspaceManager.getPacksDir(), "photo_workspace_starter_essentials.mwpack")
        if (starterPackFile.exists()) return

        try {
            // Create a sample manifest with built-in templates and elements
            val manifest = PackManifest(
                id = "pack_starter_001",
                name = "Photo Workspace Core Studio Essentials",
                version = "1.0.0",
                description = "Offline collection of shapes, badge templates, social banners, and gradient presets",
                author = "Photo Workspace Core Team",
                license = "CC0 Public Domain",
                items = listOf(
                    PackAssetItem(
                        title = "Creative Poster Artboard",
                        category = AssetCategory.TEMPLATE,
                        relativePath = "templates/creative_poster.mwproject",
                        tags = listOf("poster", "creative", "bold", "dark")
                    ),
                    PackAssetItem(
                        title = "Social Media Announcement",
                        category = AssetCategory.TEMPLATE,
                        relativePath = "templates/social_promo.mwproject",
                        tags = listOf("social", "square", "instagram", "modern")
                    ),
                    PackAssetItem(
                        title = "Neon Badge Element",
                        category = AssetCategory.SHAPE,
                        relativePath = "elements/neon_badge.json",
                        tags = listOf("badge", "neon", "sticker", "cyber")
                    ),
                    PackAssetItem(
                        title = "Retro Cyber Preset",
                        category = AssetCategory.PRESET,
                        relativePath = "presets/retro_cyber.json",
                        tags = listOf("palette", "cyberpunk", "retro")
                    )
                )
            )

            // Export local sample starter pack
            exportPack(manifest, emptyMap(), starterPackFile)
            Log.d(TAG, "Created starter essentials pack at ${starterPackFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create starter pack: ${e.message}")
        }
    }
}
