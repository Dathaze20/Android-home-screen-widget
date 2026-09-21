package com.dathaze.pagewall.wallpaper

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * Decodes and holds the drawable for each page.
 *
 * [ImageDecoder] is used rather than BitmapFactory because it returns an
 * [AnimatedImageDrawable] for an animated GIF or WebP and a plain drawable for a still photo,
 * which means the renderer can treat all of them identically.
 *
 * The cache is small and counted in entries: photos are already downsampled to display size on
 * import, and holding more than a few full-screen frames in a wallpaper process is how you get
 * killed halfway through a swipe.
 */
class PageMediaCache(maxEntries: Int = DEFAULT_ENTRIES) {

    private val cache = object : LruCache<String, Drawable>(maxEntries) {
        override fun entryRemoved(evicted: Boolean, key: String, old: Drawable, new: Drawable?) {
            // An evicted GIF would otherwise keep decoding frames in the background.
            (old as? AnimatedImageDrawable)?.stop()
        }
    }

    /**
     * Returns the drawable for [file], decoding on first use.
     *
     * [callback] is attached to animated drawables: without one they have nowhere to send their
     * "time for the next frame" invalidation, so the animation never advances.
     */
    fun get(file: File, maxWidth: Int, maxHeight: Int, callback: Drawable.Callback?): Drawable? {
        val key = "${file.absolutePath}:${file.lastModified()}:${maxWidth}x$maxHeight"
        cache.get(key)?.let { return it }

        val drawable = runCatching {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                decoder.setTargetSampleSize(
                    sampleSizeFor(info.size.width, info.size.height, maxWidth, maxHeight)
                )
                // Without this the decoder hands back a hardware bitmap, and a hardware bitmap
                // cannot be drawn onto the software canvas that SurfaceHolder.lockCanvas gives
                // out: drawing one throws, which kills the wallpaper on its first frame and
                // leaves the home screen black. Every frame here goes to a software canvas.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrElse {
            Log.w(TAG, "Could not decode ${file.name}", it)
            return null
        }

        if (drawable is AnimatedImageDrawable) {
            drawable.callback = callback
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
        cache.put(key, drawable)
        return drawable
    }

    fun clear() = cache.evictAll()

    /** Largest power-of-two subsample that still covers the target box. */
    private fun sampleSizeFor(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (maxWidth <= 0 || maxHeight <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val TAG = "PageMediaCache"
        const val DEFAULT_ENTRIES = 3
    }
}
