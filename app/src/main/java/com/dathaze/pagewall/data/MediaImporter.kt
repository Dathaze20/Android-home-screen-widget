package com.dathaze.pagewall.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Outcome of importing one piece of media, ready to be handed to [PageStore.setMedia]. */
sealed interface ImportResult {
    data class Success(
        val fileName: String,
        val kind: MediaKind,
        val posterFile: String? = null,
    ) : ImportResult

    data class Failure(val message: String) : ImportResult
}

/**
 * Copies picked media out of the gallery and into the app's private storage.
 *
 * Three different jobs hide behind one entry point:
 *  - **Photos** are re-encoded at roughly screen size. A 50 MP photo decoded at full resolution is
 *    around 200 MB in memory, and the wallpaper process gets an ordinary app heap, so importing at
 *    display scale is what keeps the engine from being killed mid-swipe.
 *  - **GIFs** are copied byte for byte. Re-encoding would flatten the animation.
 *  - **Videos** are copied byte for byte, and a still frame is extracted alongside so the settings
 *    screen and the widget have a thumbnail without starting a decoder.
 */
object MediaImporter {

    private const val TAG = "MediaImporter"
    private const val JPEG_QUALITY = 92
    private const val POSTER_QUALITY = 85

    /** Above this, a copy would eat noticeable storage and take long enough to feel broken. */
    const val MAX_VIDEO_BYTES = 250L * 1024 * 1024

    /**
     * Imports [uri] as the media for [pageIndex].
     *
     * [targetWidth]/[targetHeight] should be the display size; still photos are stored at up to
     * 1.15x that, which leaves headroom for the parallax drift without wasting memory.
     */
    fun importMedia(
        context: Context,
        store: PageStore,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): ImportResult {
        val mime = context.contentResolver.getType(uri)
        return when (MediaKind.fromMimeType(mime)) {
            MediaKind.VIDEO -> importVideo(context, store, uri, pageIndex)
            MediaKind.GIF -> importVerbatim(context, store, uri, pageIndex, MediaKind.GIF, mime)
            MediaKind.IMAGE -> importPhoto(context, store, uri, pageIndex, targetWidth, targetHeight)
        }
    }

    private fun importPhoto(
        context: Context,
        store: PageStore,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): ImportResult {
        val maxWidth = (targetWidth * 1.15f).toInt().coerceAtLeast(720)
        val maxHeight = (targetHeight * 1.15f).toInt().coerceAtLeast(1280)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.onFailure {
            Log.w(TAG, "Could not read bounds for $uri", it)
            return ImportResult.Failure("Could not open that photo")
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ImportResult.Failure("That file is not a readable photo")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull() ?: return ImportResult.Failure("Could not decode that photo")

        val orientation = readOrientation(context, uri)
        val oriented = if (orientation.isIdentity) decoded else orient(decoded, orientation)

        // Files are named by slot with a timestamp, so a replacement never collides with a bitmap
        // the engine still has open from the previous picture.
        val fileName = "page_${pageIndex}_${System.currentTimeMillis()}.jpg"
        val written = writeJpeg(oriented, File(store.mediaDir, fileName), JPEG_QUALITY)

        oriented.recycle()
        if (decoded !== oriented) decoded.recycle()

        return if (written) {
            ImportResult.Success(fileName, MediaKind.IMAGE)
        } else {
            ImportResult.Failure("Could not save that photo")
        }
    }

    /** Copies a file through unchanged, used for GIFs where re-encoding would lose the animation. */
    private fun importVerbatim(
        context: Context,
        store: PageStore,
        uri: Uri,
        pageIndex: Int,
        kind: MediaKind,
        mime: String?,
    ): ImportResult {
        val extension = extensionFor(context, uri, mime) ?: "gif"
        val fileName = "page_${pageIndex}_${System.currentTimeMillis()}.$extension"
        val target = File(store.mediaDir, fileName)
        return if (copyTo(context, uri, target)) {
            ImportResult.Success(fileName, kind)
        } else {
            target.delete()
            ImportResult.Failure("Could not copy that file")
        }
    }

    private fun importVideo(
        context: Context,
        store: PageStore,
        uri: Uri,
        pageIndex: Int,
    ): ImportResult {
        val size = querySize(context, uri)
        if (size != null && size > MAX_VIDEO_BYTES) {
            val megabytes = size / (1024 * 1024)
            return ImportResult.Failure(
                "That video is ${megabytes} MB. Trim it under ${MAX_VIDEO_BYTES / (1024 * 1024)} MB first."
            )
        }

        val extension = extensionFor(context, uri, context.contentResolver.getType(uri)) ?: "mp4"
        val fileName = "page_${pageIndex}_${System.currentTimeMillis()}.$extension"
        val target = File(store.mediaDir, fileName)
        if (!copyTo(context, uri, target)) {
            target.delete()
            return ImportResult.Failure("Could not copy that video")
        }

        val posterFile = extractPoster(store, target, pageIndex)
        return ImportResult.Success(fileName, MediaKind.VIDEO, posterFile)
    }

    /** Grabs a frame from the video so the UI has a thumbnail. A missing poster is not fatal. */
    private fun extractPoster(store: PageStore, video: File, pageIndex: Int): String? {
        val retriever = MediaMetadataRetriever()
        val frame = runCatching {
            retriever.setDataSource(video.absolutePath)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull()
        runCatching { retriever.release() }

        if (frame == null) return null
        val posterName = "poster_${pageIndex}_${System.currentTimeMillis()}.jpg"
        val written = writeJpeg(frame, File(store.mediaDir, posterName), POSTER_QUALITY)
        frame.recycle()
        return posterName.takeIf { written }
    }

    /** Copies an audio file in verbatim; no re-encoding, so the track stays as the user picked it. */
    fun importAudio(context: Context, store: PageStore, uri: Uri, pageIndex: Int): Pair<String, String>? {
        val displayName = queryDisplayName(context, uri)
        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() } ?: "mp3"
        val fileName = "audio_${pageIndex}_${System.currentTimeMillis()}.$extension"
        val target = File(store.mediaDir, fileName)
        if (!copyTo(context, uri, target)) {
            target.delete()
            return null
        }
        val title = displayName?.substringBeforeLast('.')?.takeIf { it.isNotEmpty() } ?: "Track"
        return fileName to title
    }

    private fun copyTo(context: Context, uri: Uri, target: File): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("no stream for $uri")
    }.onFailure { Log.w(TAG, "Copy failed for $uri", it) }.isSuccess

    private fun writeJpeg(bitmap: Bitmap, target: File, quality: Int): Boolean {
        // compress() reports failure by returning false, not by throwing, so the return value has
        // to be checked: otherwise a half-written file counts as a successful import.
        val compressed = runCatching {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        }.onFailure { Log.w(TAG, "Could not write ${target.name}", it) }
            .getOrDefault(false)

        if (!compressed || target.length() == 0L) {
            Log.w(TAG, "JPEG encode failed for ${target.name}")
            target.delete()
            return false
        }
        return true
    }

    private fun extensionFor(context: Context, uri: Uri, mime: String?): String? {
        queryDisplayName(context, uri)?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return mime?.substringAfterLast('/', "")?.takeIf { it.isNotEmpty() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        queryColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor -> cursor.getString(0) }

    private fun querySize(context: Context, uri: Uri): Long? =
        queryColumn(context, uri, OpenableColumns.SIZE) { cursor ->
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    private fun <T> queryColumn(
        context: Context,
        uri: Uri,
        column: String,
        read: (android.database.Cursor) -> T?,
    ): T? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) read(cursor) else null
        }
    }.getOrNull()

    /**
     * The EXIF orientation as a rotation plus a mirror.
     *
     * Front-camera shots and edited photos carry the flipped and transposed orientations, which
     * a rotation alone cannot undo: handling only 90/180/270 left those importing mirrored.
     */
    private data class Orientation(val degrees: Int, val mirrored: Boolean) {
        val isIdentity: Boolean get() = degrees == 0 && !mirrored
    }

    private fun readOrientation(context: Context, uri: Uri): Orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> Orientation(90, false)
                ExifInterface.ORIENTATION_ROTATE_180 -> Orientation(180, false)
                ExifInterface.ORIENTATION_ROTATE_270 -> Orientation(270, false)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Orientation(0, true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> Orientation(180, true)
                ExifInterface.ORIENTATION_TRANSPOSE -> Orientation(90, true)
                ExifInterface.ORIENTATION_TRANSVERSE -> Orientation(270, true)
                else -> Orientation(0, false)
            }
        } ?: Orientation(0, false)
    }.getOrDefault(Orientation(0, false))

    private fun orient(source: Bitmap, orientation: Orientation): Bitmap {
        val matrix = Matrix().apply {
            if (orientation.degrees != 0) postRotate(orientation.degrees.toFloat())
            if (orientation.mirrored) postScale(-1f, 1f)
        }
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
