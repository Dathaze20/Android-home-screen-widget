package com.dathaze.pagewall.backup

/** What the person chose to do about pages that already have something on them. */
enum class ConflictChoice {
    /** Leave every occupied page exactly as it is. Empty pages are still filled. */
    KEEP_MINE,

    /** Let the backup overwrite occupied pages too. */
    USE_BACKUP,
}

/** What a restore would do to one page. */
enum class PageAction {
    /** The page is empty here and the backup has something for it. */
    FILL_EMPTY,

    /** The page is taken and the backup disagrees; the backup wins by explicit choice. */
    REPLACE,

    /** The page is taken and the backup disagrees; it was left alone. */
    KEEP,

    /** The page already holds exactly what the backup carries. Nothing to do. */
    UNCHANGED,
}

data class PlannedPage(val index: Int, val action: PageAction, val page: BackupPage)

/** A plain-language account of what a restore is about to do, for the confirmation dialog. */
data class RestoreSummary(
    val fill: Int,
    val replace: Int,
    val keep: Int,
    val unchanged: Int,
) {
    val changes: Int get() = fill + replace
    val hasConflicts: Boolean get() = replace + keep > 0
}

/**
 * Works out what a restore would change, before anything is written.
 *
 * The rule the whole feature rests on: **an occupied page is never overwritten unless that was
 * asked for.** Empty pages are filled without asking, because there is nothing there to lose.
 *
 * [PageAction.UNCHANGED] is what makes importing the same backup twice harmless. A page already
 * holding the file the backup carries is not written again, so no file is rewritten, no
 * assignment changes, and the second import reports honestly that there was nothing to do.
 */
object BackupPlan {

    fun plan(
        existing: Map<Int, ExistingPage>,
        backup: List<BackupPage>,
        choice: ConflictChoice,
    ): List<PlannedPage> = backup
        .filter { it.hasMedia }
        .map { page ->
            val here = existing[page.index]
            val action = when {
                here == null || !here.hasMedia -> PageAction.FILL_EMPTY
                here.matches(page) -> PageAction.UNCHANGED
                choice == ConflictChoice.USE_BACKUP -> PageAction.REPLACE
                else -> PageAction.KEEP
            }
            PlannedPage(page.index, action, page)
        }

    fun summarise(planned: List<PlannedPage>): RestoreSummary = RestoreSummary(
        fill = planned.count { it.action == PageAction.FILL_EMPTY },
        replace = planned.count { it.action == PageAction.REPLACE },
        keep = planned.count { it.action == PageAction.KEEP },
        unchanged = planned.count { it.action == PageAction.UNCHANGED },
    )

    /**
     * How many pages would be overwritten if the backup were allowed to win.
     *
     * Asked before the choice is made, so the dialog can offer the choice only when there is
     * genuinely something at stake and otherwise stay out of the way.
     */
    fun conflictCount(existing: Map<Int, ExistingPage>, backup: List<BackupPage>): Int =
        backup.count { page ->
            val here = existing[page.index]
            page.hasMedia && here != null && here.hasMedia && !here.matches(page)
        }

    /** The files a plan actually needs out of the archive. */
    fun filesNeeded(planned: List<PlannedPage>): Set<String> = planned
        .filter { it.action == PageAction.FILL_EMPTY || it.action == PageAction.REPLACE }
        .flatMap { listOfNotNull(it.page.mediaFile, it.page.posterFile, it.page.audioFile) }
        .toSet()
}

/**
 * The part of a live page this decision depends on, kept free of Android types so the rule can
 * be tested without one.
 */
data class ExistingPage(
    val index: Int,
    val mediaFile: String?,
    val posterFile: String? = null,
    val audioFile: String? = null,
) {
    val hasMedia: Boolean get() = mediaFile != null

    /**
     * Whether this page already holds what the backup carries.
     *
     * Compared by file name because names are assigned once at import and never reused: the
     * same name means the same bytes. Comparing by content would mean hashing every page's
     * media on every import, to answer a question the name already answers.
     */
    fun matches(page: BackupPage): Boolean =
        mediaFile == page.mediaFile &&
            posterFile == page.posterFile &&
            audioFile == page.audioFile
}
