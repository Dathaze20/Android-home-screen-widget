package com.dathaze.pagewall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tag-to-versionCode rule, which has to agree with the release workflow exactly. */
class UpdateVersionTest {

    @Test
    fun `a tag maps onto the version code the workflow builds`() {
        assertEquals(10000, UpdateVersion.codeFromTag("v1.0.0"))
        assertEquals(10402, UpdateVersion.codeFromTag("1.4.2"))
        assertEquals(20000, UpdateVersion.codeFromTag("v2.0.0"))
        assertEquals(10500, UpdateVersion.codeFromTag("v1.5.0"))
    }

    @Test
    fun `codes sort the way the versions do`() {
        val ordered = listOf("v1.0.0", "v1.0.9", "v1.4.2", "v1.5.0", "v2.0.0")
            .map { UpdateVersion.codeFromTag(it)!! }
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun `a tag that is not a version is rejected rather than guessed at`() {
        assertNull(UpdateVersion.codeFromTag("latest"))
        assertNull(UpdateVersion.codeFromTag("v1.0"))
        assertNull(UpdateVersion.codeFromTag("1.0.0-beta"))
        assertNull(UpdateVersion.codeFromTag(""))
        assertNull(UpdateVersion.codeFromTag(null))
        // Three digits in a slot would collide with the slot above it.
        assertNull(UpdateVersion.codeFromTag("v1.100.0"))
    }

    @Test
    fun `the public build installed as v1_0_9 is offered v1_0_10`() {
        // The next release, from what is on the maintainer's phone today. The patch number
        // gaining a digit is the interesting part: as text "1.0.10" sorts below "1.0.9", and
        // only the arithmetic gets this right.
        val installed = UpdateVersion.codeFromTag("v1.0.9")
        assertEquals(10009, installed)
        assertEquals(10010, UpdateVersion.codeFromTag("v1.0.10"))
        assertTrue(UpdateVersion.isNewer("v1.0.10", installed!!))

        // ...and the release it is already running is not offered again.
        assertFalse(UpdateVersion.isNewer("v1.0.9", installed))
        assertFalse(UpdateVersion.isNewer("v1.0.8", installed))
    }

    @Test
    fun `only a strictly higher release counts as an update`() {
        assertTrue(UpdateVersion.isNewer("v1.0.1", installedCode = 10000))
        assertTrue(UpdateVersion.isNewer("v2.0.0", installedCode = 10900))
        assertFalse(UpdateVersion.isNewer("v1.0.0", installedCode = 10000))
        assertFalse(UpdateVersion.isNewer("v0.9.9", installedCode = 10000))
        // An unreadable tag must never be treated as an update.
        assertFalse(UpdateVersion.isNewer("nightly", installedCode = 10000))
    }

    @Test
    fun `size is reported in megabytes or not at all`() {
        val update = AvailableUpdate("v1.0.1", "1.0.1", "", "https://x/a.apk", "a.apk", 14451221)
        assertEquals("13.8 MB", update.readableSize)
        assertEquals("", update.copy(apkBytes = 0).readableSize)
    }
}
