package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Real files in a real folder, because what is being tested is what survives a failure.
 *
 * The property every test here defends: **a file already in the media folder is never
 * overwritten, moved or deleted.** That is what makes an interruption harmless, because the
 * assignments on disk keep naming files that are still there, whichever moment the process died
 * in. Each stage of a commit is reconstructed by hand below and the same question asked of it.
 */
class MediaCommitTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var from: File
    private lateinit var into: File

    private fun folders() {
        from = temp.newFolder("staging")
        into = temp.newFolder("pages")
    }

    private fun stage(name: String, text: String) = File(from, name).apply { writeText(text) }

    private fun live(name: String, text: String) = File(into, name).apply { writeText(text) }

    private fun names() = (into.listFiles() ?: emptyArray()).map { it.name }.sorted()

    private fun contents() = (into.listFiles() ?: emptyArray())
        .associate { it.name to it.readText() }

    @Test
    fun `every file lands under its own name, and nothing else is left behind`() {
        folders()
        stage("a.jpg", "first picture")
        stage("b.mp4", "a video")
        stage("song.mp3", "a track")

        val landed = MediaCommit.commit(from, into, setOf("a.jpg", "b.mp4", "song.mp3"))

        assertEquals(mapOf("a.jpg" to "a.jpg", "b.mp4" to "b.mp4", "song.mp3" to "song.mp3"), landed)
        assertEquals(listOf("a.jpg", "b.mp4", "song.mp3"), names())
        assertEquals("first picture", File(into, "a.jpg").readText())
    }

    // ---- interruption, stage by stage -------------------------------------------------------

    @Test
    fun `interrupted while copying leaves the live files untouched`() {
        folders()
        // The state being killed during the copies leaves: working files, nothing else.
        live("mine.jpg", "my photograph")
        File(into, "a.jpg${MediaCommit.INCOMING_SUFFIX}").writeText("half a pic")
        File(into, "b.jpg${MediaCommit.INCOMING_SUFFIX}").writeText("")

        // What the next run does about it, with the age guard satisfied.
        MediaCommit.recoverLeftovers(into, now = Long.MAX_VALUE)

        assertEquals("my photograph", File(into, "mine.jpg").readText())
        assertEquals(listOf("mine.jpg"), names())
    }

    @Test
    fun `interrupted part-way through the moves leaves every live file in place`() {
        folders()
        // Killed after one file was moved in and before the next: one new file under a free
        // name, one working file. Neither can be something a page was pointing at.
        live("mine.jpg", "my photograph")
        live("also-mine.mp4", "my video")
        File(into, "restored-1.jpg").writeText("a restored picture")
        File(into, "restored-2.jpg${MediaCommit.INCOMING_SUFFIX}").writeText("half of one")

        MediaCommit.recoverLeftovers(into, now = Long.MAX_VALUE)

        assertEquals("my photograph", File(into, "mine.jpg").readText())
        assertEquals("my video", File(into, "also-mine.mp4").readText())
        // The orphan stays: nothing refers to it, and this is not the place that decides a file
        // under a real name is unwanted.
        assertEquals(listOf("also-mine.mp4", "mine.jpg", "restored-1.jpg"), names())
    }

    @Test
    fun `a finished commit has altered no existing file, so dying before the write loses nothing`() {
        folders()
        // This is the window the whole design is about: files committed, assignments not yet
        // saved. Every file the old assignments named must still be exactly as it was.
        val wasHere = mapOf(
            "page_0_111.jpg" to "my first photograph",
            "page_1_222.jpg" to "my second photograph",
            "audio_1_333.mp3" to "my track",
        )
        wasHere.forEach { (name, text) -> live(name, text) }
        stage("page_0_999.jpg", "the restored picture")
        stage("audio_0_999.mp3", "the restored track")

        val landed = MediaCommit.commit(from, into, setOf("page_0_999.jpg", "audio_0_999.mp3"))
        assertNotNull(landed)

        wasHere.forEach { (name, text) ->
            assertEquals("$name was altered", text, File(into, name).readText())
        }
        assertTrue(names().containsAll(wasHere.keys))
        assertTrue(names().none { it.endsWith(MediaCommit.INCOMING_SUFFIX) })
    }

    @Test
    fun `a failure part-way through puts the folder back exactly as it was`() {
        folders()
        val before = mapOf("mine.jpg" to "my photograph", "mine.mp4" to "my video")
        before.forEach { (name, text) -> live(name, text) }
        stage("a.jpg", "a restored picture")

        // "missing.jpg" is what a short or damaged archive looks like by the time it gets here.
        assertNull(MediaCommit.commit(from, into, listOf("a.jpg", "missing.jpg")))

        assertEquals(before, contents())
    }

    @Test
    fun `a working file still being written is left alone`() {
        folders()
        // The race worth guarding: a restore runs in the background while something else in the
        // app tidies up. A copy in progress must not be deleted out from under it.
        val fresh = File(into, "a.jpg${MediaCommit.INCOMING_SUFFIX}").apply { writeText("copying") }

        MediaCommit.recoverLeftovers(into, now = fresh.lastModified() + 1_000)

        assertEquals(listOf("a.jpg${MediaCommit.INCOMING_SUFFIX}"), names())
    }

    // ---- never overwriting -----------------------------------------------------------------

    @Test
    fun `a name already holding different bytes is not overwritten`() {
        folders()
        live("page_0_111.jpg", "the photograph I already had")
        stage("page_0_111.jpg", "a different photograph of the same name")

        val landed = MediaCommit.commit(from, into, setOf("page_0_111.jpg"))

        // Mine is untouched, theirs landed beside it, and the caller is told where.
        assertEquals("the photograph I already had", File(into, "page_0_111.jpg").readText())
        assertEquals("page_0_111-1.jpg", landed?.get("page_0_111.jpg"))
        assertEquals(
            "a different photograph of the same name",
            File(into, "page_0_111-1.jpg").readText(),
        )
    }

    @Test
    fun `a name already holding the same bytes is reused rather than copied again`() {
        folders()
        live("page_0_111.jpg", "the very same photograph")
        stage("page_0_111.jpg", "the very same photograph")

        val landed = MediaCommit.commit(from, into, setOf("page_0_111.jpg"))

        assertEquals(mapOf("page_0_111.jpg" to "page_0_111.jpg"), landed)
        assertEquals(listOf("page_0_111.jpg"), names())
    }

    @Test
    fun `two restored files never land on the same name`() {
        folders()
        // "a.jpg" has to move aside, and the name it would move to is itself being restored.
        live("a.jpg", "mine")
        stage("a.jpg", "theirs")
        stage("a-1.jpg", "theirs as well")

        val landed = MediaCommit.commit(from, into, listOf("a.jpg", "a-1.jpg"))

        assertNotNull(landed)
        assertEquals("mine", File(into, "a.jpg").readText())
        assertEquals(2, landed!!.values.toSet().size)
        landed.forEach { (source, target) ->
            assertEquals(File(from, source).readText(), File(into, target).readText())
        }
    }

    @Test
    fun `a free name skips the taken ones, working files included`() {
        folders()
        live("a.jpg", "mine")
        File(into, "b.jpg${MediaCommit.INCOMING_SUFFIX}").writeText("in progress")

        assertEquals("a-1.jpg", MediaCommit.freeName(into, "a.jpg"))
        assertEquals("b-1.jpg", MediaCommit.freeName(into, "b.jpg"))
        assertEquals("c.jpg", MediaCommit.freeName(into, "c.jpg"))
        assertEquals("c-1.jpg", MediaCommit.freeName(into, "c.jpg", taken = setOf("c.jpg")))
        // A name with no extension keeps working.
        assertEquals("plain", MediaCommit.freeName(into, "plain"))
    }

    // ---- the guards ------------------------------------------------------------------------

    @Test
    fun `a name that could escape the folder is refused outright`() {
        folders()
        live("a.jpg", "mine")

        listOf("../escape.jpg", "sub/dir.jpg", "..", ".hidden", "").forEach { name ->
            assertNull("accepted $name", MediaCommit.commit(from, into, listOf(name)))
        }
        assertEquals(listOf("a.jpg"), names())
    }

    @Test
    fun `a name that looks like a working file is refused`() {
        folders()
        stage("a.jpg${MediaCommit.INCOMING_SUFFIX}", "confusing")

        assertNull(MediaCommit.commit(from, into, listOf("a.jpg${MediaCommit.INCOMING_SUFFIX}")))
        assertTrue(MediaCommit.isCommittable("a.jpg"))
    }

    @Test
    fun `committing nothing is allowed and does nothing`() {
        folders()
        live("a.jpg", "mine")

        assertEquals(emptyMap<String, String>(), MediaCommit.commit(from, into, emptySet()))
        assertEquals(listOf("a.jpg"), names())
    }

    @Test
    fun `the folder is created when it is not there yet`() {
        folders()
        val fresh = File(temp.root, "not-made-yet")
        stage("a.jpg", "first picture")

        assertNotNull(MediaCommit.commit(from, fresh, setOf("a.jpg")))
        assertEquals("first picture", File(fresh, "a.jpg").readText())
    }

    @Test
    fun `bytes arrive intact for a file larger than the copy buffer`() {
        folders()
        val big = ByteArray(300 * 1024) { (it % 251).toByte() }
        File(from, "big.jpg").writeBytes(big)

        assertNotNull(MediaCommit.commit(from, into, setOf("big.jpg")))
        assertTrue(big.contentEquals(File(into, "big.jpg").readBytes()))
    }
}
