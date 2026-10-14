package com.dathaze.pagewall.data

/**
 * Which saved screens are being shown, and what "reapply" would do.
 *
 * A page can hold a picture without showing it. That is the whole of the feature: switching a
 * screen off hides its media and silences its soundtrack while leaving every file exactly where
 * it is, so switching it back on needs no gallery, no picker and no backup — the same bytes under
 * the same name on the same screen number.
 *
 * Kept free of Android types so the rules can be tested without a phone, which matters here
 * because the failure being guarded against is losing somebody's pictures.
 */
object ScreenActivation {

    /**
     * What the wallpaper should draw for [page].
     *
     * A switched-off page is presented as an empty one — same index, no media, no soundtrack. The
     * engine then does what it already does for a page nobody has filled in yet, which is to draw
     * a plain background. Nothing about rendering, page maths or swipe tracking has to know this
     * feature exists, and no other screen's picture can be shown in its place.
     */
    fun shown(page: PageConfig): PageConfig =
        if (page.active) page else PageConfig(index = page.index, active = false)

    /** What reapplying would do, worked out before anything is written. */
    data class ReapplyPlan(
        /** Screens that were switched off and would come back on. */
        val switchedOn: List<Int>,
        /** Screens already on, which are refreshed rather than changed. */
        val alreadyOn: List<Int>,
        /** Screens whose saved file could not be found, left exactly as they are. */
        val unreadable: List<Int>,
    ) {
        /** Screens showing their saved media once this has run. */
        val showing: Int get() = switchedOn.size + alreadyOn.size

        val hasProblems: Boolean get() = unreadable.isNotEmpty()
    }

    /**
     * Plans a reapply over [pages], using [exists] to ask whether a saved file is still there.
     *
     * A page whose file has gone is reported rather than switched on: turning it on would put a
     * blank screen up and call it success. It is not altered either — the assignment stays, in
     * case the file comes back with a restore.
     */
    fun planReapply(pages: List<PageConfig>, exists: (String) -> Boolean): ReapplyPlan {
        val switchedOn = mutableListOf<Int>()
        val alreadyOn = mutableListOf<Int>()
        val unreadable = mutableListOf<Int>()

        pages.filter { it.hasSavedMedia }.sortedBy { it.index }.forEach { page ->
            val files = listOfNotNull(page.mediaFile, page.posterFile, page.audioFile)
            when {
                files.any { !exists(it) } -> unreadable += page.index
                page.active -> alreadyOn += page.index
                else -> switchedOn += page.index
            }
        }
        return ReapplyPlan(switchedOn, alreadyOn, unreadable)
    }

    /** What to tell someone after a reapply. Counts screens, not files, and never overstates. */
    fun message(plan: ReapplyPlan): String {
        val good = when (plan.showing) {
            0 -> null
            1 -> "1 saved screen reactivated."
            else -> "${plan.showing} saved screens reactivated."
        }
        val bad = when {
            plan.unreadable.isEmpty() -> null
            plan.unreadable.size == 1 ->
                "Screen ${plan.unreadable.single() + 1} could not be read, so it was left alone."
            else -> {
                val list = plan.unreadable.map { it + 1 }
                val names = list.dropLast(1).joinToString(", ") + " and ${list.last()}"
                "Screens $names could not be read, so they were left alone."
            }
        }
        return listOfNotNull(good, bad).joinToString(" ")
            .ifEmpty { "Nothing is saved yet, so there was nothing to reapply." }
    }
}
