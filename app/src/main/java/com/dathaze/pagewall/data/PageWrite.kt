package com.dathaze.pagewall.data

/**
 * Which files stop being needed when the pages change.
 *
 * Two mistakes are possible here and both lose a photograph, so the rule lives on its own where
 * it can be tested rather than being worked out slot by slot at each write.
 *
 * The first mistake is per-slot thinking: "this page's photo changed, so delete the old file."
 * Nothing says another page is not pointing at that same file — a restored backup can carry one
 * picture for two pages — and deleting it blanks a page nobody touched.
 *
 * The second is order. A file may only be deleted once the assignment that no longer names it is
 * safely on disk. The other way round, an interruption in between leaves the assignment naming a
 * file that has already been deleted, which is the one failure no later run can repair.
 */
object PageWrite {

    /** Every file the pages refer to. */
    fun filesUsedBy(pages: List<PageConfig>): Set<String> = pages
        .flatMap { listOfNotNull(it.mediaFile, it.posterFile, it.audioFile) }
        .toSet()

    /**
     * Files that [before] referred to and [after] does not, so nothing is left pointing at them.
     *
     * Deliberately the whole-list difference: a file still named by any page, even a different
     * one, is not an orphan.
     */
    fun orphans(before: List<PageConfig>, after: List<PageConfig>): Set<String> =
        filesUsedBy(before) - filesUsedBy(after)
}
