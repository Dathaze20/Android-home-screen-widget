package com.dathaze.pagewall.ui

import com.dathaze.pagewall.data.PageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tile grid has to fit beside a header and an action bar at every supported page count. */
class GridLayoutTest {

    @Test
    fun `five screens fit in two rows, which is what broke before`() {
        val columns = GridLayout.columnsFor(5)
        assertEquals(3, columns)
        assertEquals(2, GridLayout.rowsFor(5, columns))
    }

    @Test
    fun `a few screens get wide tiles rather than a lone column`() {
        assertEquals(1, GridLayout.columnsFor(1))
        assertEquals(2, GridLayout.columnsFor(2))
        assertEquals(3, GridLayout.columnsFor(3))
        assertEquals(2, GridLayout.columnsFor(4))
    }

    @Test
    fun `every supported page count stays within three rows`() {
        (PageStore.MIN_PAGES..PageStore.MAX_PAGES).forEach { count ->
            val rows = GridLayout.rowsFor(count, GridLayout.columnsFor(count))
            assertTrue("$count screens needed $rows rows", rows <= 3)
        }
    }

    @Test
    fun `rows never collapse to zero, whatever it is asked`() {
        assertEquals(1, GridLayout.rowsFor(0, 3))
        assertEquals(1, GridLayout.rowsFor(5, 0))
        assertEquals(1, GridLayout.rowsFor(-1, -1))
    }

    @Test
    fun `columns never exceed what there are tiles for`() {
        (PageStore.MIN_PAGES..PageStore.MAX_PAGES).forEach { count ->
            val columns = GridLayout.columnsFor(count)
            assertTrue("$count screens asked for $columns columns", columns in 1..4)
            assertTrue("$count screens got more columns than tiles", columns <= count)
        }
    }
}
