package com.dathaze.pagewall.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dangerous failure here is firing during the tail of an ordinary swipe, which would drag the
 * wallpaper back to screen 1 every time you tried to move away from it.
 */
class HomeJumpPolicyTest {

    private fun jump(
        mode: DetectionMode = DetectionMode.TOUCH,
        enabled: Boolean = true,
        currentPage: Int = 3,
        defaultHomePage: Int = 0,
        msSinceLastTouch: Long = 5_000,
        msSinceLastJump: Long = 60_000,
    ) = HomeJumpPolicy.shouldJumpHome(
        mode, enabled, currentPage, defaultHomePage, msSinceLastTouch, msSinceLastJump,
    )

    @Test
    fun `an untouched launcher scrolling itself counts as going home`() {
        assertTrue(jump())
    }

    @Test
    fun `the settling tail of a swipe never counts`() {
        assertFalse(jump(msSinceLastTouch = 0))
        assertFalse(jump(msSinceLastTouch = 300))
        assertFalse(jump(msSinceLastTouch = HomeJumpPolicy.TOUCH_SETTLE_MS - 1))
        assertTrue(jump(msSinceLastTouch = HomeJumpPolicy.TOUCH_SETTLE_MS))
    }

    @Test
    fun `one burst of reports cannot fire it over and over`() {
        assertFalse(jump(msSinceLastJump = 0))
        assertFalse(jump(msSinceLastJump = HomeJumpPolicy.JUMP_COOLDOWN_MS - 1))
        assertTrue(jump(msSinceLastJump = HomeJumpPolicy.JUMP_COOLDOWN_MS))
    }

    @Test
    fun `off by default means off`() {
        assertFalse(jump(enabled = false))
    }

    @Test
    fun `offset tracking never needs the guess`() {
        assertFalse(jump(mode = DetectionMode.OFFSET))
    }

    @Test
    fun `standing on the home screen already is not a jump`() {
        assertFalse(jump(currentPage = 0, defaultHomePage = 0))
        assertFalse(jump(currentPage = 2, defaultHomePage = 2))
    }
}
