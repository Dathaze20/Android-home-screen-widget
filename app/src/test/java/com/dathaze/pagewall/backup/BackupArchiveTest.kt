package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Real zip files on a real file system, because the cases worth testing are the damaged ones and
 * they cannot be faked convincingly: a truncated archive, a declared picture that is not inside,
 * bytes that changed after they were written.
 */
class BackupArchiveTest {

    @get:Rule val temp = TemporaryFolder()

    private val media = mapOf(
        "page_0_1.jpg" to "first picture".toByteArray(),
        "page_1_2.jpg" to "second picture".toByteArray(),
        "poster_2_3.jpg" to "a poster frame".toByteArray(),
    )

    private val pages = listOf(
        BackupPage(0, mediaFile = "page_0_1.jpg"),
        BackupPage(1, mediaFile = "page_1_2.jpg"),
        BackupPage(2, mediaFile = "page_1_2.jpg", mediaKind = "VIDEO", posterFile = "poster_2_3.jpg"),
    )

    private fun writeBackup(
        into: File = temp.newFile("backup.zip"),
        contents: Map<String, ByteArray> = media,
        pageList: List<BackupPage> = pages,
    ): Pair<File, BackupWriteResult> {
        val result = into.outputStream().use { out ->
            BackupArchive.write(
                target = out,
                pages = pageList,
                settings = mapOf("crossfade_ms" to "i:350", "photo_fit" to "s:FULL_IMAGE"),
                appId = "com.dathaze.pagewall",
                appVersion = "1.0.9",
                pageCount = 5,
                exported = "2026-10-08T00:00:00Z",
                openFile = { name -> contents[name]?.let { ByteArrayInputStream(it) } },
            )
        }
        return into to result
    }

    @Test
    fun `a backup written here verifies and reads back what went in`() {
        val (file, result) = writeBackup()
        assertTrue(result.skipped.isEmpty())

        val check = BackupArchive.verify(file)
        assertTrue("expected Ok, got $check", check is BackupCheck.Ok)
        val manifest = (check as BackupCheck.Ok).manifest

        assertEquals(BackupManifest.CURRENT_VERSION, manifest.version)
        assertEquals(5, manifest.pageCount)
        assertEquals(3, manifest.pages.size)
        assertEquals(3, manifest.files.size)
        assertEquals("i:350", manifest.settings["crossfade_ms"])
        // A file used by two pages is stored once.
        assertEquals(1, manifest.files.count { it.name == "page_1_2.jpg" })
    }

    @Test
    fun `a truncated archive is reported damaged, not restored`() {
        val (file, _) = writeBackup()
        RandomAccessFile(file, "rw").use { it.setLength(file.length() / 2) }

        val check = BackupArchive.verify(file)
        assertTrue("truncated file passed verification: $check", check is BackupCheck.Damaged)
    }

    @Test
    fun `a file altered after the backup was written fails its checksum`() {
        val (file, _) = writeBackup()
        // Rebuild the same archive with one picture's bytes changed but the original manifest
        // left in place: the size is identical, so only the hash can catch it.
        val tampered = temp.newFile("tampered.zip")
        val original = java.util.zip.ZipFile(file)
        java.util.zip.ZipOutputStream(tampered.outputStream()).use { out ->
            original.use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    val bytes = zip.getInputStream(entry).readBytes()
                    out.write(
                        if (entry.name.endsWith("page_0_1.jpg")) "FIRST PICTURE".toByteArray()
                        else bytes
                    )
                    out.closeEntry()
                }
            }
        }

        val check = BackupArchive.verify(tampered)
        assertTrue("tampered file passed verification: $check", check is BackupCheck.Damaged)
        assertTrue((check as BackupCheck.Damaged).reason.contains("checksum"))
    }

    @Test
    fun `a zip that is not a backup is rejected with a readable reason`() {
        val notABackup = temp.newFile("photos.zip")
        java.util.zip.ZipOutputStream(notABackup.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("holiday.jpg"))
            it.write("not a backup".toByteArray())
            it.closeEntry()
        }
        val check = BackupArchive.verify(notABackup)
        assertTrue(check is BackupCheck.Damaged)
        assertTrue((check as BackupCheck.Damaged).reason.contains("manifest"))
    }

    @Test
    fun `an empty or missing file is rejected rather than crashing`() {
        assertTrue(BackupArchive.verify(temp.newFile("empty.zip")) is BackupCheck.Damaged)
        assertTrue(BackupArchive.verify(File(temp.root, "nope.zip")) is BackupCheck.Damaged)
    }

    @Test
    fun `a page whose file has vanished is reported, not written as a broken promise`() {
        // The picture for page 1 is gone from disk by the time the backup runs.
        val (file, result) = writeBackup(contents = media - "page_1_2.jpg")

        assertTrue("page_1_2.jpg" in result.skipped)
        // The manifest must not reference what the archive does not carry, or verify would
        // rightly call the backup incomplete.
        val check = BackupArchive.verify(file)
        assertTrue("expected Ok, got $check", check is BackupCheck.Ok)
        val manifest = (check as BackupCheck.Ok).manifest
        assertNull(manifest.pages.first { it.index == 1 }.mediaFile)
        assertTrue(manifest.files.none { it.name == "page_1_2.jpg" })
    }

    @Test
    fun `extracting puts the named files in the staging directory and nowhere else`() {
        val (file, _) = writeBackup()
        val staging = temp.newFolder("staging")

        val written = BackupArchive.extract(file, setOf("page_0_1.jpg"), staging)
        assertEquals(setOf("page_0_1.jpg"), written)
        assertEquals("first picture", File(staging, "page_0_1.jpg").readText())
        // Only what was asked for.
        assertEquals(1, staging.listFiles()!!.size)
    }

    @Test
    fun `extraction fails as a whole rather than leaving a half restore`() {
        val (file, _) = writeBackup()
        val staging = temp.newFolder("staging2")
        assertNull(BackupArchive.extract(file, setOf("page_0_1.jpg", "not-in-here.jpg"), staging))
    }

    @Test
    fun `a name that tries to climb out of the staging directory is refused`() {
        val (file, _) = writeBackup()
        val staging = temp.newFolder("staging3")
        assertNull(BackupArchive.extract(file, setOf("../escaped.jpg"), staging))
        assertNull(BackupArchive.extract(file, setOf("/etc/passwd"), staging))
    }

    @Test
    fun `verifying does not modify the file it checks`() {
        val (file, _) = writeBackup()
        val before = file.readBytes()
        assertNotNull(BackupArchive.verify(file))
        assertTrue(before.contentEquals(file.readBytes()))
    }
}
