package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A manifest that parses is not yet one worth acting on. */
class BackupManifestTest {

    private fun manifest(
        pages: List<BackupPage> = listOf(BackupPage(0, mediaFile = "a.jpg")),
        files: List<BackupEntry> = listOf(BackupEntry("a.jpg", 12, "a".repeat(64))),
    ) = BackupManifest(
        version = 1,
        exported = "2026-10-08T00:00:00Z",
        appId = "com.dathaze.pagewall",
        appVersion = "1.0.9",
        pageCount = 5,
        pages = pages,
        settings = mapOf("parallax" to "b:true", "audio_volume" to "f:0.5"),
        files = files,
    )

    @Test
    fun `a manifest survives a round trip through json`() {
        val back = BackupManifest.parse(manifest().toJson())
        assertNotNull(back)
        assertEquals(manifest(), back)
    }

    @Test
    fun `the format does not name an application id as a condition of restoring`() {
        // Backups must move between the personal and public builds, so the recorded id is
        // information only. Parsing one taken from the other build has to succeed.
        val fromPublic = manifest().copy(appId = "io.github.dathaze20.pagewallpaper")
        assertNotNull(BackupManifest.parse(fromPublic.toJson()))
    }

    @Test
    fun `anything that is not a backup parses to nothing`() {
        listOf("", "{}", "not json at all", "[1,2,3]", """{"version":0}""")
            .forEach { assertNull("accepted: $it", BackupManifest.parse(it)) }
    }

    @Test
    fun `a backup from a future version is refused rather than half understood`() {
        val future = manifest().toJson().replace("\"version\": 1", "\"version\": 99")
        assertNull(BackupManifest.parse(future))
    }

    @Test
    fun `a page referring to a file the manifest does not declare is rejected`() {
        val lying = manifest(
            pages = listOf(BackupPage(0, mediaFile = "a.jpg"), BackupPage(1, mediaFile = "ghost.jpg")),
        )
        assertFalse(lying.isSelfConsistent())
        assertNull(BackupManifest.parse(lying.toJson()))
    }

    @Test
    fun `duplicate page indices are rejected`() {
        val twice = manifest(pages = listOf(BackupPage(0, "a.jpg"), BackupPage(0, "a.jpg")))
        assertFalse(twice.isSelfConsistent())
    }

    @Test
    fun `an entry without a real checksum is rejected`() {
        assertFalse(manifest(files = listOf(BackupEntry("a.jpg", 12, ""))).isSelfConsistent())
        assertFalse(manifest(files = listOf(BackupEntry("a.jpg", 12, "zz"))).isSelfConsistent())
        assertFalse(manifest(files = listOf(BackupEntry("a.jpg", -1, "a".repeat(64)))).isSelfConsistent())
    }

    @Test
    fun `file names that could escape a directory are refused`() {
        listOf("../a.jpg", "a/b.jpg", "a\\b.jpg", "..", ".", "", ".hidden")
            .forEach { assertFalse("accepted: $it", BackupEntry.isSafeName(it)) }
        listOf("a.jpg", "page_0_123.jpg", "poster_2_9.jpg").forEach {
            assertTrue("refused: $it", BackupEntry.isSafeName(it))
        }
    }

    @Test
    fun `a file declared twice is rejected`() {
        val dupes = listOf(BackupEntry("a.jpg", 1, "a".repeat(64)), BackupEntry("a.jpg", 2, "b".repeat(64)))
        assertFalse(manifest(files = dupes).isSelfConsistent())
    }
}
