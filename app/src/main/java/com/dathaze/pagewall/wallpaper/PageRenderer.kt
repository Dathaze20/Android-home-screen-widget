package com.dathaze.pagewall.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws one frame of the wallpaper: the current page's picture, optionally crossfading from the
 * page just left, with the crop panned by the scroll offset.
 *
 * Everything here goes through [Drawable], so a still photo and an animated GIF take exactly the
 * same path. Videos do not come through here at all — MediaPlayer renders those straight onto the
 * wallpaper surface, which is a different mode entirely.
 */
class PageRenderer {

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    /**
     * @param outgoing the page being faded out, or null when there is nothing to fade from
     * @param progress 0f..1f through the crossfade; 1f means [current] is fully in
     * @param pan 0f..1f horizontal position within the picture's spare width
     */
    fun draw(
        canvas: Canvas,
        current: Drawable?,
        outgoing: Drawable?,
        progress: Float,
        pan: Float,
        currentPageLabel: Int,
    ) {
        canvas.drawColor(Color.BLACK)
        val fade = progress.coerceIn(0f, 1f)

        if (outgoing != null && fade < 1f) {
            drawCovering(canvas, outgoing, pan, alpha = 255)
        }

        if (current != null) {
            drawCovering(canvas, current, pan, alpha = (fade * 255).roundToInt())
        } else if (outgoing == null || fade >= 1f) {
            drawPlaceholder(canvas, currentPageLabel, fade)
        }
    }

    /**
     * Scales [drawable] to cover the canvas, keeping its aspect ratio and cropping the overflow.
     * Spare width is where the parallax lives: [pan] slides the crop across it.
     */
    private fun drawCovering(canvas: Canvas, drawable: Drawable, pan: Float, alpha: Int) {
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()
        if (canvasWidth <= 0f || canvasHeight <= 0f) return

        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            // A drawable with no natural size (rare) can only be stretched to fit.
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.alpha = alpha
            drawable.draw(canvas)
            return
        }

        val scale = max(canvasWidth / intrinsicWidth, canvasHeight / intrinsicHeight)
        val scaledWidth = intrinsicWidth * scale
        val scaledHeight = intrinsicHeight * scale
        val left = -(scaledWidth - canvasWidth) * pan.coerceIn(0f, 1f)
        val top = -(scaledHeight - canvasHeight) / 2f

        drawable.setBounds(
            left.roundToInt(),
            top.roundToInt(),
            (left + scaledWidth).roundToInt(),
            (top + scaledHeight).roundToInt(),
        )
        drawable.alpha = alpha
        drawable.draw(canvas)
    }

    /** Shown for a page with nothing assigned, so an empty slot reads as empty rather than broken. */
    private fun drawPlaceholder(canvas: Canvas, pageLabel: Int, progress: Float) {
        val shade = 18 + (pageLabel % 4) * 10
        placeholderPaint.color = Color.rgb(shade, shade, shade + 6)
        placeholderPaint.alpha = (progress.coerceIn(0f, 1f) * 255).roundToInt()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), placeholderPaint)

        textPaint.textSize = canvas.width / 18f
        textPaint.alpha = (progress.coerceIn(0f, 1f) * 140).roundToInt()
        canvas.drawText(
            "Page ${pageLabel + 1}",
            canvas.width / 2f,
            canvas.height / 2f,
            textPaint,
        )
    }
}
