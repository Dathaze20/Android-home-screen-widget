package com.dathaze.pagewall.backup

import com.dathaze.pagewall.data.PageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSettingsTest {

    @Test
    fun `every type survives the round trip as itself`() {
        listOf(true, false, 0, 350, -1, 0.5f, 1.0f, 0f, "FULL_IMAGE", "", 42L).forEach { value ->
            val encoded = BackupSettings.encode(value)!!
            assertEquals("round trip failed for $value", value, BackupSettings.decode(encoded))
        }
    }

    @Test
    fun `a float is written so it reads back the same in any locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            // A locale where the decimal separator is a comma. "%f" formatting here would
            // produce 0,5 and the backup would not restore on an English phone.
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val encoded = BackupSettings.encode(0.5f)!!
            assertTrue("locale leaked into the value: $encoded", encoded.contains('.'))
            assertEquals(0.5f, BackupSettings.decode(encoded))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `nonsense decodes to nothing rather than a wrong value`() {
        listOf("", "x", "b:", "b:yes", "i:nope", "f:", "q:1", "350", "i350")
            .forEach { assertNull("accepted: $it", BackupSettings.decode(it)) }
    }

    @Test
    fun `measurements are not settings and are never carried`() {
        val diagnostics = listOf(
            PageStore.KEY_OFFSET_EVENTS, PageStore.KEY_LAST_OFFSET, PageStore.KEY_MIN_SEEN,
            PageStore.KEY_MAX_SEEN, PageStore.KEY_COMPUTED_PAGE, PageStore.KEY_TOUCH_RAW,
            PageStore.KEY_SWIPES, PageStore.KEY_DISPLAYED_PAGE, PageStore.KEY_DETECTION_MODE,
            PageStore.KEY_CURRENT_PAGE, PageStore.KEY_SAW_OFFSETS, PageStore.KEY_HOME_KEYS,
            PageStore.KEY_DETECTED_PAGES, PageStore.KEY_SYNC_NONCE, PageStore.KEY_SYNC_PAGE,
            PageStore.KEY_LAST_SWIPE, PageStore.KEY_TOUCH_EVENTS, PageStore.KEY_LAST_STEP,
        )
        diagnostics.forEach {
            assertFalse("$it is a reading, not a choice", it in BackupSettings.KEYS)
        }
    }

    @Test
    fun `page assignments never travel as a settings blob`() {
        // They go as structure so a restore can merge page by page. As a blob the only possible
        // restore would be to overwrite everything.
        assertFalse(PageStore.KEY_PAGES in BackupSettings.KEYS)
    }

    @Test
    fun `a hand-edited backup cannot set keys that are not on the list`() {
        val hostile = mapOf(
            PageStore.KEY_CROSSFADE to "i:200",
            PageStore.KEY_PAGES to "s:[]",
            PageStore.KEY_CURRENT_PAGE to "i:9",
            "something_invented" to "s:x",
        )
        val clean = BackupSettings.sanitise(hostile)
        assertEquals(mapOf<String, Any>(PageStore.KEY_CROSSFADE to 200), clean)
    }

    @Test
    fun `a value that will not decode is dropped rather than restored as junk`() {
        val broken = mapOf(PageStore.KEY_CROSSFADE to "i:banana", PageStore.KEY_PARALLAX to "b:true")
        assertEquals(mapOf<String, Any>(PageStore.KEY_PARALLAX to true), BackupSettings.sanitise(broken))
    }
}
