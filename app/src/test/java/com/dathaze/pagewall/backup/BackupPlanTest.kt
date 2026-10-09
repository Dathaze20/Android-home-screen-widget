package com.dathaze.pagewall.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promise being tested is a single sentence: a page that already has something on it is
 * never overwritten unless that was asked for.
 */
class BackupPlanTest {

    private val backup = listOf(
        BackupPage(0, mediaFile = "b0.jpg"),
        BackupPage(1, mediaFile = "b1.jpg"),
        BackupPage(2, mediaFile = "b2.jpg"),
    )

    private fun existing(vararg pages: ExistingPage) = pages.associateBy { it.index }

    @Test
    fun `empty pages are filled without asking, because there is nothing to lose`() {
        val plan = BackupPlan.plan(existing(), backup, ConflictChoice.KEEP_MINE)
        assertEquals(3, plan.size)
        assertTrue(plan.all { it.action == PageAction.FILL_EMPTY })
        assertEquals(3, BackupPlan.summarise(plan).fill)
    }

    @Test
    fun `keeping mine leaves every occupied page exactly as it was`() {
        val here = existing(
            ExistingPage(0, "mine0.jpg"),
            ExistingPage(1, "mine1.jpg"),
        )
        val plan = BackupPlan.plan(here, backup, ConflictChoice.KEEP_MINE)

        assertEquals(PageAction.KEEP, plan.first { it.index == 0 }.action)
        assertEquals(PageAction.KEEP, plan.first { it.index == 1 }.action)
        assertEquals(PageAction.FILL_EMPTY, plan.first { it.index == 2 }.action)

        // Nothing kept is in the set of files to pull out of the archive.
        val needed = BackupPlan.filesNeeded(plan)
        assertEquals(setOf("b2.jpg"), needed)
    }

    @Test
    fun `using the backup replaces only the pages that actually disagree`() {
        val here = existing(
            ExistingPage(0, "mine0.jpg"),
            ExistingPage(1, "b1.jpg"),
        )
        val plan = BackupPlan.plan(here, backup, ConflictChoice.USE_BACKUP)

        assertEquals(PageAction.REPLACE, plan.first { it.index == 0 }.action)
        assertEquals(PageAction.UNCHANGED, plan.first { it.index == 1 }.action)
        assertEquals(PageAction.FILL_EMPTY, plan.first { it.index == 2 }.action)
    }

    @Test
    fun `importing the same backup twice changes nothing the second time`() {
        val first = BackupPlan.plan(existing(), backup, ConflictChoice.KEEP_MINE)
        assertEquals(3, BackupPlan.summarise(first).changes)

        // The state the first import produced.
        val after = existing(*backup.map { ExistingPage(it.index, it.mediaFile) }.toTypedArray())

        listOf(ConflictChoice.KEEP_MINE, ConflictChoice.USE_BACKUP).forEach { choice ->
            val second = BackupPlan.plan(after, backup, choice)
            assertTrue(
                "re-import under $choice wanted to change something",
                second.all { it.action == PageAction.UNCHANGED },
            )
            assertEquals(0, BackupPlan.summarise(second).changes)
            assertTrue(BackupPlan.filesNeeded(second).isEmpty())
        }
    }

    @Test
    fun `no conflicts does not mean the pages are empty`() {
        // Two quite different situations, both with zero conflicts. A summary that cannot tell
        // them apart lets a dialog say "every page here is empty" to somebody looking at five
        // pages full of their own photographs, which is what it did.
        val nothingHere = BackupPlan.summarise(
            BackupPlan.plan(existing(), backup, ConflictChoice.KEEP_MINE)
        )
        val alreadyMatching = BackupPlan.summarise(
            BackupPlan.plan(
                existing(*backup.map { ExistingPage(it.index, it.mediaFile) }.toTypedArray()),
                backup,
                ConflictChoice.KEEP_MINE,
            )
        )

        assertEquals(0, nothingHere.keep)
        assertEquals(0, alreadyMatching.keep)

        // ...and yet they are not the same thing at all.
        assertEquals(3, nothingHere.fill)
        assertEquals(0, nothingHere.unchanged)
        assertEquals(0, alreadyMatching.fill)
        assertEquals(3, alreadyMatching.unchanged)
    }

    @Test
    fun `a page is only a conflict when it holds something different`() {
        val here = existing(
            ExistingPage(0, "b0.jpg"),
            ExistingPage(1, "mine1.jpg"),
            ExistingPage(2, null),
        )
        assertEquals(1, BackupPlan.conflictCount(here, backup))
        assertFalse(BackupPlan.summarise(BackupPlan.plan(existing(), backup, ConflictChoice.KEEP_MINE)).hasConflicts)
    }

    @Test
    fun `a page matching on media but not its poster counts as different`() {
        val here = existing(ExistingPage(0, "b0.jpg", posterFile = "other-poster.jpg"))
        val video = listOf(BackupPage(0, mediaFile = "b0.jpg", posterFile = "b0-poster.jpg"))
        assertEquals(1, BackupPlan.conflictCount(here, video))
        assertEquals(PageAction.KEEP, BackupPlan.plan(here, video, ConflictChoice.KEEP_MINE).single().action)
    }

    @Test
    fun `pages the backup has nothing for are not touched at all`() {
        val emptyInBackup = listOf(BackupPage(0, mediaFile = null), BackupPage(1, mediaFile = "b1.jpg"))
        val here = existing(ExistingPage(0, "mine0.jpg"))
        val plan = BackupPlan.plan(here, emptyInBackup, ConflictChoice.USE_BACKUP)

        assertEquals(1, plan.size)
        assertEquals(1, plan.single().index)
        // Page 0 is absent from the plan entirely, so nothing can clear it.
        assertTrue(plan.none { it.index == 0 })
    }

    @Test
    fun `a page holding only a track still restores`() {
        // The plan used to ask whether a backup page had media, so a page with a song and no
        // picture was dropped from the plan: its track was in the backup and was quietly never
        // put back.
        val audioOnly = listOf(BackupPage(3, audioFile = "song.mp3", audioTitle = "A song"))
        val plan = BackupPlan.plan(existing(), audioOnly, ConflictChoice.KEEP_MINE)

        assertEquals(1, plan.size)
        assertEquals(PageAction.FILL_EMPTY, plan.single().action)
        assertEquals(1, BackupPlan.summarise(plan).fill)
        // And the track is actually pulled out of the archive.
        assertEquals(setOf("song.mp3"), BackupPlan.filesNeeded(plan))
    }

    @Test
    fun `a page here holding only a track is not treated as empty`() {
        // The other half of the same mistake: read as empty, such a page would have been filled
        // without asking, overwriting a track the person chose.
        val here = existing(ExistingPage(3, mediaFile = null, audioFile = "mine.mp3"))
        val fromBackup = listOf(BackupPage(3, audioFile = "theirs.mp3"))

        assertEquals(1, BackupPlan.conflictCount(here, fromBackup))
        assertEquals(
            PageAction.KEEP,
            BackupPlan.plan(here, fromBackup, ConflictChoice.KEEP_MINE).single().action,
        )
        assertTrue(BackupPlan.filesNeeded(BackupPlan.plan(here, fromBackup, ConflictChoice.KEEP_MINE)).isEmpty())
        assertEquals(
            PageAction.REPLACE,
            BackupPlan.plan(here, fromBackup, ConflictChoice.USE_BACKUP).single().action,
        )
    }

    @Test
    fun `a picture arriving where only a track is stored is still a conflict`() {
        val here = existing(ExistingPage(4, mediaFile = null, audioFile = "mine.mp3"))
        val fromBackup = listOf(BackupPage(4, mediaFile = "photo.jpg"))

        assertEquals(1, BackupPlan.conflictCount(here, fromBackup))
        assertEquals(
            PageAction.KEEP,
            BackupPlan.plan(here, fromBackup, ConflictChoice.KEEP_MINE).single().action,
        )
    }

    @Test
    fun `re-importing a backup of a track-only page changes nothing`() {
        val audioOnly = listOf(BackupPage(3, audioFile = "song.mp3", audioTitle = "A song"))
        val after = existing(ExistingPage(3, mediaFile = null, audioFile = "song.mp3"))

        listOf(ConflictChoice.KEEP_MINE, ConflictChoice.USE_BACKUP).forEach { choice ->
            val plan = BackupPlan.plan(after, audioOnly, choice)
            assertEquals(PageAction.UNCHANGED, plan.single().action)
            assertTrue(BackupPlan.filesNeeded(plan).isEmpty())
        }
    }

    @Test
    fun `audio alone is enough to make a page different`() {
        val here = existing(ExistingPage(0, "b0.jpg", audioFile = "old-song.mp3"))
        val withAudio = listOf(BackupPage(0, mediaFile = "b0.jpg", audioFile = "new-song.mp3"))
        assertEquals(PageAction.KEEP, BackupPlan.plan(here, withAudio, ConflictChoice.KEEP_MINE).single().action)
        assertEquals(PageAction.REPLACE, BackupPlan.plan(here, withAudio, ConflictChoice.USE_BACKUP).single().action)
    }
}
