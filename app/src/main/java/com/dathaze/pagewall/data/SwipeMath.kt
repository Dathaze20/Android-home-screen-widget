package com.dathaze.pagewall.data

import kotlin.math.abs

/** Which way a finger travelled across the wallpaper, once it counts as a page swipe. */
enum class SwipeDirection { NONE, LEFT, RIGHT }

/** How the engine decides which home screen it is on. */
enum class DetectionMode { OFFSET, TOUCH }

/** Whether the touch fallback is off, forced on, or left to decide for itself. */
enum class TouchCompatibility { AUTO, ON, OFF }

/**
 * Turns a finger gesture into a page step, for launchers that never move the wallpaper offset.
 *
 * One UI reports a fixed offset of 0.5 with no step, so [PageMath] has nothing to work from. A
 * live wallpaper can however ask for raw touch events, and a horizontal flick across the home
 * screen is the same gesture that changed the page — so the page can be stepped directly.
 *
 * Pure arithmetic, no Android imports, so the thresholds can be unit tested.
 */
object SwipeMath {

    /** A swipe must cross this fraction of the screen to count on distance alone. */
    const val DISTANCE_FRACTION = 0.22f

    /** A quick flick counts below that distance, if it is at least this fast. */
    const val MIN_VELOCITY_PX_PER_SECOND = 650f

    /** A fast flick still has to travel this far, so a tap with jitter never counts. */
    const val MIN_FLICK_FRACTION = 0.06f

    /** Horizontal travel must beat vertical travel by this much, so scrolls are not page swipes. */
    const val HORIZONTAL_RATIO = 1.5f

    /** One gesture must not be able to step two pages. */
    const val COOLDOWN_MS = 350L

    /** Below this, the observed offset range counts as "the launcher never moves it". */
    const val FLAT_OFFSET_RANGE = 0.01f

    /**
     * @param dx horizontal travel in pixels, negative when the finger moved leftwards
     * @param dy vertical travel in pixels
     * @param durationMs how long the gesture took
     * @param screenWidth width of the wallpaper surface in pixels
     */
    fun detect(dx: Float, dy: Float, durationMs: Long, screenWidth: Int): SwipeDirection {
        if (screenWidth <= 0) return SwipeDirection.NONE
        if (!dx.isFinite() || !dy.isFinite()) return SwipeDirection.NONE

        val horizontal = abs(dx)
        val vertical = abs(dy)

        // A mostly vertical drag is a scroll or a notification pull, never a page change.
        if (horizontal < vertical * HORIZONTAL_RATIO) return SwipeDirection.NONE

        val farEnough = horizontal >= screenWidth * DISTANCE_FRACTION
        val fastEnough = durationMs > 0 &&
            horizontal >= screenWidth * MIN_FLICK_FRACTION &&
            horizontal / (durationMs / 1000f) >= MIN_VELOCITY_PX_PER_SECOND

        if (!farEnough && !fastEnough) return SwipeDirection.NONE

        // Dragging leftwards pulls the next page in from the right.
        return if (dx < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
    }

    /** Applies a swipe to the current page, clamped so the ends do not wrap. */
    fun nextPage(currentPage: Int, direction: SwipeDirection, pageCount: Int): Int {
        if (pageCount <= 1) return 0
        val stepped = when (direction) {
            SwipeDirection.LEFT -> currentPage + 1
            SwipeDirection.RIGHT -> currentPage - 1
            SwipeDirection.NONE -> currentPage
        }
        return stepped.coerceIn(0, pageCount - 1)
    }

    /**
     * Which detection method should be running.
     *
     * Offsets are always preferred; touch is the fallback for a launcher whose offset never moves.
     * Before any offset has been seen the range is empty, which counts as flat — better to answer
     * swipes straight away than to sit on one picture waiting for a report that never comes.
     */
    fun detectionMode(
        setting: TouchCompatibility,
        observedMinOffset: Float,
        observedMaxOffset: Float,
    ): DetectionMode {
        val offsetsMove = observedMinOffset >= 0f &&
            observedMaxOffset >= 0f &&
            observedMaxOffset - observedMinOffset > FLAT_OFFSET_RANGE

        return when (setting) {
            TouchCompatibility.ON -> DetectionMode.TOUCH
            TouchCompatibility.OFF -> DetectionMode.OFFSET
            TouchCompatibility.AUTO -> if (offsetsMove) DetectionMode.OFFSET else DetectionMode.TOUCH
        }
    }
}
