package com.dathaze.pagewall.backup

import java.io.File

/**
 * Puts restored files into the live media folder, all of them or none of them.
 *
 * Copying straight onto the live names is what a restore must never do. Opening a file the
 * wallpaper is already showing truncates it before the first byte of the replacement arrives, so
 * a copy that dies halfway — the card fills up, the archive turns out to be short — leaves a page
 * pointing at a file that is no longer a picture. There is no undoing that: the original is gone.
 *
 * So the work is split in two. First every file is copied in beside its destination under a
 * working name, which touches nothing the engine reads; running out of room here costs only the
 * working files. Only once every byte is safely on disk are they swapped in by rename, which is
 * atomic on one file system, with whatever was there moved aside first so a failure part-way
 * through can put it all back.
 *
 * Kept free of Android types so the thing that moves someone's only copy of their photos can be
 * tested — including its failure paths — without a phone.
 */
object MediaCommit {

    private const val BUFFER = 64 * 1024

    /** The copy in progress. Never a name the app itself assigns, so it cannot shadow real media. */
    const val INCOMING_SUFFIX = ".pw-incoming"

    /** What used to be at a name, kept until the swap has fully succeeded. */
    const val DISPLACED_SUFFIX = ".pw-displaced"

    /**
     * Copies every name in [names] from [from] into [into], committing all of them or none.
     *
     * Returns false when nothing was changed. A name that is not a plain file name, or that
     * collides with the working names used here, is refused outright rather than worked around.
     */
    fun commit(from: File, into: File, names: Collection<String>): Boolean {
        if (names.isEmpty()) return true
        if (names.any { !isCommittable(it) }) return false
        if (!into.isDirectory && !into.mkdirs()) return false

        recoverLeftovers(into)

        // Step one: get the bytes onto the disk without touching anything live.
        val staged = mutableListOf<Pair<File, File>>()
        val copied = runCatching {
            names.forEach { name ->
                val source = File(from, name)
                val incoming = File(into, name + INCOMING_SUFFIX)
                source.inputStream().use { input ->
                    incoming.outputStream().use { output -> input.copyTo(output, BUFFER) }
                }
                // A short write does not throw on every file system, and a silently truncated
                // photo is the one outcome this whole dance exists to prevent.
                if (incoming.length() != source.length()) error("short copy of $name")
                staged += incoming to File(into, name)
            }
        }.isSuccess

        if (!copied) {
            names.forEach { File(into, it + INCOMING_SUFFIX).delete() }
            return false
        }

        // Step two: swap them in, remembering enough to undo it.
        val displaced = mutableListOf<Pair<File, File>>()
        val swapped = mutableListOf<File>()
        val swapOk = runCatching {
            staged.forEach { (incoming, target) ->
                if (target.exists()) {
                    val aside = File(into, target.name + DISPLACED_SUFFIX)
                    aside.delete()
                    if (!target.renameTo(aside)) error("could not move ${target.name} aside")
                    displaced += target to aside
                }
                if (!incoming.renameTo(target)) error("could not put ${target.name} in place")
                swapped += target
            }
        }.isSuccess

        if (!swapOk) {
            swapped.forEach { it.delete() }
            displaced.forEach { (target, aside) -> aside.renameTo(target) }
            staged.forEach { (incoming, _) -> incoming.delete() }
            return false
        }

        displaced.forEach { (_, aside) -> aside.delete() }
        return true
    }

    /**
     * Cleans up after a previous attempt that was killed outright.
     *
     * A displaced file is the only copy of what used to be at that name, so it is put back when
     * nothing has taken its place. Only when the name is occupied again — the swap did finish —
     * is it safe to throw away.
     */
    fun recoverLeftovers(into: File) {
        val leftovers = into.listFiles() ?: return
        leftovers.forEach { file ->
            when {
                file.name.endsWith(INCOMING_SUFFIX) -> file.delete()
                file.name.endsWith(DISPLACED_SUFFIX) -> {
                    val target = File(into, file.name.removeSuffix(DISPLACED_SUFFIX))
                    if (target.exists()) file.delete() else file.renameTo(target)
                }
            }
        }
    }

    /** A name that is safe to join to a path and cannot be mistaken for a working file. */
    fun isCommittable(name: String): Boolean =
        BackupEntry.isSafeName(name) &&
            !name.endsWith(INCOMING_SUFFIX) &&
            !name.endsWith(DISPLACED_SUFFIX)
}
