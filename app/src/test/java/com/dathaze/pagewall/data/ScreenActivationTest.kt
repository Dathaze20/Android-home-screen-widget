package com.dathaze.pagewall.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Switching a screen off must hide a picture and never lose one.
 *
 * The distinction these tests defend: *saved* and *shown* are two different things. Everything
 * here is about the second one changing while the first one does not.
 */
class ScreenActivationTest {

    private val photo = PageConfig(0, mediaFile = "page_0_1.jpg")
    private val video = PageConfig(
        1,
        mediaFile = "page_1_2.mp4",
        mediaKind = MediaKind.VIDEO,
        posterFile = "poster_1_2.jpg",
    )
    private val withSong = PageConfig(2, mediaFile = "page_2_3.jpg", audioFile = "audio_2_3.mp3")

    private fun allPresent(_name: String) = true

    // ---- what the wallpaper is shown ------------------------------------------------------

    @Test
    fun `a switched-on screen is shown exactly as it is saved`() {
        assertEquals(photo, ScreenActivation.shown(photo))
        assertEquals(video, ScreenActivation.shown(video))
    }

    @Test
    fun `a switched-off screen is shown as empty, keeping its own number`() {
        val off = photo.copy(active = false)
        val shown = ScreenActivation.shown(off)

        assertEquals(0, shown.index)
        assertFalse(shown.hasMedia)
        assertFalse(shown.hasAudio)
        // The engine draws a plain background for a page with nothing on it, which is what a
        // switched-off screen must look like. It must never be handed another screen's picture.
        assertEquals(null, shown.mediaFile)
        assertEquals(null, shown.posterFile)
    }

    @Test
    fun `switching off never alters the saved record`() {
        val off = withSong.copy(active = false)
        ScreenActivation.shown(off)

        // The file names are what a restore would have to recreate. They stay put.
        assertEquals("page_2_3.jpg", off.mediaFile)
        assertEquals("audio_2_3.mp3", off.audioFile)
        assertEquals(2, off.index)
        assertTrue(off.hasSavedMedia)
    }

    @Test
    fun `a screen switched off goes silent as well as dark`() {
        // The soundtrack is part of the page, so hiding the page stops the music. The file is
        // still named by the saved record.
        val off = withSong.copy(active = false)
        assertEquals(null, ScreenActivation.shown(off).audioFile)
        assertEquals("audio_2_3.mp3", off.audioFile)
    }

    @Test
    fun `an empty screen is unaffected either way`() {
        val empty = PageConfig(4)
        assertEquals(empty, ScreenActivation.shown(empty))
        assertEquals(PageConfig(4, active = false), ScreenActivation.shown(empty.copy(active = false)))
    }

    @Test
    fun `a page saved before this feature existed counts as switched on`() {
        // PageConfig's default is what an older stored record decodes to, and it has to mean
        // "carry on showing this" or an update would blank somebody's home screen.
        assertTrue(PageConfig(0, mediaFile = "old.jpg").active)
        assertTrue(PageConfig(0, mediaFile = "old.jpg").isShowing)
    }

    // ---- reapplying ------------------------------------------------------------------------

    @Test
    fun `reapply switches on every saved screen and leaves the numbers alone`() {
        val pages = listOf(
            photo.copy(active = false),
            video.copy(active = false),
            withSong.copy(active = false),
        )
        val plan = ScreenActivation.planReapply(pages, ::allPresent)

        assertEquals(listOf(0, 1, 2), plan.switchedOn)
        assertEquals(emptyList<Int>(), plan.alreadyOn)
        assertEquals(3, plan.showing)
        assertFalse(plan.hasProblems)
        assertEquals("3 saved screens reactivated.", ScreenActivation.message(plan))
    }

    @Test
    fun `reapply counts screens that were already on rather than claiming to have fixed them`() {
        val pages = listOf(photo, video.copy(active = false), withSong)
        val plan = ScreenActivation.planReapply(pages, ::allPresent)

        assertEquals(listOf(1), plan.switchedOn)
        assertEquals(listOf(0, 2), plan.alreadyOn)
        assertEquals(3, plan.showing)
    }

    @Test
    fun `reapply with everything already on is still an honest five screens`() {
        val five = (0..4).map { PageConfig(it, mediaFile = "page_${it}_1.jpg") }
        val plan = ScreenActivation.planReapply(five, ::allPresent)

        assertEquals(emptyList<Int>(), plan.switchedOn)
        assertEquals(5, plan.showing)
        assertEquals("5 saved screens reactivated.", ScreenActivation.message(plan))
    }

    @Test
    fun `reapply ignores screens with nothing saved on them`() {
        val pages = listOf(photo, PageConfig(1), PageConfig(2))
        val plan = ScreenActivation.planReapply(pages, ::allPresent)

        assertEquals(1, plan.showing)
        assertEquals("1 saved screen reactivated.", ScreenActivation.message(plan))
    }

    @Test
    fun `a screen whose file has gone is reported, not switched on`() {
        // The failure this exists to prevent: switching a screen on, showing a blank home
        // screen, and reporting success.
        val pages = listOf(photo, video.copy(active = false))
        val plan = ScreenActivation.planReapply(pages) { it != "page_1_2.mp4" }

        assertEquals(emptyList<Int>(), plan.switchedOn)
        assertEquals(listOf(1), plan.unreadable)
        assertTrue(plan.hasProblems)

        val said = ScreenActivation.message(plan)
        assertTrue(said, said.contains("1 saved screen reactivated."))
        assertTrue(said, said.contains("Screen 2 could not be read"))
    }

    @Test
    fun `a missing poster frame or soundtrack counts as unreadable too`() {
        assertEquals(listOf(1), ScreenActivation.planReapply(listOf(video)) { it != "poster_1_2.jpg" }.unreadable)
        assertEquals(listOf(2), ScreenActivation.planReapply(listOf(withSong)) { it != "audio_2_3.mp3" }.unreadable)
    }

    @Test
    fun `several unreadable screens are listed by number, one-based`() {
        val pages = listOf(photo, video, withSong)
        val plan = ScreenActivation.planReapply(pages) { it == "page_1_2.mp4" || it == "poster_1_2.jpg" }

        assertEquals(listOf(0, 2), plan.unreadable)
        assertTrue(ScreenActivation.message(plan).contains("Screens 1 and 3 could not be read"))
    }

    @Test
    fun `nothing saved at all says so rather than reporting zero screens`() {
        val plan = ScreenActivation.planReapply(listOf(PageConfig(0), PageConfig(1)), ::allPresent)
        assertEquals(0, plan.showing)
        assertEquals("Nothing is saved yet, so there was nothing to reapply.", ScreenActivation.message(plan))
    }

    @Test
    fun `reapply reports screens in order whatever order they are stored in`() {
        val jumbled = listOf(
            PageConfig(4, mediaFile = "e.jpg", active = false),
            PageConfig(1, mediaFile = "b.jpg", active = false),
            PageConfig(3, mediaFile = "d.jpg", active = false),
        )
        assertEquals(listOf(1, 3, 4), ScreenActivation.planReapply(jumbled, ::allPresent).switchedOn)
    }

    @Test
    fun `reapply never moves a picture onto a different screen number`() {
        val pages = listOf(
            PageConfig(0, mediaFile = "first.jpg", active = false),
            PageConfig(3, mediaFile = "fourth.jpg", active = false),
        )
        val plan = ScreenActivation.planReapply(pages, ::allPresent)

        // Screen 4 stays screen 4; the gap at 1 and 2 is not closed up.
        assertEquals(listOf(0, 3), plan.switchedOn)
        assertEquals("first.jpg", pages.first { it.index == 0 }.mediaFile)
        assertEquals("fourth.jpg", pages.first { it.index == 3 }.mediaFile)
    }

    // ---- the tick and NOW are different things ---------------------------------------------

    @Test
    fun `the tick follows the switch, not the tracker`() {
        // isShowing is what puts a tick on a tile. Nothing here knows or cares which screen the
        // wallpaper is currently tracking, which is what keeps NOW independent of it.
        assertTrue(photo.isShowing)
        assertFalse(photo.copy(active = false).isShowing)
        assertFalse(PageConfig(0).isShowing)
        assertFalse(PageConfig(0, active = false).isShowing)
    }
}
