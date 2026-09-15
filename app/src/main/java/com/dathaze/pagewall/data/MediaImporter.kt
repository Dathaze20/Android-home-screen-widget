package com.dathaze.pagewall.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Copies picked media out of the gallery and into the app's private storage.
 *
 * Images are downsampled to roughly the screen size on the way in. A 50 MP photo decoded at full
 * resolution is ~200 MB in memory, and the wallpaper process gets a small heap, so importing at
 * display scale is what keeps the engine from being killed mid-swipe.
 */
object MediaImporter {

    private const val TAG = "MediaImporter"
    private const val JPEG_QUALITY = 92

    /**
     * Imports [uri] as the picture for [pageIndex], returning the stored file name.
     *
     * [targetWidth]/[targetHeight] should be the display size; the image is stored at up to
     * 1.3x that, which leaves headroom for parallax drift without wasting memory.
     */
    fun importImage(
        context: Context,
        store: PageStore,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): String? {
        val maxWidth = (targetWidth * 1.3f).toInt().coerceAtLeast(720)
        val maxHeight = (targetHeight * 1.3f).toInt().coerceAtLeast(1280)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.onFailure {
            Log.w(TAG, "Could not read bounds for $uri", it)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull() ?: return null

        val rotation = readRotation(context, uri)
        val oriented = if (rotation == 0) decoded else rotate(decoded, rotation)

        // Page files are named by slot, with a timestamp so a replacement never collides with a
        // bitmap the engine still has open from the previous picture.
        val fileName = "page_${pageIndex}_${System.currentTimeMillis()}.jpg"
        val target = File(store.mediaDir, fileName)
        val written = runCatching {
            FileOutputStream(target).use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }.isSuccess
        oriented.recycle()
        if (decoded !== oriented) decoded.recycle()

        if (!written) {
            target.delete()
            return null
        }
        return fileName
    }

    /** Copies an audio file in verbatim; no re-encoding, so the track stays as the user picked it. */
    fun importAudio(context: Context, store: PageStore, uri: Uri, pageIndex: Int): Pair<String, String>? {
        val displayName = queryDisplayName(context, uri)
        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() } ?: "mp3"
        val fileName = "audio_${pageIndex}_${System.currentTimeMillis()}.$extension"
        val target = File(store.mediaDir, fileName)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("no stream for $uri")
        }.isSuccess
        if (!copied) {
            target.delete()
            return null
        }
        val title = displayName?.substringBeforeLast('.')?.takeIf { it.isNotEmpty() } ?: "Track"
        return fileName to title
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun readRotation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrDefault(source)
    }

    /** Largest power-of-two subsample that still covers the target box. */
    private fun sampleSizeFor(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        return sample
    }
}
