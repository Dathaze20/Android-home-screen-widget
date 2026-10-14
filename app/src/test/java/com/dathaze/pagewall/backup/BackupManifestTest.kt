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

    // ---- which screens were switched on -----------------------------------------------------

    @Test
    fun `whether a screen was switched on survives the round trip`() {
        val pages = listOf(
            BackupPage(0, mediaFile = "a.jpg", active = true),
            BackupPage(1, mediaFile = "b.jpg", active = false),
        )
        val manifest = BackupManifest(
            version = BackupManifest.CURRENT_VERSION,
            exported = "2026-10-14T00:00:00Z",
            appId = "io.github.dathaze20.pagewallpaper",
            appVersion = "1.0.10",
            pageCount = 5,
            pages = pages,
            settings = emptyMap(),
            files = listOf(
                BackupEntry("a.jpg", 1, "a".repeat(64)),
                BackupEntry("b.jpg", 1, "b".repeat(64)),
            ),
        )

        val back = BackupManifest.parse(manifest.toJson())
        assertNotNull(back)
        assertTrue(back!!.pages.first { it.index == 0 }.active)
        assertFalse(back.pages.first { it.index == 1 }.active)
    }

    @Test
    fun `a backup written before the switch existed restores every screen switched on`() {
        // The shape every backup up to v1.0.10 has: no "active" key anywhere. Reading one must
        // not leave somebody's screens switched off.
        val old = """
            {
              "version": 1,
              "exported": "2026-10-09T00:00:00Z",
              "appId": "io.github.dathaze20.pagewallpaper",
              "appVersion": "1.0.9",
              "pageCount": 5,
              "pages": [
                {"index": 0, "mediaFile": "a.jpg", "mediaKind": "IMAGE"},
                {"index": 1, "mediaFile": "b.jpg", "mediaKind": "IMAGE"}
              ],
              "settings": {},
              "files": [
                {"name": "a.jpg", "size": 1, "sha256": "${"a".repeat(64)}"},
                {"name": "b.jpg", "size": 1, "sha256": "${"b".repeat(64)}"}
              ]
            }
        """.trimIndent()

        val manifest = BackupManifest.parse(old)
        assertNotNull(manifest)
        assertTrue(manifest!!.pages.all { it.active })
    }

    @Test
    fun `switching a screen off does not drop its files from the backup`() {
        // A switched-off screen is still carried, media and all, or a backup taken while it was
        // off would lose the picture it was meant to protect.
        val off = BackupPage(0, mediaFile = "a.jpg", posterFile = "p.jpg", audioFile = "s.mp3", active = false)
        val manifest = BackupManifest(
            version = 1,
            exported = "2026-10-14T00:00:00Z",
            appId = "io.github.dathaze20.pagewallpaper",
            appVersion = "1.0.10",
            pageCount = 5,
            pages = listOf(off),
            settings = emptyMap(),
            files = listOf(
                BackupEntry("a.jpg", 1, "a".repeat(64)),
                BackupEntry("p.jpg", 1, "c".repeat(64)),
                BackupEntry("s.mp3", 1, "d".repeat(64)),
            ),
        )
        assertEquals(setOf("a.jpg", "p.jpg", "s.mp3"), manifest.referencedFiles())
        assertNotNull(BackupManifest.parse(manifest.toJson()))
    }
}
