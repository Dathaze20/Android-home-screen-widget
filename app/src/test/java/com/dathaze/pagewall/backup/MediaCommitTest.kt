package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Real files in a real folder, because what is being tested is what survives a failure.
 *
 * The regression behind every test here: a restore used to copy straight onto the live file
 * names. A copy that died halfway — a full card, a short read — had already truncated a photo
 * the wallpaper was showing, and there was nothing left to put back.
 */
class MediaCommitTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var from: File
    private lateinit var into: File

    private fun setUpFolders() {
        from = temp.newFolder("staging")
        into = temp.newFolder("pages")
    }

    private fun stage(name: String, text: String) = File(from, name).apply { writeText(text) }

    private fun live(name: String, text: String) = File(into, name).apply { writeText(text) }

    private fun liveNames() = (into.listFiles() ?: emptyArray()).map { it.name }.sorted()

    @Test
    fun `every file lands, and nothing else is left in the folder`() {
        setUpFolders()
        stage("a.jpg", "first picture")
        stage("b.mp4", "a video")
        stage("song.mp3", "a track")

        assertTrue(MediaCommit.commit(from, into, setOf("a.jpg", "b.mp4", "song.mp3")))

        assertEquals(listOf("a.jpg", "b.mp4", "song.mp3"), liveNames())
        assertEquals("first picture", File(into, "a.jpg").readText())
        assertEquals("a track", File(into, "song.mp3").readText())
    }

    @Test
    fun `a file that is not in the staging folder changes nothing at all`() {
        setUpFolders()
        stage("a.jpg", "new picture")
        live("a.jpg", "the picture I already had")
        live("keep.jpg", "untouched")

        // "missing.jpg" is what a short or damaged archive looks like by the time it gets here.
        assertFalse(MediaCommit.commit(from, into, listOf("a.jpg", "missing.jpg")))

        // The live file is whole, not truncated, and not replaced.
        assertEquals("the picture I already had", File(into, "a.jpg").readText())
        assertEquals("untouched", File(into, "keep.jpg").readText())
        assertEquals(listOf("a.jpg", "keep.jpg"), liveNames())
    }

    @Test
    fun `a failure leaves no working files behind for the engine to find`() {
        setUpFolders()
        stage("a.jpg", "new picture")
        live("a.jpg", "old picture")

        assertFalse(MediaCommit.commit(from, into, listOf("a.jpg", "missing.jpg")))

        assertTrue(
            "working files were left in the live folder: ${liveNames()}",
            liveNames().none {
                it.endsWith(MediaCommit.INCOMING_SUFFIX) || it.endsWith(MediaCommit.DISPLACED_SUFFIX)
            },
        )
    }

    @Test
    fun `replacing a file that is already there succeeds and keeps no copy of the old one`() {
        setUpFolders()
        stage("a.jpg", "the replacement")
        live("a.jpg", "the original")

        assertTrue(MediaCommit.commit(from, into, setOf("a.jpg")))

        assertEquals("the replacement", File(into, "a.jpg").readText())
        assertEquals(listOf("a.jpg"), liveNames())
    }

    @Test
    fun `committing nothing is allowed and does nothing`() {
        setUpFolders()
        live("a.jpg", "mine")

        assertTrue(MediaCommit.commit(from, into, emptySet()))
        assertEquals(listOf("a.jpg"), liveNames())
    }

    @Test
    fun `a name that could escape the folder is refused outright`() {
        setUpFolders()
        live("a.jpg", "mine")

        listOf("../escape.jpg", "sub/dir.jpg", "..", ".hidden", "").forEach { name ->
            assertFalse("accepted $name", MediaCommit.commit(from, into, listOf(name)))
        }
        assertEquals(listOf("a.jpg"), liveNames())
    }

    @Test
    fun `a name that looks like one of the working files is refused`() {
        setUpFolders()
        stage("a.jpg${MediaCommit.INCOMING_SUFFIX}", "confusing")

        assertFalse(MediaCommit.commit(from, into, listOf("a.jpg${MediaCommit.INCOMING_SUFFIX}")))
        assertFalse(MediaCommit.isCommittable("a.jpg${MediaCommit.DISPLACED_SUFFIX}"))
    }

    @Test
    fun `a half-finished swap from a killed run is put back, not thrown away`() {
        setUpFolders()
        // What being killed between the two renames leaves: the old file moved aside, its name
        // free. The displaced copy is the only copy there is.
        File(into, "a.jpg${MediaCommit.DISPLACED_SUFFIX}").writeText("the only copy")
        stage("b.jpg", "something else")

        assertTrue(MediaCommit.commit(from, into, setOf("b.jpg")))

        assertEquals("the only copy", File(into, "a.jpg").readText())
        assertEquals(listOf("a.jpg", "b.jpg"), liveNames())
    }

    @Test
    fun `a displaced copy is dropped once the name is occupied again`() {
        setUpFolders()
        live("a.jpg", "the file that did land")
        File(into, "a.jpg${MediaCommit.DISPLACED_SUFFIX}").writeText("what it replaced")

        MediaCommit.recoverLeftovers(into)

        assertEquals("the file that did land", File(into, "a.jpg").readText())
        assertEquals(listOf("a.jpg"), liveNames())
    }

    @Test
    fun `an abandoned copy in progress is cleaned up`() {
        setUpFolders()
        File(into, "a.jpg${MediaCommit.INCOMING_SUFFIX}").writeText("half a photo")

        MediaCommit.recoverLeftovers(into)

        assertEquals(emptyList<String>(), liveNames())
    }

    @Test
    fun `the folder is created when it is not there yet`() {
        setUpFolders()
        val fresh = File(temp.root, "not-made-yet")
        stage("a.jpg", "first picture")

        assertTrue(MediaCommit.commit(from, fresh, setOf("a.jpg")))
        assertEquals("first picture", File(fresh, "a.jpg").readText())
    }

    @Test
    fun `bytes arrive intact for a file larger than the copy buffer`() {
        setUpFolders()
        val big = ByteArray(300 * 1024) { (it % 251).toByte() }
        File(from, "big.jpg").writeBytes(big)

        assertTrue(MediaCommit.commit(from, into, setOf("big.jpg")))
        assertTrue(big.contentEquals(File(into, "big.jpg").readBytes()))
    }
}
