package com.dathaze.pagewall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quiet update notice, tested where its decisions actually live.
 *
 * Every rule here is about a clock — a day between requests, a day after a dismissal — and the
 * only way to test a clock without waiting for it is to pass the time in. That is why none of
 * this reads [System.currentTimeMillis] itself.
 */
class UpdateNoticeTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun update(tag: String, bytes: Long = 12_600_000L) = AvailableUpdate(
        tag = tag,
        versionName = tag.removePrefix("v"),
        notes = "notes",
        apkUrl = "https://example.invalid/$tag.apk",
        apkName = "page-wallpaper-public-$tag.apk",
        apkBytes = bytes,
    )

    // ─── How often GitHub is asked ───

    @Test
    fun `a phone that has never checked is due`() {
        assertTrue(UpdateNotice.dueForCheck(lastCheckedAt = 0L, now = now))
    }

    @Test
    fun `a check an hour ago is not due again`() {
        assertFalse(UpdateNotice.dueForCheck(lastCheckedAt = now - hour, now = now))
    }

    @Test
    fun `a check just under a day ago is not due again`() {
        assertFalse(UpdateNotice.dueForCheck(lastCheckedAt = now - 23 * hour, now = now))
    }

    @Test
    fun `a check a day ago is due`() {
        assertTrue(UpdateNotice.dueForCheck(lastCheckedAt = now - 24 * hour, now = now))
    }

    @Test
    fun `a clock that moved backwards does not silence the check forever`() {
        // A phone whose time was wrong and got corrected: the stored stamp is in the future, and
        // "a day since then" would never arrive.
        assertTrue(UpdateNotice.dueForCheck(lastCheckedAt = now + 48 * hour, now = now))
    }

    // ─── Dismissing ───

    @Test
    fun `dismissing holds that version back`() {
        assertTrue(UpdateNotice.snoozeHolds("v1.0.13", "v1.0.13", now - hour, now))
    }

    @Test
    fun `a dismissal expires after a day`() {
        assertFalse(UpdateNotice.snoozeHolds("v1.0.13", "v1.0.13", now - 25 * hour, now))
    }

    @Test
    fun `a dismissal says nothing about the next version`() {
        assertFalse(UpdateNotice.snoozeHolds("v1.0.14", "v1.0.13", now - hour, now))
    }

    @Test
    fun `nothing dismissed holds nothing back`() {
        assertFalse(UpdateNotice.snoozeHolds("v1.0.13", null, 0L, now))
        assertFalse(UpdateNotice.snoozeHolds("v1.0.13", "", 0L, now))
    }

    @Test
    fun `a dismissal stamped in the future does not hold forever`() {
        assertFalse(UpdateNotice.snoozeHolds("v1.0.13", "v1.0.13", now + 48 * hour, now))
    }

    // ─── What a remembered answer is still worth ───

    @Test
    fun `a remembered release newer than this build still counts`() {
        assertTrue(UpdateNotice.stillNewer(update("v1.0.13"), installedVersionCode = 10012))
    }

    @Test
    fun `a remembered release this build has caught up with does not`() {
        // Someone who updated by another route: the cache describes what they are now running.
        assertFalse(UpdateNotice.stillNewer(update("v1.0.13"), installedVersionCode = 10013))
        assertFalse(UpdateNotice.stillNewer(update("v1.0.13"), installedVersionCode = 10014))
    }

    @Test
    fun `nothing remembered counts for nothing`() {
        assertFalse(UpdateNotice.stillNewer(null, installedVersionCode = 10012))
    }

    @Test
    fun `a remembered tag that is not a version counts for nothing`() {
        assertFalse(UpdateNotice.stillNewer(update("nightly"), installedVersionCode = 10012))
    }

    // ─── Whether the strip is up ───

    @Test
    fun `a newer release that has not been dismissed shows the strip`() {
        assertTrue(
            UpdateNotice.bannerVisible(update("v1.0.13"), 10012, null, 0L, now)
        )
    }

    @Test
    fun `nothing at all shows nothing at all`() {
        assertFalse(UpdateNotice.bannerVisible(null, 10012, null, 0L, now))
    }

    @Test
    fun `a dismissed release shows no strip for a day`() {
        assertFalse(
            UpdateNotice.bannerVisible(update("v1.0.13"), 10012, "v1.0.13", now - hour, now)
        )
        assertTrue(
            UpdateNotice.bannerVisible(update("v1.0.13"), 10012, "v1.0.13", now - 25 * hour, now)
        )
    }

    @Test
    fun `dismissing one version does not hide the next`() {
        assertTrue(
            UpdateNotice.bannerVisible(update("v1.0.14"), 10012, "v1.0.13", now - hour, now)
        )
    }

    @Test
    fun `the dot outlives the dismissal that hid the strip`() {
        val found = update("v1.0.13")
        assertFalse(UpdateNotice.bannerVisible(found, 10012, "v1.0.13", now - hour, now))
        // The indicator beside Check for updates follows this instead, so the news stays.
        assertTrue(UpdateNotice.stillNewer(found, 10012))
    }

    // ─── What a finished check means ───

    @Test
    fun `a found release is remembered`() {
        val found = update("v1.0.13")
        assertEquals(
            NoticeOutcome.Found(found),
            UpdateNotice.outcome(UpdateState.Available(found)),
        )
    }

    @Test
    fun `being up to date clears what was remembered`() {
        assertEquals(NoticeOutcome.None, UpdateNotice.outcome(UpdateState.UpToDate))
    }

    @Test
    fun `an offline check says nothing and records nothing`() {
        assertEquals(
            NoticeOutcome.Silent,
            UpdateNotice.outcome(
                UpdateState.Failed("Could not reach GitHub — check your connection")
            ),
        )
    }

    @Test
    fun `a rate limit says nothing and records nothing`() {
        assertEquals(
            NoticeOutcome.Silent,
            UpdateNotice.outcome(
                UpdateState.Failed("GitHub is rate-limiting us — try again later.")
            ),
        )
    }

    @Test
    fun `a release with no APK for this build yet is not an answer worth keeping`() {
        // It becomes one within minutes of the tag being pushed, so it is left unrecorded and
        // asked again next launch rather than held for a day.
        assertEquals(
            NoticeOutcome.Silent,
            UpdateNotice.outcome(
                UpdateState.Failed("v1.0.13 is out, but has no build for this app yet")
            ),
        )
    }

    @Test
    fun `no part of a manual download is mistaken for a check`() {
        assertEquals(NoticeOutcome.Silent, UpdateNotice.outcome(UpdateState.Idle))
        assertEquals(NoticeOutcome.Silent, UpdateNotice.outcome(UpdateState.Checking))
        assertEquals(NoticeOutcome.Silent, UpdateNotice.outcome(UpdateState.Downloading(40)))
        assertEquals(NoticeOutcome.Silent, UpdateNotice.outcome(UpdateState.ReadyToInstall))
        assertEquals(NoticeOutcome.Silent, UpdateNotice.outcome(UpdateState.NeedsInstallPermission))
    }

    // ─── What the strip says ───

    @Test
    fun `the strip names the version that is available`() {
        assertEquals("v1.0.13 is available", UpdateNotice.bannerTitle("v1.0.13"))
    }

    @Test
    fun `the strip gives the installed version and the download size`() {
        assertEquals(
            "You have v1.0.12 • 12.0 MB. Installs over this one; nothing is lost.",
            UpdateNotice.bannerDetail("1.0.12", update("v1.0.13").readableSize),
        )
    }

    @Test
    fun `a release with no reported size says nothing about size`() {
        assertEquals(
            "You have v1.0.12. Installs over this one; nothing is lost.",
            UpdateNotice.bannerDetail("1.0.12", update("v1.0.13", bytes = 0L).readableSize),
        )
    }

    @Test
    fun `an unreadable installed version is left out rather than printed`() {
        assertEquals(
            "12.0 MB. Installs over this one; nothing is lost.",
            UpdateNotice.bannerDetail("unknown", "12.0 MB"),
        )
        assertEquals(
            "12.0 MB. Installs over this one; nothing is lost.",
            UpdateNotice.bannerDetail("", "12.0 MB"),
        )
    }

    @Test
    fun `with neither known the promise is still made`() {
        assertEquals(
            "Installs over this one; nothing is lost.",
            UpdateNotice.bannerDetail("", ""),
        )
    }

    @Test
    fun `the windows are a day, not an hour or a week`() {
        assertEquals(24 * hour, UpdateNotice.CHECK_EVERY_MS)
        assertEquals(24 * hour, UpdateNotice.SNOOZE_MS)
        // Late enough to be clear of the launch, soon enough to be the same session.
        assertTrue(UpdateNotice.FIRST_DELAY_MS in 2_000L..15_000L)
    }
}
