package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The touch fallback's thresholds, for the launchers that never move the wallpaper offset.
 *
 * JVM tests: they prove the gesture arithmetic, not that any given launcher forwards touch
 * events to a wallpaper in the first place.
 */
class SwipeMathTest {

    private val screenWidth = 1080

    @Test
    fun `a long drag leftwards is a swipe left`() {
        val result = SwipeMath.detect(dx = -400f, dy = 20f, durationMs = 300, screenWidth = screenWidth)
        assertEquals(SwipeDirection.LEFT, result)
    }

    @Test
    fun `a long drag rightwards is a swipe right`() {
        val result = SwipeMath.detect(dx = 400f, dy = 20f, durationMs = 300, screenWidth = screenWidth)
        assertEquals(SwipeDirection.RIGHT, result)
    }

    @Test
    fun `a tap is never a swipe`() {
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = 2f, dy = 1f, durationMs = 80, screenWidth = screenWidth),
        )
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = 0f, dy = 0f, durationMs = 40, screenWidth = screenWidth),
        )
    }

    @Test
    fun `a vertical drag is never a swipe`() {
        // Pulling the notification shade or scrolling a folder must not change the page.
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = 60f, dy = 600f, durationMs = 300, screenWidth = screenWidth),
        )
    }

    @Test
    fun `a diagonal drag counts only when it is mostly horizontal`() {
        assertEquals(
            SwipeDirection.LEFT,
            SwipeMath.detect(dx = -500f, dy = 200f, durationMs = 300, screenWidth = screenWidth),
        )
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = -300f, dy = 260f, durationMs = 300, screenWidth = screenWidth),
        )
    }

    @Test
    fun `a short drag below the distance threshold does not count`() {
        // 100px of 1080 is under the 22% bar, and slow enough to miss the velocity bar too.
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = -100f, dy = 5f, durationMs = 800, screenWidth = screenWidth),
        )
    }

    @Test
    fun `a fast flick counts even when it is short`() {
        // 150px in 60ms is 2500 px per second.
        assertEquals(
            SwipeDirection.LEFT,
            SwipeMath.detect(dx = -150f, dy = 5f, durationMs = 60, screenWidth = screenWidth),
        )
    }

    @Test
    fun `nonsense input is rejected rather than crashing`() {
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = -400f, dy = 0f, durationMs = 300, screenWidth = 0),
        )
        assertEquals(
            SwipeDirection.NONE,
            SwipeMath.detect(dx = Float.NaN, dy = 0f, durationMs = 300, screenWidth = screenWidth),
        )
    }

    @Test
    fun `swiping steps one page at a time and stops at the ends`() {
        assertEquals(1, SwipeMath.nextPage(0, SwipeDirection.LEFT, pageCount = 5))
        assertEquals(2, SwipeMath.nextPage(1, SwipeDirection.LEFT, pageCount = 5))
        assertEquals(4, SwipeMath.nextPage(3, SwipeDirection.LEFT, pageCount = 5))
        // Already on the last page.
        assertEquals(4, SwipeMath.nextPage(4, SwipeDirection.LEFT, pageCount = 5))

        assertEquals(3, SwipeMath.nextPage(4, SwipeDirection.RIGHT, pageCount = 5))
        assertEquals(0, SwipeMath.nextPage(1, SwipeDirection.RIGHT, pageCount = 5))
        // Already on the first page.
        assertEquals(0, SwipeMath.nextPage(0, SwipeDirection.RIGHT, pageCount = 5))

        assertEquals(2, SwipeMath.nextPage(2, SwipeDirection.NONE, pageCount = 5))
        assertEquals(0, SwipeMath.nextPage(0, SwipeDirection.LEFT, pageCount = 1))
    }

    @Test
    fun `five pages can be walked forwards and back by swiping`() {
        var page = 0
        repeat(4) { page = SwipeMath.nextPage(page, SwipeDirection.LEFT, 5) }
        assertEquals(4, page)
        repeat(4) { page = SwipeMath.nextPage(page, SwipeDirection.RIGHT, 5) }
        assertEquals(0, page)
    }

    @Test
    fun `touch takes over only when the offset never moves`() {
        // What this phone reports: a fixed 0.5 forever.
        assertEquals(
            DetectionMode.TOUCH,
            SwipeMath.detectionMode(TouchCompatibility.AUTO, 0.5f, 0.5f),
        )
        // A launcher that sweeps the range properly keeps the offset path.
        assertEquals(
            DetectionMode.OFFSET,
            SwipeMath.detectionMode(TouchCompatibility.AUTO, 0.0f, 1.0f),
        )
        // Nothing reported yet: answer swipes rather than sit on one picture.
        assertEquals(
            DetectionMode.TOUCH,
            SwipeMath.detectionMode(TouchCompatibility.AUTO, -1f, -1f),
        )
    }

    @Test
    fun `the manual setting overrides what the offsets are doing`() {
        assertEquals(
            DetectionMode.TOUCH,
            SwipeMath.detectionMode(TouchCompatibility.ON, 0.0f, 1.0f),
        )
        assertEquals(
            DetectionMode.OFFSET,
            SwipeMath.detectionMode(TouchCompatibility.OFF, 0.5f, 0.5f),
        )
    }
}
