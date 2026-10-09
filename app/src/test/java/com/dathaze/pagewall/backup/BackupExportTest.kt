package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An export that cannot carry everything must fail, loudly, before anything is written.
 *
 * The failure being guarded against does not look like one: a backup missing a photograph still
 * copies to Downloads, still reports a size, still sits in Drive looking exactly like a backup —
 * and only turns out to be incomplete on the day somebody needs it.
 */
class BackupExportTest {

    private val pages = listOf(
        BackupPage(0, mediaFile = "page_0_1.jpg"),
        BackupPage(1, mediaFile = "page_1_2.mp4", mediaKind = "VIDEO", posterFile = "poster_1_2.jpg"),
        BackupPage(2, mediaFile = "page_2_3.jpg", audioFile = "audio_2_3.mp3"),
    )

    private val allThere = setOf(
        "page_0_1.jpg", "page_1_2.mp4", "poster_1_2.jpg", "page_2_3.jpg", "audio_2_3.mp3",
    )

    private fun missing(vararg gone: String) =
        BackupExport.missingFiles(pages) { it in allThere - gone.toSet() }

    @Test
    fun `nothing missing when every file is on disk`() {
        assertTrue(BackupExport.missingFiles(pages) { it in allThere }.isEmpty())
    }

    @Test
    fun `a missing photo is reported`() {
        assertEquals(listOf("page_0_1.jpg"), missing("page_0_1.jpg"))
    }

    @Test
    fun `a missing poster frame or track counts just as much`() {
        assertEquals(listOf("poster_1_2.jpg"), missing("poster_1_2.jpg"))
        assertEquals(listOf("audio_2_3.mp3"), missing("audio_2_3.mp3"))
    }

    @Test
    fun `a name the archive could not carry counts as missing`() {
        // It cannot go into the zip, so a backup claiming it would be incomplete in exactly the
        // same way as one whose file had been deleted.
        val unsafe = listOf(BackupPage(0, mediaFile = "../escape.jpg"))
        assertEquals(listOf("../escape.jpg"), BackupExport.missingFiles(unsafe) { true })
    }

    @Test
    fun `the reason names the page, because a file name stamped with a millisecond means nothing`() {
        val reason = BackupExport.incompleteReason(pages, listOf("page_0_1.jpg"))
        assertTrue(reason, reason.contains("Page 1"))
        assertTrue(reason, reason.contains("Nothing was saved"))
    }

    @Test
    fun `several pages are listed, one-based, in order`() {
        val reason = BackupExport.incompleteReason(pages, listOf("audio_2_3.mp3", "poster_1_2.jpg"))
        assertTrue(reason, reason.contains("Pages 2, 3"))
    }

    @Test
    fun `a page is named once however many of its files are gone`() {
        val reason = BackupExport.incompleteReason(pages, listOf("page_1_2.mp4", "poster_1_2.jpg"))
        assertTrue(reason, reason.contains("Page 2"))
        assertEquals(1, Regex("Page 2").findAll(reason).count())
    }

    @Test
    fun `pages with nothing on them cannot make an export incomplete`() {
        val empties = listOf(BackupPage(0), BackupPage(1))
        assertTrue(BackupExport.missingFiles(empties) { false }.isEmpty())
    }
}
