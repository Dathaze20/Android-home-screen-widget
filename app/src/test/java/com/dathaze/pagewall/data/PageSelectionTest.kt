package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two questions that look like one: what the home screen lays out, and what a backup has to
 * carry. The app keeps a page's photo when the page count is turned down, so the second answer
 * is larger than the first, and a backup that asked the first question would leave photos behind.
 */
class PageSelectionTest {

    private val saved = listOf(
        PageConfig(0, mediaFile = "a.jpg"),
        PageConfig(2, mediaFile = "c.jpg"),
        PageConfig(7, mediaFile = "hidden.jpg"),
        PageConfig(9, audioFile = "hidden-song.mp3", audioTitle = "Hidden"),
    )

    @Test
    fun `the visible pages are one per page the launcher has, gaps included`() {
        val visible = PageSelection.visible(saved, pageCount = 3)

        assertEquals(listOf(0, 1, 2), visible.map { it.index })
        assertEquals("a.jpg", visible[0].mediaFile)
        assertEquals(null, visible[1].mediaFile)
        assertEquals("c.jpg", visible[2].mediaFile)
    }

    @Test
    fun `a page saved beyond the current count is still assigned`() {
        // The regression: a backup built from the visible pages stopped at page 2 and silently
        // left pages 7 and 9 out, even though their files were still on the phone.
        val assigned = PageSelection.assigned(saved)

        assertEquals(listOf(0, 2, 7, 9), assigned.map { it.index })
        assertEquals("hidden.jpg", assigned.first { it.index == 7 }.mediaFile)
        assertEquals("hidden-song.mp3", assigned.first { it.index == 9 }.audioFile)
    }

    @Test
    fun `a page holding only a track counts as assigned`() {
        assertEquals(
            listOf(5),
            PageSelection.assigned(listOf(PageConfig(5, audioFile = "song.mp3"))).map { it.index },
        )
    }

    @Test
    fun `pages with nothing on them are not assigned`() {
        assertEquals(emptyList<Int>(), PageSelection.assigned(listOf(PageConfig(0), PageConfig(1))).map { it.index })
    }

    @Test
    fun `assigned pages come back in page order whatever order they were stored in`() {
        val jumbled = listOf(
            PageConfig(9, mediaFile = "i.jpg"),
            PageConfig(1, mediaFile = "b.jpg"),
            PageConfig(4, mediaFile = "e.jpg"),
        )
        assertEquals(listOf(1, 4, 9), PageSelection.assigned(jumbled).map { it.index })
    }

    @Test
    fun `a page named twice is reported once`() {
        // A backup manifest listing the same page twice is rejected as self-contradictory when
        // it is read back, so the export must not be able to produce one.
        val duplicated = listOf(
            PageConfig(1, mediaFile = "first.jpg"),
            PageConfig(1, mediaFile = "second.jpg"),
        )
        assertEquals(listOf(1), PageSelection.assigned(duplicated).map { it.index })
    }

    @Test
    fun `turning the count down and back up again loses nothing`() {
        // The behaviour the retention exists for, stated as a test so a future tidy-up of
        // PageStore cannot quietly drop it.
        val narrowed = PageSelection.visible(saved, pageCount = 1)
        assertEquals(listOf(0), narrowed.map { it.index })

        val widened = PageSelection.visible(saved, pageCount = 8)
        assertEquals("hidden.jpg", widened[7].mediaFile)
    }
}
