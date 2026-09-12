package com.photo.workspace.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.Collections

/**
 * Shared in-memory cache for decoded bitmaps.
 *
 * Before this existed, both [com.photo.workspace.core.ui.components.CanvasView] and
 * [com.photo.workspace.core.data.exporter.CanvasExporter] called
 * `BitmapFactory.decodeFile(...)` directly, at full resolution, every single time an
 * image layer was drawn. For the canvas view, that meant a full-resolution disk read
 * + decode on the UI thread on *every recomposition* of the Canvas (i.e. every frame
 * while dragging, resizing, or animating an image layer) — a guaranteed source of
 * jank and, for large photos, a real risk of transient OOM churn from repeatedly
 * allocating full-size bitmaps that are immediately discarded.
 *
 * This cache decodes downsampled to the actual on-screen size requested (so a 4000x3000
 * photo shown in a 300x200 layer is decoded near that size, not full-res), and caches
 * the result so repeated draws of the same layer are free.
 */
object BitmapCache {

    /** Total decoded-bitmap memory budget. Tuned for low-RAM devices; safe to raise on higher-end targets. */
    private const val MAX_CACHE_BYTES = 48L * 1024 * 1024

    private var currentBytes = 0L

    // accessOrder = true gives us LRU iteration order for manual trimming below.
    private val cache = Collections.synchronizedMap(LinkedHashMap<String, Bitmap>(16, 0.75f, true))

    /**
     * Decodes the image at [path], downsampled to fit within [reqWidth] x [reqHeight],
     * caching the result. Pass `Int.MAX_VALUE` for both dimensions to decode at full
     * resolution (used by the exporter, which needs full quality).
     *
     * Returns null if the file is missing, unreadable, or not a valid image — callers
     * should fall back to a placeholder in that case, same as before this cache existed.
     */
    @Synchronized
    fun decodeSampled(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (path.isEmpty()) return null
        val key = "$path:$reqWidth:$reqHeight"
        cache[key]?.let { existing -> if (!existing.isRecycled) return existing else cache.remove(key) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

        cache[key] = bitmap
        currentBytes += bitmap.byteCount
        trimToBudget()
        return bitmap
    }

    private fun trimToBudget() {
        val iterator = cache.entries.iterator()
        while (currentBytes > MAX_CACHE_BYTES && iterator.hasNext()) {
            val entry = iterator.next()
            currentBytes -= entry.value.byteCount
            iterator.remove()
        }
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || reqWidth == Int.MAX_VALUE || reqHeight == Int.MAX_VALUE) return 1
        var inSampleSize = 1
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** Drop all cached entries for [path] — call this after the user edits/replaces that file on disk. */
    @Synchronized
    fun invalidate(path: String) {
        val prefix = "$path:"
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.startsWith(prefix)) {
                currentBytes -= entry.value.byteCount
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        currentBytes = 0L
    }
}
