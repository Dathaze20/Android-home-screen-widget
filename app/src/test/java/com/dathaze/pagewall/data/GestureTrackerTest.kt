package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** One press, at most one page step — and no cooldown punishing a genuine fast second swipe. */
class GestureTrackerTest {

    private val width = 1080

    private fun GestureTracker.swipe(
        from: Float,
        to: Float,
        startMs: Long,
        endMs: Long,
        y: Float = 500f,
    ): SwipeDirection {
        onDown(from, y, startMs)
        return onUp(to, y, endMs, width)
    }

    @Test
    fun `two separate swipes in quick succession both count`() {
        // The regression this class exists for. The old 350 ms cooldown swallowed the second
        // swipe, leaving the wallpaper a page behind the launcher.
        val tracker = GestureTracker()
        assertEquals(SwipeDirection.LEFT, tracker.swipe(900f, 200f, 1000, 1150))
        assertEquals(SwipeDirection.LEFT, tracker.swipe(900f, 200f, 1200, 1350))
        assertEquals(SwipeDirection.LEFT, tracker.swipe(900f, 200f, 1400, 1550))
    }

    @Test
    fun `five pages can be swiped forwards and back quickly`() {
        val tracker = GestureTracker()
        var page = 0
        var time = 0L
        repeat(4) {
            val direction = tracker.swipe(900f, 200f, time, time + 120)
            page = SwipeMath.nextPage(page, direction, 5)
            time += 140
        }
        assertEquals(4, page)
        repeat(4) {
            val direction = tracker.swipe(200f, 900f, time, time + 120)
            page = SwipeMath.nextPage(page, direction, 5)
            time += 140
        }
        assertEquals(0, page)
    }

    @Test
    fun `one gesture cannot produce two steps`() {
        val tracker = GestureTracker()
        tracker.onDown(900f, 500f, 1000)
        assertEquals(SwipeDirection.LEFT, tracker.onUp(200f, 500f, 1150, width))
        // A repeated or duplicated release must be ignored.
        assertEquals(SwipeDirection.NONE, tracker.onUp(200f, 500f, 1150, width))
        assertFalse(tracker.isArmed)
    }

    @Test
    fun `a release with no press does nothing`() {
        val tracker = GestureTracker()
        assertEquals(SwipeDirection.NONE, tracker.onUp(200f, 500f, 1150, width))
    }

    @Test
    fun `a cancelled gesture cannot be completed later`() {
        val tracker = GestureTracker()
        tracker.onDown(900f, 500f, 1000)
        tracker.reset()
        assertEquals(SwipeDirection.NONE, tracker.onUp(200f, 500f, 1150, width))
    }

    @Test
    fun `a gesture abandoned while hidden cannot change the page on return`() {
        // Press, wallpaper hidden (reset), then a release arriving in a new context.
        val tracker = GestureTracker()
        tracker.onDown(900f, 500f, 1000)
        tracker.reset()
        assertEquals(SwipeDirection.NONE, tracker.onUp(100f, 500f, 9000, width))
        // The next genuine gesture still works.
        assertEquals(SwipeDirection.LEFT, tracker.swipe(900f, 200f, 9100, 9250))
    }

    @Test
    fun `a second finger invalidates the gesture`() {
        val tracker = GestureTracker()
        tracker.onDown(900f, 500f, 1000)
        tracker.onExtraPointer()
        assertEquals(SwipeDirection.NONE, tracker.onUp(200f, 500f, 1150, width))
        // And the next single-finger swipe is unaffected.
        assertEquals(SwipeDirection.LEFT, tracker.swipe(900f, 200f, 1200, 1350))
    }

    @Test
    fun `a tap does not change the page`() {
        val tracker = GestureTracker()
        assertEquals(SwipeDirection.NONE, tracker.swipe(500f, 503f, 1000, 1080))
    }

    @Test
    fun `a vertical drag does not change the page`() {
        val tracker = GestureTracker()
        tracker.onDown(500f, 100f, 1000)
        assertEquals(SwipeDirection.NONE, tracker.onUp(540f, 900f, 1300, width))
    }

    @Test
    fun `a mostly horizontal diagonal still counts`() {
        val tracker = GestureTracker()
        tracker.onDown(900f, 400f, 1000)
        assertEquals(SwipeDirection.LEFT, tracker.onUp(300f, 600f, 1250, width))
    }
}
