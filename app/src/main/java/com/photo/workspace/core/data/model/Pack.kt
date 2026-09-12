package com.photo.workspace.core.data.model

import java.util.UUID

enum class AssetCategory {
    TEMPLATE,
    ELEMENT,
    SHAPE,
    STICKER,
    BACKGROUND,
    FONT,
    PRESET,
    PHOTO
}

data class PackAssetItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: AssetCategory,
    val relativePath: String,
    val thumbnailPath: String? = null,
    val tags: List<String> = emptyList(),
    val license: String = "CC0 - Photo Workspace Community",
    val extraData: Map<String, String> = emptyMap()
)

data class PackManifest(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val version: String = "1.0.0",
    val description: String = "Asset pack for Photo Workspace creative studio",
    val author: String = "Photo Workspace Creator",
    val license: String = "CC0 / Free for Commercial Use",
    val items: List<PackAssetItem> = emptyList()
)

data class LibraryAsset(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: AssetCategory,
    val sourcePackId: String? = null,
    val localFilePath: String,
    val thumbnailUri: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)
