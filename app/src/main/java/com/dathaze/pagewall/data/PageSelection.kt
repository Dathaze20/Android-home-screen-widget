package com.dathaze.pagewall.data

/**
 * Which saved pages a caller means.
 *
 * Assignments for pages beyond the current page count are kept on purpose: dialling the count
 * down and back up again must not lose a photo. That leaves two different right answers to
 * "what are my pages", and mixing them up is exactly how a backup quietly leaves someone's
 * pictures behind — so both answers live here, named, instead of being spelled out at each call
 * site and drifting apart.
 */
object PageSelection {

    /** What the home screen shows: one entry per page the launcher has, empty ones included. */
    fun visible(saved: List<PageConfig>, pageCount: Int): List<PageConfig> {
        val byIndex = saved.associateBy { it.index }
        return (0 until pageCount).map { byIndex[it] ?: PageConfig(it) }
    }

    /**
     * Every page with something on it, whatever the page count happens to be today.
     *
     * This is what a backup has to carry. A page holding a photo the launcher cannot reach right
     * now is still the person's photo, and a backup that omits it is a backup that loses it.
     */
    fun assigned(saved: List<PageConfig>): List<PageConfig> = saved
        .filter { it.hasMedia || it.hasAudio }
        // One entry per page, the same way [visible] keys by index: a stored list that somehow
        // names a page twice must not produce a backup that contradicts itself.
        .distinctBy { it.index }
        .sortedBy { it.index }
}
