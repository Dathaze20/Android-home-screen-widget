package com.dathaze.pagewall.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Works out which home screen page the launcher is showing, from the scroll offset it reports.
 *
 * Deliberately free of Android imports so the arithmetic can be unit tested on the JVM. Nothing
 * here touches the wallpaper surface; it only turns numbers into a page index.
 *
 * ### Why this does not use xOffsetStep
 *
 * The obvious formula is `xOffset / xOffsetStep`, since a step is the fraction of the scroll
 * range one page occupies. It is also useless on launchers that report a step of 0 — One UI
 * among them — where it degrades to "stay on whatever page you were on", which looks exactly
 * like the wallpaper being broken.
 *
 * So the page count the user set is the source of truth, and the step is only ever used as a
 * hint for guessing that count on launchers that do report one.
 */
object PageMath {

    /** Step values outside this are treated as "the launcher told us nothing useful". */
    private const val MIN_USABLE_STEP = 0.0001f

    /** A calibrated range narrower than this is a mis-tap, not a real range. */
    const val MIN_CALIBRATION_SPAN = 0.02f

    /**
     * The page for [xOffset], given how many pages the user says they have.
     *
     * [calibrationMin]/[calibrationMax] rescale the offset when a launcher only sweeps part of
     * the 0..1 range; pass -1f for both when uncalibrated.
     *
     * With 5 pages and no calibration this maps 0.00, 0.25, 0.50, 0.75 and 1.00 onto pages
     * 0, 1, 2, 3 and 4, and anything between to the nearest of them.
     */
    fun pageFor(
        xOffset: Float,
        pageCount: Int,
        calibrationMin: Float = -1f,
        calibrationMax: Float = -1f,
    ): Int {
        if (pageCount <= 1) return 0
        if (!xOffset.isFinite()) return 0

        val normalized = normalize(xOffset, calibrationMin, calibrationMax)
        val page = (normalized * (pageCount - 1)).roundToInt()
        return page.coerceIn(0, pageCount - 1)
    }

    /**
     * Maps a raw offset onto 0..1 using the calibrated edges when they are set and sane.
     * Without calibration the offset is simply clamped, since launchers already use 0..1.
     */
    fun normalize(xOffset: Float, calibrationMin: Float, calibrationMax: Float): Float {
        val calibrated = calibrationMin >= 0f &&
            calibrationMax >= 0f &&
            abs(calibrationMax - calibrationMin) >= MIN_CALIBRATION_SPAN

        if (!calibrated) return xOffset.coerceIn(0f, 1f)

        val low = minOf(calibrationMin, calibrationMax)
        val high = maxOf(calibrationMin, calibrationMax)
        return ((xOffset - low) / (high - low)).coerceIn(0f, 1f)
    }

    /**
     * The launcher's own page count, derived from [xOffsetStep], or null when it reports a step
     * this cannot be read from. Only a hint: the user's setting always wins.
     */
    fun launcherPageCount(xOffsetStep: Float, maxPages: Int): Int? {
        if (!xOffsetStep.isFinite() || xOffsetStep < MIN_USABLE_STEP) return null
        val pages = (1f / xOffsetStep).roundToInt() + 1
        if (pages < 2 || pages > maxPages) return null
        return pages
    }
}
