package com.dathaze.pagewall.ui

import kotlin.math.ceil

/**
 * How the page tiles are arranged.
 *
 * Kept separate from the composables and free of Android imports so the arithmetic is unit
 * tested. It matters more than it looks: the tiles are sized from the column width, so the column
 * count decides whether the whole set of screens is visible at once or has to be scrolled.
 */
object GridLayout {

    /** Tile width divided by tile height. Roughly the shape of a phone, because that is what a
     * tile is a picture of. */
    const val TILE_ASPECT = 0.56f

    /**
     * Columns for [pageCount] tiles.
     *
     * Two columns made a five-screen grid three rows tall, which did not fit next to a header and
     * an action bar — the tiles were squeezed until the pictures were unreadable. Three columns
     * put five screens in two rows, which is what fits.
     */
    fun columnsFor(pageCount: Int): Int = when {
        pageCount <= 2 -> pageCount.coerceAtLeast(1)
        pageCount == 3 -> 3
        pageCount == 4 -> 2
        pageCount <= 9 -> 3
        else -> 4
    }

    /** Rows needed for [pageCount] tiles at [columns] across. */
    fun rowsFor(pageCount: Int, columns: Int): Int {
        if (pageCount <= 0 || columns <= 0) return 1
        return ceil(pageCount / columns.toFloat()).toInt().coerceAtLeast(1)
    }
}
