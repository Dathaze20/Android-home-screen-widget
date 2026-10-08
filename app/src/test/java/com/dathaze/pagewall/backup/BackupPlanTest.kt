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
    fun `audio alone is enough to make a page different`() {
        val here = existing(ExistingPage(0, "b0.jpg", audioFile = "old-song.mp3"))
        val withAudio = listOf(BackupPage(0, mediaFile = "b0.jpg", audioFile = "new-song.mp3"))
        assertEquals(PageAction.KEEP, BackupPlan.plan(here, withAudio, ConflictChoice.KEEP_MINE).single().action)
        assertEquals(PageAction.REPLACE, BackupPlan.plan(here, withAudio, ConflictChoice.USE_BACKUP).single().action)
    }
}
