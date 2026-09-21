package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The three moments touch tracking cannot observe a page change for itself. */
class SyncPolicyTest {

    private val pages = 5
    private val home = 0

    @Test
    fun `returning from an app snaps to the configured home screen`() {
        val page = SyncPolicy.pageOnVisible(
            mode = DetectionMode.TOUCH,
            syncOnReturnHome = true,
            screenWasOff = false,
            defaultHomePage = home,
            currentPage = 3,
            pageCount = pages,
        )
        assertEquals(home, page)
    }

    @Test
    fun `a home screen other than the first is honoured`() {
        val page = SyncPolicy.pageOnVisible(
            mode = DetectionMode.TOUCH,
            syncOnReturnHome = true,
            screenWasOff = false,
            defaultHomePage = 2,
            currentPage = 4,
            pageCount = pages,
        )
        assertEquals(2, page)
    }

    @Test
    fun `unlocking keeps the page you were on`() {
        // On screen 4, lock, unlock: the launcher never moved, so neither should the wallpaper.
        val page = SyncPolicy.pageOnVisible(
            mode = DetectionMode.TOUCH,
            syncOnReturnHome = true,
            screenWasOff = true,
            defaultHomePage = home,
            currentPage = 3,
            pageCount = pages,
        )
        assertEquals(3, page)
    }

    @Test
    fun `the sync setting switched off keeps the page`() {
        val page = SyncPolicy.pageOnVisible(
            mode = DetectionMode.TOUCH,
            syncOnReturnHome = false,
            screenWasOff = false,
            defaultHomePage = home,
            currentPage = 3,
            pageCount = pages,
        )
        assertEquals(3, page)
    }

    @Test
    fun `offset tracking never needs syncing`() {
        val page = SyncPolicy.pageOnVisible(
            mode = DetectionMode.OFFSET,
            syncOnReturnHome = true,
            screenWasOff = false,
            defaultHomePage = home,
            currentPage = 3,
            pageCount = pages,
        )
        assertEquals(3, page)
    }

    @Test
    fun `a restarted engine starts on the home screen under touch tracking`() {
        val page = SyncPolicy.pageOnEngineStart(
            mode = DetectionMode.TOUCH,
            defaultHomePage = 1,
            storedPage = 4,
            pageCount = pages,
        )
        assertEquals(1, page)
    }

    @Test
    fun `a restarted engine keeps the stored page under offset tracking`() {
        val page = SyncPolicy.pageOnEngineStart(
            mode = DetectionMode.OFFSET,
            defaultHomePage = 0,
            storedPage = 3,
            pageCount = pages,
        )
        assertEquals(3, page)
    }

    @Test
    fun `changing the page count under touch tracking keeps the page and clamps it`() {
        // The regression: 0.5 is a frozen constant here, and deriving from it would jump to the
        // middle page every time the count changed.
        assertEquals(
            2,
            SyncPolicy.pageOnPageCountChange(
                mode = DetectionMode.TOUCH,
                currentPage = 4,
                pageCount = 3,
                lastOffset = 0.5f,
                calibrationMin = -1f,
                calibrationMax = -1f,
            ),
        )
        // Growing the count leaves the page alone.
        assertEquals(
            4,
            SyncPolicy.pageOnPageCountChange(
                mode = DetectionMode.TOUCH,
                currentPage = 4,
                pageCount = 8,
                lastOffset = 0.5f,
                calibrationMin = -1f,
                calibrationMax = -1f,
            ),
        )
    }

    @Test
    fun `changing the page count under offset tracking recomputes from the offset`() {
        assertEquals(
            4,
            SyncPolicy.pageOnPageCountChange(
                mode = DetectionMode.OFFSET,
                currentPage = 0,
                pageCount = 5,
                lastOffset = 1.0f,
                calibrationMin = -1f,
                calibrationMax = -1f,
            ),
        )
    }

    @Test
    fun `offset tracking with no offset yet keeps the page`() {
        assertEquals(
            3,
            SyncPolicy.pageOnPageCountChange(
                mode = DetectionMode.OFFSET,
                currentPage = 3,
                pageCount = 5,
                lastOffset = -1f,
                calibrationMin = -1f,
                calibrationMax = -1f,
            ),
        )
    }

    @Test
    fun `every path clamps into the configured range`() {
        assertEquals(
            0,
            SyncPolicy.pageOnVisible(DetectionMode.TOUCH, true, false, 9, 0, pageCount = 1),
        )
        assertEquals(
            2,
            SyncPolicy.pageOnEngineStart(DetectionMode.OFFSET, 0, storedPage = 9, pageCount = 3),
        )
    }
}
