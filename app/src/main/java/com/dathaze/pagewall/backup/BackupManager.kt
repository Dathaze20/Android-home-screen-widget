package com.dathaze.pagewall.backup

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.PageAssignment
import com.dathaze.pagewall.data.PageStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where an export ended up, or why it did not. */
sealed interface ExportResult {
    data class Saved(val fileName: String, val where: String, val bytes: Long, val skipped: Int) : ExportResult
    data class Failed(val reason: String) : ExportResult
}

/** What a restore did. */
sealed interface RestoreResult {
    data class Done(val filled: Int, val replaced: Int, val kept: Int, val unchanged: Int, val settings: Boolean) : RestoreResult
    data class Failed(val reason: String) : RestoreResult
}

/**
 * Backup and restore against the live app.
 *
 * The decisions all live in [BackupArchive], [BackupPlan] and [BackupSettings], which are pure
 * and tested. What is left here is the Android-shaped work: putting a file in Downloads without
 * asking for a storage permission, reading one back out of whatever the file picker returned,
 * and committing a restore in an order that cannot leave the app half-restored.
 */
class BackupManager(private val context: Context) {

    private val store = PageStore(context)

    /**
     * Writes a backup into the phone's Downloads folder.
     *
     * Built in the cache first and only then copied out. Writing straight into Downloads would
     * mean a failure halfway through leaves a plausible-looking half file in the folder the
     * person will reach for later, and a backup that looks fine until the day it is needed is
     * worse than no backup.
     */
    fun exportToDownloads(now: Date = Date()): ExportResult {
        val staging = File(context.cacheDir, "backup-out").apply { mkdirs() }
        staging.listFiles()?.forEach { it.delete() }
        val temp = File(staging, "backup.zip")

        // Every page with something on it, not just the ones the launcher can reach today.
        // Turning the page count down hides a page without clearing it, and a backup that
        // skipped those would lose a photo the person still has.
        val pages = store.assignedPages().map { page ->
            BackupPage(
                index = page.index,
                mediaFile = page.mediaFile,
                mediaKind = page.mediaKind.name,
                posterFile = page.posterFile,
                audioFile = page.audioFile,
                audioTitle = page.audioTitle,
            )
        }
        val settings = BackupSettings.KEYS.mapNotNull { key ->
            BackupSettings.encode(store.rawSetting(key))?.let { key to it }
        }.toMap()

        val result = runCatching {
            temp.outputStream().use { out ->
                BackupArchive.write(
                    target = out,
                    pages = pages,
                    settings = settings,
                    appId = context.packageName,
                    appVersion = installedVersionName(),
                    pageCount = store.pageCount,
                    exported = ISO.format(now),
                    openFile = { name -> store.fileFor(name)?.inputStream() },
                )
            }
        }.getOrElse {
            Log.w(TAG, "Could not build the backup", it)
            staging.deleteRecursively()
            return ExportResult.Failed("Could not read your pages while building the backup.")
        }

        // Checked before it is handed over, so "saved" never means "a file of some kind exists".
        when (val check = BackupArchive.verify(temp)) {
            is BackupCheck.Damaged -> {
                staging.deleteRecursively()
                return ExportResult.Failed("The backup did not come out readable — ${check.reason}")
            }
            is BackupCheck.Ok -> Unit
        }

        val fileName = "page-wallpaper-backup-${DATE.format(now)}.zip"
        val bytes = temp.length()
        val where = runCatching { copyToDownloads(temp, fileName) }.getOrElse {
            Log.w(TAG, "Could not write to Downloads", it)
            staging.deleteRecursively()
            return ExportResult.Failed("Could not write to your Downloads folder.")
        }
        staging.deleteRecursively()

        return if (where == null) {
            ExportResult.Failed("Could not create the file in Downloads.")
        } else {
            ExportResult.Saved(fileName, where, bytes, result.skipped.size)
        }
    }

    /**
     * Copies [source] into Downloads and returns a human-readable location.
     *
     * MediaStore on Android 10 and up, which needs no permission at all; the file stays pending
     * until it is fully written so a file manager never shows a half-copied backup. Below that
     * there is no MediaStore Downloads collection, so the public directory is used instead and
     * the media scanner is told, which is why WRITE_EXTERNAL_STORAGE is declared with
     * maxSdkVersion 28 and asked for nowhere else.
     */
    private fun copyToDownloads(source: File, fileName: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out, COPY_BUFFER) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/$fileName"
        }

        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val out = File(dir, fileName)
        source.inputStream().use { input -> out.outputStream().use { input.copyTo(it, COPY_BUFFER) } }
        MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("application/zip"), null)
        return out.absolutePath
    }

    /**
     * Copies a picked file into the cache and checks it, changing nothing.
     *
     * The copy is not optional. A content URI is a stream the other app may not let us read
     * twice, and verifying a backup means reading every byte of it; doing that from a URI would
     * leave nothing to restore from afterwards.
     */
    fun inspect(uri: Uri): Pair<BackupCheck, File?> {
        val staging = File(context.cacheDir, "backup-in").apply { mkdirs() }
        staging.listFiles()?.forEach { it.delete() }
        val local = File(staging, "incoming.zip")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                local.outputStream().use { input.copyTo(it, COPY_BUFFER) }
            } ?: error("no stream")
        }.isSuccess

        if (!copied) {
            staging.deleteRecursively()
            return BackupCheck.Damaged("That file could not be opened.") to null
        }
        return BackupArchive.verify(local) to local
    }

    /** Throws away whatever [inspect] left in the cache. */
    fun discardIncoming() {
        File(context.cacheDir, "backup-in").deleteRecursively()
    }

    /**
     * Restores from a file already checked by [inspect].
     *
     * Order matters, and it is the order that makes a failure survivable. Everything is
     * extracted into a staging directory first; only once every file is on disk are they handed
     * to [MediaCommit], which puts all of them in place or none of them; and only once that has
     * succeeded are the page assignments written. A failure at any point leaves the app exactly
     * as it was, with nothing left behind in the folder the engine reads.
     */
    fun restore(
        local: File,
        manifest: BackupManifest,
        choice: ConflictChoice,
        restoreSettings: Boolean,
    ): RestoreResult {
        // Hidden pages count as occupied: a page beyond the current count still holds its
        // photo, so a restore must not read it as empty and write straight over it.
        val existing = store.assignedPages().associate { page ->
            page.index to ExistingPage(page.index, page.mediaFile, page.posterFile, page.audioFile)
        }
        val planned = BackupPlan.plan(existing, manifest.pages, choice)
        val summary = BackupPlan.summarise(planned)
        val needed = BackupPlan.filesNeeded(planned)

        val staging = File(context.cacheDir, "backup-stage").apply { mkdirs() }
        staging.deleteRecursively()
        staging.mkdirs()

        if (needed.isNotEmpty()) {
            val extracted = BackupArchive.extract(local, needed, staging)
            if (extracted == null || extracted != needed) {
                staging.deleteRecursively()
                return RestoreResult.Failed("The backup could not be unpacked — nothing was changed.")
            }
        }

        // Into the live folder, all of them or none: see [MediaCommit] for why a plain copy
        // onto the live names cannot be undone. Files are written under the names the backup
        // carries, so importing the same backup twice lands on the same names.
        val moved = MediaCommit.commit(staging, store.mediaDir, needed)
        staging.deleteRecursively()

        if (!moved) {
            return RestoreResult.Failed("The restored files could not be saved — your pages are unchanged.")
        }

        // Only now are assignments written, and only for pages the plan actually touches. Slots
        // the backup leaves empty are left alone rather than cleared: a backup carrying only a
        // track for a page says nothing about the picture already there, and clearing it would
        // delete a file the person never asked to lose.
        val assignments = planned
            .filter { it.action == PageAction.FILL_EMPTY || it.action == PageAction.REPLACE }
            .mapNotNull { p ->
                p.page.mediaFile?.let { media ->
                    PageAssignment(
                        index = p.index,
                        fileName = media,
                        kind = runCatching { MediaKind.valueOf(p.page.mediaKind) }
                            .getOrDefault(MediaKind.IMAGE),
                        posterFile = p.page.posterFile,
                    )
                }
            }
        if (assignments.isNotEmpty()) store.setMediaBatch(assignments)

        planned.filter { it.action == PageAction.FILL_EMPTY || it.action == PageAction.REPLACE }
            .forEach { p -> p.page.audioFile?.let { store.setAudio(p.index, it, p.page.audioTitle) } }

        if (restoreSettings) store.applySettings(BackupSettings.sanitise(manifest.settings))

        return RestoreResult.Done(
            filled = summary.fill,
            replaced = summary.replace,
            kept = summary.keep,
            unchanged = summary.unchanged,
            settings = restoreSettings,
        )
    }

    private fun installedVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private companion object {
        const val TAG = "BackupManager"
        const val COPY_BUFFER = 64 * 1024
        val DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }
}
