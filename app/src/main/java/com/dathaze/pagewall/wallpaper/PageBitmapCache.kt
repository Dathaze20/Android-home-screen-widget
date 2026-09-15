package com.dathaze.pagewall.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Holds decoded page pictures.
 *
 * The wallpaper process has a modest heap and must survive the user flicking across every page,
 * so the cache is sized in bytes rather than entries and evicted bitmaps are left to the GC
 * (never recycled here: a crossfade may still be drawing the outgoing page).
 */
class PageBitmapCache(maxBytes: Int = defaultBudget()) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Returns the decoded bitmap for [file], decoding and caching it on first use. */
    fun get(file: File): Bitmap? {
        val key = "${file.absolutePath}:${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()

    companion object {
        /** A quarter of the heap, capped so a huge-heap device does not hoard memory for pictures. */
        fun defaultBudget(): Int {
            val heapBytes = Runtime.getRuntime().maxMemory()
            return (heapBytes / 4).coerceAtMost(64L * 1024 * 1024).toInt()
        }
    }
}
