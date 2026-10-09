package com.dathaze.pagewall.backup

/**
 * Whether the pages can be backed up completely, asked before anything is written.
 *
 * A backup missing one of the photographs it claims to hold is the worst thing this feature
 * could produce, because it does not look like a failure. It copies to Downloads, reports a size
 * and a file name, sits in Drive for a year, and only turns out to be incomplete on the day
 * somebody is restoring it because their phone is gone.
 *
 * So an export that cannot carry everything is refused outright rather than trimmed to fit, and
 * refused here — before a file is created — so there is nothing half-written to clean up and
 * nothing in Downloads to mistake for a backup.
 */
object BackupExport {

    /**
     * Files the pages refer to that [exists] cannot find, or that could not be carried safely.
     *
     * An unsafe name counts as missing: it cannot go into the archive, so a backup claiming it
     * would be incomplete in exactly the same way.
     */
    fun missingFiles(pages: List<BackupPage>, exists: (String) -> Boolean): List<String> = pages
        .flatMap { listOfNotNull(it.mediaFile, it.posterFile, it.audioFile) }
        .distinct()
        .filter { !BackupEntry.isSafeName(it) || !exists(it) }

    /**
     * What to tell someone whose export was refused.
     *
     * Names the pages rather than the file names: a file name stamped with a millisecond means
     * nothing to anybody, but "page 3" is something they can go and look at, and reassigning or
     * clearing that page is what makes the export work.
     */
    fun incompleteReason(pages: List<BackupPage>, missing: List<String>): String {
        val gone = missing.toSet()
        val affected = pages
            .filter { page ->
                listOfNotNull(page.mediaFile, page.posterFile, page.audioFile).any { it in gone }
            }
            .map { it.index + 1 }
            .distinct()
            .sorted()

        val where = when {
            affected.isEmpty() -> "A file this backup needs is missing"
            affected.size == 1 -> "Page ${affected.single()} refers to a file that is missing"
            else -> "Pages ${affected.joinToString(", ")} refer to files that are missing"
        }
        return "$where, so this backup would be incomplete. Nothing was saved. " +
            "Reassign or clear the page, then export again."
    }
}
