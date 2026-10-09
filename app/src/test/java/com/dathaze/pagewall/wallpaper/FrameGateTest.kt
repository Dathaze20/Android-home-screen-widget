package com.dathaze.pagewall.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The combination that matters is visible-but-no-surface: a surface can be destroyed and
 * recreated without the wallpaper ever being hidden, and the engine has to survive that.
 */
class FrameGateTest {

    @Test
    fun `draws when on screen with a surface and no video`() {
        assertTrue(FrameGate.canDraw(visible = true, surfaceAlive = true, videoMode = false))
    }

    @Test
    fun `a destroyed surface stops drawing without the wallpaper being hidden`() {
        assertFalse(FrameGate.canDraw(visible = true, surfaceAlive = false, videoMode = false))
    }

    @Test
    fun `hidden never draws, surface or not`() {
        assertFalse(FrameGate.canDraw(visible = false, surfaceAlive = true, videoMode = false))
        assertFalse(FrameGate.canDraw(visible = false, surfaceAlive = false, videoMode = false))
    }

    @Test
    fun `video mode keeps the canvas off a surface the player owns`() {
        assertFalse(FrameGate.canDraw(visible = true, surfaceAlive = true, videoMode = true))
    }

    @Test
    fun `choosing a mode is allowed while a video owns the surface`() {
        // render() decides whether to keep the player or take the surface back, so it must not
        // be blocked by the very state it exists to leave.
        assertTrue(FrameGate.hasSurface(visible = true, surfaceAlive = true))
        assertFalse(FrameGate.canDraw(visible = true, surfaceAlive = true, videoMode = true))
    }

    @Test
    fun `nothing touches a surface that is gone or hidden`() {
        assertFalse(FrameGate.hasSurface(visible = true, surfaceAlive = false))
        assertFalse(FrameGate.hasSurface(visible = false, surfaceAlive = true))
    }

    @Test
    fun `visibility and surface are independent, not interchangeable`() {
        // The bug this replaces treated them as one flag: destroying the surface cleared
        // visibility, and nothing set it back when the surface returned.
        assertFalse(FrameGate.canDraw(visible = true, surfaceAlive = false, videoMode = false))
        assertFalse(FrameGate.canDraw(visible = false, surfaceAlive = true, videoMode = false))
        assertTrue(FrameGate.canDraw(visible = true, surfaceAlive = true, videoMode = false))
    }
}
