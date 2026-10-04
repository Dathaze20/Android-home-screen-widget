package com.dathaze.pagewall.data

/**
 * Whether the launcher has jumped back to its home screen on its own.
 *
 * Pressing Home while you are already on the home screen is the one page change Android gives a
 * live wallpaper no direct signal for. The wallpaper never becomes invisible, so the resync in
 * [SyncPolicy.pageOnVisible] never runs; under touch tracking there is no swipe to observe; and
 * One UI reports a fixed scroll offset, so the offset's *value* says nothing either.
 *
 * What may still be left is the offset callback's *timing*. A launcher calls back while its
 * workspace is moving, whoever started the move. So an offset report that arrives well clear of
 * any finger means the launcher scrolled by itself, and on a home screen the overwhelmingly
 * common reason for that is the Home button.
 *
 * That is a reasonable inference, not a fact Android tells us, which is why the setting behind it
 * is off until it has been shown to work on a particular phone. The settle window is the load
 * bearing part: a launcher keeps reporting for a while after a finger lifts, and treating that
 * tail as launcher-initiated would snap to screen 1 after every swipe.
 */
object HomeJumpPolicy {

    /** How long after a touch the launcher's own settling animation may still be reporting. */
    const val TOUCH_SETTLE_MS = 1_200L

    /** Minimum gap between two jumps, so one burst of reports cannot fire repeatedly. */
    const val JUMP_COOLDOWN_MS = 2_000L

    /**
     * True when an offset report should be read as "the launcher went home".
     *
     * @param msSinceLastTouch time since the last touch event the launcher forwarded. Large when
     *   no finger has been near the screen, which is the whole signal.
     * @param msSinceLastJump time since this fired last, to stop a burst of reports repeating it.
     */
    fun shouldJumpHome(
        mode: DetectionMode,
        enabled: Boolean,
        currentPage: Int,
        defaultHomePage: Int,
        msSinceLastTouch: Long,
        msSinceLastJump: Long,
    ): Boolean {
        if (!enabled) return false
        // Offset tracking reads the real page straight from the launcher and needs no guessing.
        if (mode != DetectionMode.TOUCH) return false
        // Already there: nothing to do, and firing would reset the cooldown for nothing.
        if (currentPage == defaultHomePage) return false
        if (msSinceLastTouch < TOUCH_SETTLE_MS) return false
        if (msSinceLastJump < JUMP_COOLDOWN_MS) return false
        return true
    }
}
