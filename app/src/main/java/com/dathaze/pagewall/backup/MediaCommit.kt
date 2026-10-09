package com.dathaze.pagewall.backup

import java.io.File
import java.security.MessageDigest

/**
 * Puts restored files into the live media folder, all of them or none of them, **without ever
 * overwriting or deleting anything already in it.**
 *
 * That is the whole design, and it is what makes an interruption survivable. The dangerous
 * version of this job replaces files in place: opening a file the wallpaper is already showing
 * truncates it before the first byte of the replacement arrives, and being killed between
 * replacing the file and persisting the new assignment leaves the page naming a file whose
 * contents are gone. There is no undoing that — the original is not anywhere else.
 *
 * So nothing here touches an existing file. A name is only ever written if it is free; if the
 * name is taken, the bytes are compared, and either they are the same file already here (a
 * re-import, nothing to do) or the restored copy lands under a name of its own. The caller is
 * told which name each file ended up under, so the assignment it writes afterwards names a file
 * that is certainly on disk.
 *
 * What that leaves, at every instant: every file the saved assignments refer to still exists.
 * Killed during the copies, there are working files to tidy and nothing else. Killed during the
 * moves, there are unreferenced files to tidy and nothing else. Killed after this returns but
 * before the assignments are written, the restore simply did not happen.
 *
 * Kept free of Android types so the thing that moves someone's only copy of their photographs
 * can be tested — failure paths included — without a phone.
 */
object MediaCommit {

    private const val BUFFER = 64 * 1024

    /** A copy in progress. Never a name the app itself assigns, so it cannot shadow real media. */
    const val INCOMING_SUFFIX = ".pw-incoming"

    /**
     * How long a working file is left alone before it is treated as abandoned.
     *
     * A restore runs in the background while the rest of the app carries on, and anything that
     * constructs a [com.dathaze.pagewall.data.PageStore] tidies up. Without this, tidying up
     * could delete a copy that is still being written.
     */
    const val TEMP_MIN_AGE_MILLIS = 5 * 60 * 1000L

    /** How many times a taken name is varied before giving up. */
    private const val MAX_VARIANTS = 999

    /**
     * Copies every name in [names] from [from] into [into], committing all of them or none.
     *
     * Returns the name each file actually ended up under — usually the name it came with — or
     * null when nothing was committed, in which case [into] is exactly as it was found.
     */
    fun commit(from: File, into: File, names: Collection<String>): Map<String, String>? {
        if (names.isEmpty()) return emptyMap()
        if (names.any { !isCommittable(it) }) return null
        if (!into.isDirectory && !into.mkdirs()) return null

        recoverLeftovers(into)

        val landed = LinkedHashMap<String, String>()
        val temps = mutableListOf<File>()
        val added = mutableListOf<File>()

        val ok = runCatching {
            // Step one: every byte onto the disk under a working name. Nothing live is touched,
            // so running out of room here costs only the working files.
            names.forEach { name ->
                val source = File(from, name)
                if (!source.isFile) error("$name was not extracted")

                val here = File(into, name)
                if (here.isFile && sameBytes(here, source)) {
                    // Byte for byte what is already here: importing the same backup again.
                    // Writing it would be a long way round to no change at all.
                    landed[name] = name
                    return@forEach
                }

                val target = freeName(into, name, landed.values.toSet())
                    ?: error("no free name for $name")
                val temp = File(into, target + INCOMING_SUFFIX)
                copy(source, temp)
                temps += temp
                landed[name] = target
            }

            // Step two: move each into place. Every destination name was free when it was
            // chosen, so nothing is displaced and nothing existing can be lost.
            landed.forEach { (_, target) ->
                val temp = File(into, target + INCOMING_SUFFIX)
                if (!temp.isFile) return@forEach
                val destination = File(into, target)
                if (destination.exists()) error("$target was taken while restoring")
                if (!temp.renameTo(destination)) error("could not put $target in place")
                added += destination
            }
        }.isSuccess

        if (!ok) {
            // Everything this call created, and only that: the folder goes back to how it was.
            added.forEach { it.delete() }
            temps.forEach { it.delete() }
            return null
        }

        return landed
    }

    /**
     * Deletes working files left behind by a run that was killed outright.
     *
     * Only files old enough to be certainly abandoned, because a restore in progress is writing
     * files that look exactly like these. Nothing else is ever removed: a file under a real name
     * may be the only copy of a photograph, and this is not the place that decides such a thing
     * is unwanted.
     */
    fun recoverLeftovers(
        into: File,
        now: Long = System.currentTimeMillis(),
        minAgeMillis: Long = TEMP_MIN_AGE_MILLIS,
    ) {
        val files = into.listFiles() ?: return
        files.forEach { file ->
            if (!file.name.endsWith(INCOMING_SUFFIX)) return@forEach
            if (now - file.lastModified() < minAgeMillis) return@forEach
            file.delete()
        }
    }

    /** A name that is safe to join to a path and cannot be mistaken for a working file. */
    fun isCommittable(name: String): Boolean =
        BackupEntry.isSafeName(name) && !name.endsWith(INCOMING_SUFFIX)

    /**
     * [name] if it is free, otherwise the same name with a number worked into it.
     *
     * Reached only when a backup carries a file whose name is already in use here — which, since
     * names are stamped with the millisecond they were imported, means a backup taken on this
     * phone whose file has since been replaced by different bytes. Rare, and the answer is still
     * not to overwrite: the restored copy gets its own name.
     */
    fun freeName(into: File, name: String, taken: Set<String> = emptySet()): String? {
        if (isFree(into, name, taken)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.take(dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        for (n in 1..MAX_VARIANTS) {
            val candidate = "$stem-$n$extension"
            if (isCommittable(candidate) && isFree(into, candidate, taken)) return candidate
        }
        return null
    }

    private fun isFree(into: File, name: String, taken: Set<String>): Boolean =
        name !in taken &&
            !File(into, name).exists() &&
            !File(into, name + INCOMING_SUFFIX).exists()

    private fun copy(source: File, target: File) {
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, BUFFER) }
        }
        // A short write does not throw on every file system, and a silently truncated photo is
        // the one outcome this whole dance exists to prevent.
        if (target.length() != source.length()) error("short copy of ${source.name}")
    }

    /**
     * Whether two files hold the same bytes.
     *
     * Asked only about a name that is already taken, so the cost is a hash of one file in a case
     * that practically does not arise — not a hash of every page on every import.
     */
    private fun sameBytes(a: File, b: File): Boolean =
        a.length() == b.length() && hash(a) == hash(b)

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
