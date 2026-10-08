package com.dathaze.pagewall.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** What checking a backup file concluded. */
sealed interface BackupCheck {
    data class Ok(val manifest: BackupManifest, val totalBytes: Long) : BackupCheck

    /** [reason] is written to be shown to the person holding the file, not to a log. */
    data class Damaged(val reason: String) : BackupCheck
}

/** What writing a backup produced. [skipped] names pages whose files had gone missing. */
data class BackupWriteResult(
    val manifest: BackupManifest,
    val skipped: List<String>,
)

/**
 * Reading and writing the backup zip.
 *
 * Pure JVM — `java.util.zip`, `java.security` and nothing else — so the awkward cases can be
 * tested with real files on a real file system: a truncated archive, a declared picture that is
 * not inside, a picture whose bytes have changed since it was written, a zip that is not a
 * backup at all. Those are the cases that matter, because they are the ones where silently
 * carrying on would destroy what the person was trying to protect.
 *
 * Everything streams. A backup of a few video pages runs to hundreds of megabytes and must never
 * be held in memory on a phone.
 */
object BackupArchive {

    const val MANIFEST_NAME = "manifest.json"
    private const val BUFFER = 64 * 1024

    /**
     * Writes a backup into [target].
     *
     * The manifest goes in last, because each file's hash is only known once it has been
     * streamed. Zip entries are found by name rather than by position, so order does not matter
     * to anything reading it back.
     *
     * [openFile] returns the bytes for a file name, or null if it is no longer on disk. A page
     * referring to a file that has vanished has its reference dropped rather than being written
     * into a manifest that promises something the archive does not contain — and the name comes
     * back in [BackupWriteResult.skipped] so it can be reported instead of hidden.
     */
    fun write(
        target: OutputStream,
        pages: List<BackupPage>,
        settings: Map<String, String>,
        appId: String,
        appVersion: String,
        pageCount: Int,
        exported: String,
        openFile: (String) -> InputStream?,
    ): BackupWriteResult {
        val entries = mutableListOf<BackupEntry>()
        val skipped = mutableListOf<String>()
        val written = mutableSetOf<String>()

        ZipOutputStream(target.buffered()).use { zip ->
            val wanted = pages.flatMap {
                listOfNotNull(it.mediaFile, it.posterFile, it.audioFile)
            }.distinct()

            wanted.forEach { name ->
                if (!BackupEntry.isSafeName(name)) { skipped += name; return@forEach }
                val source = openFile(name)
                if (source == null) { skipped += name; return@forEach }
                val entry = source.use { input -> copyInto(zip, name, input) }
                entries += entry
                written += name
            }

            // Pages keep only the references that actually made it into the archive, so the
            // manifest never promises a file that is not here.
            val kept = pages.map { page ->
                page.copy(
                    mediaFile = page.mediaFile?.takeIf { it in written },
                    posterFile = page.posterFile?.takeIf { it in written },
                    audioFile = page.audioFile?.takeIf { it in written },
                )
            }
            val manifest = BackupManifest(
                version = BackupManifest.CURRENT_VERSION,
                exported = exported,
                appId = appId,
                appVersion = appVersion,
                pageCount = pageCount,
                pages = kept,
                settings = settings,
                files = entries,
            )
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            return BackupWriteResult(manifest, skipped)
        }
    }

    private fun copyInto(zip: ZipOutputStream, name: String, input: InputStream): BackupEntry {
        zip.putNextEntry(ZipEntry("${BackupEntry.MEDIA_PREFIX}$name"))
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            zip.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            total += read
        }
        zip.closeEntry()
        return BackupEntry(name, total, digest.digest().toHex())
    }

    /**
     * Checks [file] without extracting or changing anything.
     *
     * Every declared file is opened and hashed, so this answers "could this actually be
     * restored" rather than "does this look like a zip". That costs a full read of the archive,
     * which is the right trade for the one question a backup exists to answer.
     */
    fun verify(file: File): BackupCheck {
        if (!file.exists()) return BackupCheck.Damaged("That file is no longer there.")
        if (file.length() == 0L) return BackupCheck.Damaged("That file is empty.")

        val zip = runCatching { ZipFile(file) }.getOrNull()
            ?: return BackupCheck.Damaged("That is not a readable zip file — it may have been cut short while copying.")

        zip.use {
            val manifestEntry = it.getEntry(MANIFEST_NAME)
                ?: return BackupCheck.Damaged("That zip is not a Page Wallpaper backup — it has no manifest.")

            val json = runCatching {
                it.getInputStream(manifestEntry).bufferedReader().use { r -> r.readText() }
            }.getOrNull() ?: return BackupCheck.Damaged("The backup's manifest could not be read.")

            val manifest = BackupManifest.parse(json)
                ?: return BackupCheck.Damaged("The backup's manifest is damaged or from a newer version of the app.")

            var total = 0L
            manifest.files.forEach { declared ->
                val entry = it.getEntry(declared.path)
                    ?: return BackupCheck.Damaged("The backup is incomplete — ${declared.name} is missing from it.")

                val actual = runCatching { hashOf(it.getInputStream(entry)) }.getOrNull()
                    ?: return BackupCheck.Damaged("The backup is damaged — ${declared.name} could not be read.")

                if (actual.second != declared.sizeBytes) {
                    return BackupCheck.Damaged("The backup is damaged — ${declared.name} is the wrong size.")
                }
                if (actual.first != declared.sha256) {
                    return BackupCheck.Damaged("The backup is damaged — ${declared.name} does not match its checksum.")
                }
                total += actual.second
            }
            return BackupCheck.Ok(manifest, total)
        }
    }

    /**
     * Extracts the named files into [destination], which must be a staging directory and not
     * the live media folder. Returns the names written, or null if anything went wrong — a
     * half-finished extraction is the caller's to throw away, not to commit.
     */
    fun extract(file: File, names: Set<String>, destination: File): Set<String>? {
        if (!destination.exists() && !destination.mkdirs()) return null
        val zip = runCatching { ZipFile(file) }.getOrNull() ?: return null
        val done = mutableSetOf<String>()
        zip.use {
            names.forEach { name ->
                if (!BackupEntry.isSafeName(name)) return null
                val entry = it.getEntry("${BackupEntry.MEDIA_PREFIX}$name") ?: return null
                val out = File(destination, name)
                val ok = runCatching {
                    it.getInputStream(entry).use { input ->
                        out.outputStream().buffered().use { sink -> input.copyTo(sink, BUFFER) }
                    }
                }.isSuccess
                if (!ok) return null
                done += name
            }
        }
        return done
    }

    /** Hash and byte count in one pass, so a large file is read once rather than twice. */
    private fun hashOf(input: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var total = 0L
        input.use {
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }
        return digest.digest().toHex() to total
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
