package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which files stop being needed. Getting this wrong deletes a photograph that something is
 * still pointing at, which is why it is a whole-list question rather than a per-slot one.
 */
class PageWriteTest {

    private val before = listOf(
        PageConfig(0, mediaFile = "a.jpg"),
        PageConfig(1, mediaFile = "b.mp4", mediaKind = MediaKind.VIDEO, posterFile = "b-poster.jpg"),
        PageConfig(2, mediaFile = "c.jpg", audioFile = "song.mp3", audioTitle = "A song"),
    )

    @Test
    fun `replacing a page's photo orphans the one it used to hold`() {
        val after = before.map { if (it.index == 0) it.copy(mediaFile = "new.jpg") else it }
        assertEquals(setOf("a.jpg"), PageWrite.orphans(before, after))
    }

    @Test
    fun `a file another page still points at is never an orphan`() {
        // Reachable from a restored backup, which may carry one picture for two pages. Deleting
        // it on behalf of the page that changed would blank the page that did not.
        val shared = listOf(
            PageConfig(0, mediaFile = "shared.jpg"),
            PageConfig(1, mediaFile = "shared.jpg"),
        )
        val after = listOf(
            PageConfig(0, mediaFile = "new.jpg"),
            PageConfig(1, mediaFile = "shared.jpg"),
        )
        assertEquals(emptySet<String>(), PageWrite.orphans(shared, after))
    }

    @Test
    fun `clearing a page orphans everything it held and nothing else`() {
        val after = before.filterNot { it.index == 2 }
        assertEquals(setOf("c.jpg", "song.mp3"), PageWrite.orphans(before, after))
    }

    @Test
    fun `a video's poster frame goes with it`() {
        val after = before.filterNot { it.index == 1 }
        assertEquals(setOf("b.mp4", "b-poster.jpg"), PageWrite.orphans(before, after))
    }

    @Test
    fun `changing nothing orphans nothing`() {
        assertEquals(emptySet<String>(), PageWrite.orphans(before, before))
    }

    @Test
    fun `adding a page orphans nothing`() {
        val after = before + PageConfig(3, mediaFile = "d.jpg")
        assertEquals(emptySet<String>(), PageWrite.orphans(before, after))
    }

    @Test
    fun `a restore that only adds pages deletes nothing at all`() {
        // The shape of a restore filling empty pages: the files already here are all still
        // named afterwards, so there is nothing to delete and nothing that can be lost.
        val after = before + listOf(
            PageConfig(5, mediaFile = "restored-5.jpg"),
            PageConfig(6, mediaFile = "restored-6.jpg"),
        )
        assertTrue(PageWrite.orphans(before, after).isEmpty())
    }

    @Test
    fun `every file is accounted for`() {
        assertEquals(
            setOf("a.jpg", "b.mp4", "b-poster.jpg", "c.jpg", "song.mp3"),
            PageWrite.filesUsedBy(before),
        )
        assertEquals(emptySet<String>(), PageWrite.filesUsedBy(listOf(PageConfig(0))))
    }
}
