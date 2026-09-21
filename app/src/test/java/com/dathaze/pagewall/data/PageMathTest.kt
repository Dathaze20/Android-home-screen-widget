package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The page arithmetic, checked against the cases that actually broke on a phone.
 *
 * These run on the JVM with no device, so they verify the maths and nothing else: they cannot
 * say whether a given launcher reports offsets in the first place.
 */
class PageMathTest {

    @Test
    fun `five pages map across the full offset range`() {
        assertEquals(0, PageMath.pageFor(0.00f, pageCount = 5))
        assertEquals(1, PageMath.pageFor(0.25f, pageCount = 5))
        assertEquals(2, PageMath.pageFor(0.50f, pageCount = 5))
        assertEquals(3, PageMath.pageFor(0.75f, pageCount = 5))
        assertEquals(4, PageMath.pageFor(1.00f, pageCount = 5))
    }

    @Test
    fun `offsets between pages round to the nearest page`() {
        assertEquals(0, PageMath.pageFor(0.10f, pageCount = 5))
        assertEquals(1, PageMath.pageFor(0.20f, pageCount = 5))
        assertEquals(1, PageMath.pageFor(0.30f, pageCount = 5))
        assertEquals(3, PageMath.pageFor(0.70f, pageCount = 5))
        assertEquals(4, PageMath.pageFor(0.90f, pageCount = 5))
    }

    @Test
    fun `page never escapes the configured range`() {
        assertEquals(0, PageMath.pageFor(-5f, pageCount = 5))
        assertEquals(4, PageMath.pageFor(5f, pageCount = 5))
        assertEquals(0, PageMath.pageFor(Float.NaN, pageCount = 5))
        assertEquals(0, PageMath.pageFor(0.5f, pageCount = 1))
        assertEquals(0, PageMath.pageFor(0.5f, pageCount = 0))
    }

    @Test
    fun `other page counts divide the range evenly`() {
        assertEquals(0, PageMath.pageFor(0.0f, pageCount = 3))
        assertEquals(1, PageMath.pageFor(0.5f, pageCount = 3))
        assertEquals(2, PageMath.pageFor(1.0f, pageCount = 3))

        assertEquals(0, PageMath.pageFor(0.0f, pageCount = 2))
        assertEquals(1, PageMath.pageFor(1.0f, pageCount = 2))
    }

    @Test
    fun `a missing step does not affect the page at all`() {
        // The regression this whole class exists for: with no usable step the page used to
        // freeze, so every home screen showed the same picture.
        val pages = (0..4).map { index ->
            PageMath.pageFor(index * 0.25f, pageCount = 5)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), pages)
    }

    @Test
    fun `calibration rescales a launcher that only sweeps part of the range`() {
        // A launcher reporting 0.30 at the left edge and 0.70 at the right still has to drive
        // all five pages.
        assertEquals(0, PageMath.pageFor(0.30f, 5, calibrationMin = 0.30f, calibrationMax = 0.70f))
        assertEquals(1, PageMath.pageFor(0.40f, 5, calibrationMin = 0.30f, calibrationMax = 0.70f))
        assertEquals(2, PageMath.pageFor(0.50f, 5, calibrationMin = 0.30f, calibrationMax = 0.70f))
        assertEquals(3, PageMath.pageFor(0.60f, 5, calibrationMin = 0.30f, calibrationMax = 0.70f))
        assertEquals(4, PageMath.pageFor(0.70f, 5, calibrationMin = 0.30f, calibrationMax = 0.70f))
    }

    @Test
    fun `calibration captured backwards still works`() {
        assertEquals(0, PageMath.pageFor(0.30f, 5, calibrationMin = 0.70f, calibrationMax = 0.30f))
        assertEquals(4, PageMath.pageFor(0.70f, 5, calibrationMin = 0.70f, calibrationMax = 0.30f))
    }

    @Test
    fun `a calibration span too small to be real is ignored`() {
        // Tapping both edges on the same page must not wedge every page onto one picture.
        assertEquals(2, PageMath.pageFor(0.50f, 5, calibrationMin = 0.50f, calibrationMax = 0.50f))
        assertEquals(4, PageMath.pageFor(1.00f, 5, calibrationMin = 0.50f, calibrationMax = 0.50f))
    }

    @Test
    fun `launcher page count is read from a usable step only`() {
        assertEquals(5, PageMath.launcherPageCount(0.25f, maxPages = 12))
        assertEquals(3, PageMath.launcherPageCount(0.5f, maxPages = 12))
        assertEquals(2, PageMath.launcherPageCount(1.0f, maxPages = 12))

        // What this phone actually reports.
        assertNull(PageMath.launcherPageCount(0f, maxPages = 12))
        assertNull(PageMath.launcherPageCount(-1f, maxPages = 12))
        assertNull(PageMath.launcherPageCount(Float.NaN, maxPages = 12))
        // 1/0.001 + 1 is far past any real home screen.
        assertNull(PageMath.launcherPageCount(0.001f, maxPages = 12))
    }
}
