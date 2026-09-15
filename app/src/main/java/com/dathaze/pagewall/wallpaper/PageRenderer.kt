package com.dathaze.pagewall.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File

/**
 * Draws one frame of the wallpaper: the current page's picture, optionally crossfading from the
 * page the user just left, with the crop panned by the scroll offset.
 */
class PageRenderer(private val cache: PageBitmapCache) {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        alpha = 160
    }
    private val src = Rect()
    private val dst = RectF()

    /**
     * @param outgoing the page being faded out, or null when there is nothing to fade from
     * @param progress 0f..1f through the crossfade; 1f means [current] is fully in
     * @param pan 0f..1f horizontal position within the picture's spare width
     */
    fun draw(
        canvas: Canvas,
        current: File?,
        outgoing: File?,
        progress: Float,
        pan: Float,
        currentPageLabel: Int,
    ) {
        canvas.drawColor(Color.BLACK)

        val currentBitmap = current?.let { cache.get(it) }
        val outgoingBitmap = outgoing?.let { cache.get(it) }

        if (outgoingBitmap != null && progress < 1f) {
            bitmapPaint.alpha = 255
            drawCovering(canvas, outgoingBitmap, pan)
        }

        if (currentBitmap != null) {
            bitmapPaint.alpha = (progress.coerceIn(0f, 1f) * 255).toInt()
            drawCovering(canvas, currentBitmap, pan)
        } else if (outgoingBitmap == null || progress >= 1f) {
            drawPlaceholder(canvas, currentPageLabel, progress)
        }
    }

    /** Center-crops [bitmap] to fill the canvas, sliding the crop horizontally by [pan]. */
    private fun drawCovering(canvas: Canvas, bitmap: Bitmap, pan: Float) {
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()
        if (canvasWidth <= 0f || canvasHeight <= 0f) return

        val canvasAspect = canvasWidth / canvasHeight
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (bitmapAspect > canvasAspect) {
            // Wider than the screen: crop the sides, and use the slack for the parallax pan.
            val srcWidth = (bitmap.height * canvasAspect).toInt().coerceAtMost(bitmap.width)
            val slack = bitmap.width - srcWidth
            val left = (slack * pan.coerceIn(0f, 1f)).toInt()
            src.set(left, 0, left + srcWidth, bitmap.height)
        } else {
            // Taller than the screen: crop top and bottom evenly, no horizontal slack to pan.
            val srcHeight = (bitmap.width / canvasAspect).toInt().coerceAtMost(bitmap.height)
            val top = (bitmap.height - srcHeight) / 2
            src.set(0, top, bitmap.width, top + srcHeight)
        }

        dst.set(0f, 0f, canvasWidth, canvasHeight)
        canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
    }

    /** Shown for a page with no picture yet, so an empty slot is obvious rather than just black. */
    private fun drawPlaceholder(canvas: Canvas, pageLabel: Int, progress: Float) {
        val shade = 18 + (pageLabel % 4) * 10
        placeholderPaint.color = Color.rgb(shade, shade, shade + 6)
        placeholderPaint.alpha = (progress.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), placeholderPaint)

        textPaint.textSize = canvas.width / 18f
        textPaint.alpha = (progress.coerceIn(0f, 1f) * 140).toInt()
        canvas.drawText(
            "Page ${pageLabel + 1}",
            canvas.width / 2f,
            canvas.height / 2f,
            textPaint,
        )
    }
}
