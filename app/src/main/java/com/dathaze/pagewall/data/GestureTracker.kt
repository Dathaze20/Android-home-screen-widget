package com.dathaze.pagewall.data

/**
 * Turns a stream of touch actions into at most one page step per gesture.
 *
 * Replaces the time-based cooldown that used to guard against double-counting. A cooldown cannot
 * tell a duplicate apart from a genuine second swipe, so swiping quickly through pages left the
 * wallpaper a page behind the launcher. Gesture state can: one press starts one gesture, one
 * release may produce one step, and anything else clears it.
 *
 * Pure state, no Android imports, so every rule below is unit tested.
 */
class GestureTracker {

    private var downX = 0f
    private var downY = 0f
    private var downAtMs = 0L

    /** False once the gesture has been spent or invalidated; only a fresh press re-arms it. */
    private var armed = false

    /** A gesture that grew a second finger is a pinch or a launcher gesture, never a page swipe. */
    private var multiTouch = false

    val isArmed: Boolean get() = armed

    fun onDown(x: Float, y: Float, timeMs: Long) {
        downX = x
        downY = y
        downAtMs = timeMs
        armed = true
        multiTouch = false
    }

    /** A second finger landing invalidates the gesture without waiting for the release. */
    fun onExtraPointer() {
        multiTouch = true
    }

    /**
     * Consumes the gesture and reports the swipe it amounted to.
     *
     * Always disarms, so the same release can never be counted twice, while a new press
     * immediately afterwards is free to produce the next step however quickly it arrives.
     */
    fun onUp(x: Float, y: Float, timeMs: Long, screenWidth: Int): SwipeDirection {
        if (!armed) return SwipeDirection.NONE
        armed = false
        if (multiTouch) return SwipeDirection.NONE

        return SwipeMath.detect(
            dx = x - downX,
            dy = y - downY,
            durationMs = timeMs - downAtMs,
            screenWidth = screenWidth,
        )
    }

    /**
     * Drops a gesture in progress. Called on ACTION_CANCEL and whenever the wallpaper is hidden,
     * the surface goes away or the screen turns off — a half-finished press must never be
     * completed against a release that arrives in a different context.
     */
    fun reset() {
        armed = false
        multiTouch = false
        downAtMs = 0L
    }
}
