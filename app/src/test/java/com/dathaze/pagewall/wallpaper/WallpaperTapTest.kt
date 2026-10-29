package com.dathaze.pagewall.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #7, Sony Xperia 10 VII: a single tap on an empty home screen opened the app, and a long
 * press opened it over the launcher's own menu as the finger lifted.
 *
 * The cause was treating `android.wallpaper.tap` as a double tap. Android does not say what kind
 * of touch produced it — the launcher decides, and AOSP-derived launchers report every single
 * tap, plus one more when a long press ends. So the question is no longer "was this a tap" but
 * "did anybody ask for taps to open the app", and the answer is no unless they did.
 */
class WallpaperTapTest {

    @Test
    fun `a tap does not open the app`() {
        // The report, in one line. This is the default on every phone, every launcher.
        assertFalse(WallpaperTap.opensApp(WallpaperTap.COMMAND_TAP, enabled = false))
    }

    @Test
    fun `the end of a long press does not open the app either`() {
        // Same command, sent again when the finger lifts off a long press. It must not be able
        // to appear over the launcher's own menu.
        repeat(3) {
            assertFalse(WallpaperTap.opensApp(WallpaperTap.COMMAND_TAP, enabled = false))
        }
    }

    @Test
    fun `a tap opens the app only when that was asked for`() {
        assertTrue(WallpaperTap.opensApp(WallpaperTap.COMMAND_TAP, enabled = true))
    }

    @Test
    fun `no other wallpaper command opens the app, switched on or off`() {
        // The engine is sent other commands — a dropped launcher icon, a reported scroll. None
        // of them is a touch and none may open anything.
        listOf(
            "android.wallpaper.drop",
            "android.home.drop",
            "android.wallpaper.secondaryTap",
            "android.wallpaper.reapply",
            "",
            null,
        ).forEach { action ->
            assertFalse("$action", WallpaperTap.opensApp(action, enabled = false))
            assertFalse("$action", WallpaperTap.opensApp(action, enabled = true))
        }
    }

    @Test
    fun `the command string is the one Android actually sends`() {
        // WallpaperManager.COMMAND_TAP. Matching on anything else would silently disable the
        // setting for the people who turn it on.
        assertTrue(WallpaperTap.COMMAND_TAP == "android.wallpaper.tap")
    }
}
