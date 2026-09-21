package com.dathaze.pagewall.data

/**
 * Decides which page the wallpaper should show at the moments it cannot observe a swipe.
 *
 * Touch tracking only ever learns about page changes it sees as gestures. Three moments produce
 * none: returning to the launcher from an app (the launcher may jump to its own home page), the
 * engine being recreated after the process died, and the user changing the page count. Each is
 * handled deliberately here rather than left to drift.
 *
 * Pure arithmetic, no Android imports, so each rule is unit tested.
 */
object SyncPolicy {

    /**
     * The page to show when the wallpaper becomes visible again.
     *
     * @param screenWasOff true when the display turned off while hidden, so this is an unlock
     *   rather than a return from an app. The launcher is still on whatever page it was on, so
     *   the page is kept.
     *
     * Android offers no non-invasive way to tell the Home button from Back, so this is a setting
     * rather than a guess: with it off, the page is always kept.
     */
    fun pageOnVisible(
        mode: DetectionMode,
        syncOnReturnHome: Boolean,
        screenWasOff: Boolean,
        defaultHomePage: Int,
        currentPage: Int,
        pageCount: Int,
    ): Int {
        val kept = currentPage.coerceIn(0, maxPage(pageCount))
        // Offset tracking learns the real page from the launcher within a frame of becoming
        // visible, so it never needs help.
        if (mode != DetectionMode.TOUCH) return kept
        if (!syncOnReturnHome) return kept
        // An unlock is not a return from an app: the launcher did not move.
        if (screenWasOff) return kept
        return defaultHomePage.coerceIn(0, maxPage(pageCount))
    }

    /**
     * The page a freshly created engine should start on.
     *
     * After a process death there is no trustworthy offset to recover the launcher's page from
     * when the launcher reports a fixed one, and the stored page may be arbitrarily stale. The
     * configured home screen is the better guess; offset tracking keeps the stored page because
     * the launcher will correct it immediately.
     */
    fun pageOnEngineStart(
        mode: DetectionMode,
        defaultHomePage: Int,
        storedPage: Int,
        pageCount: Int,
    ): Int = when (mode) {
        DetectionMode.TOUCH -> defaultHomePage.coerceIn(0, maxPage(pageCount))
        DetectionMode.OFFSET -> storedPage.coerceIn(0, maxPage(pageCount))
    }

    /**
     * The page to hold after the page count or calibration changed.
     *
     * Under touch tracking the offset is a frozen constant, so deriving a page from it would
     * teleport the wallpaper to whatever that constant maps to — the middle page, for a launcher
     * reporting 0.5. The tracked page is kept and only clamped into the new range.
     */
    fun pageOnPageCountChange(
        mode: DetectionMode,
        currentPage: Int,
        pageCount: Int,
        lastOffset: Float,
        calibrationMin: Float,
        calibrationMax: Float,
    ): Int {
        if (mode == DetectionMode.TOUCH) return currentPage.coerceIn(0, maxPage(pageCount))
        if (!lastOffset.isFinite() || lastOffset < 0f) {
            return currentPage.coerceIn(0, maxPage(pageCount))
        }
        return PageMath.pageFor(lastOffset, pageCount, calibrationMin, calibrationMax)
    }

    private fun maxPage(pageCount: Int): Int = (pageCount - 1).coerceAtLeast(0)
}
