package com.dathaze.pagewall.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.dathaze.pagewall.data.PhotoFit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws one frame of the wallpaper: the current page's picture, optionally crossfading from the
 * page just left.
 *
 * Everything here goes through [Drawable], so a still photo and an animated GIF take exactly the
 * same path. Videos do not come through here at all — MediaPlayer renders those straight onto the
 * wallpaper surface, which is a different mode entirely.
 */
class PageRenderer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val blurSrc = Rect()
    private val blurDst = RectF()

    /**
     * Tiny copies of each picture, used as the blurred backdrop.
     *
     * Blurring on a software canvas has no cheap built-in: RenderEffect needs hardware
     * acceleration, which `lockCanvas` does not give. Scaling a picture down to a few dozen
     * pixels and stretching it back out with bilinear filtering costs almost nothing and looks
     * like a heavy gaussian, which is exactly what a backdrop wants.
     */
    private val blurCache = object : LruCache<Drawable, Bitmap>(BLUR_CACHE_ENTRIES) {}

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
        fit: PhotoFit,
    ) {
        canvas.drawColor(Color.BLACK)
        val fade = progress.coerceIn(0f, 1f)

        if (outgoing != null && fade < 1f) {
            drawPicture(canvas, outgoing, pan, alpha = 255, fit = fit)
        }

        if (current != null) {
            drawPicture(canvas, current, pan, alpha = (fade * 255).roundToInt(), fit = fit)
        } else if (outgoing == null || fade >= 1f) {
            drawPlaceholder(canvas, currentPageLabel, fade)
        }
    }

    /**
     * [PhotoFit.FILL_SCREEN] crops the picture to cover the screen; [PhotoFit.FULL_IMAGE] keeps
     * every pixel of it and fills the leftover space with a blurred, darkened copy rather than
     * black bars.
     *
     * A landscape picture on a tall phone cannot be complete, uncropped, undistorted *and* reach
     * all four corners — the shapes simply differ. Full image keeps the picture intact and gives
     * up the corners; fill screen keeps the corners and gives up the edges of the picture.
     */
    private fun drawPicture(canvas: Canvas, drawable: Drawable, pan: Float, alpha: Int, fit: PhotoFit) {
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

        if (fit == PhotoFit.FILL_SCREEN) {
            drawCovering(canvas, drawable, pan, alpha)
            return
        }

        drawBackdrop(canvas, drawable, alpha)

        // The whole picture, centred, at its own aspect ratio. Never stretched, never cropped.
        val scale = min(canvasWidth / intrinsicWidth, canvasHeight / intrinsicHeight)
        val width = intrinsicWidth * scale
        val height = intrinsicHeight * scale
        val left = (canvasWidth - width) / 2f
        val top = (canvasHeight - height) / 2f

        drawable.setBounds(
            left.roundToInt(),
            top.roundToInt(),
            (left + width).roundToInt(),
            (top + height).roundToInt(),
        )
        drawable.alpha = alpha
        drawable.draw(canvas)
    }

    /** Scales the drawable up until it covers the canvas, cropping the overflow. */
    private fun drawCovering(canvas: Canvas, drawable: Drawable, pan: Float, alpha: Int) {
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight

        val scale = max(canvasWidth / intrinsicWidth, canvasHeight / intrinsicHeight)
        val scaledWidth = intrinsicWidth * scale
        val scaledHeight = intrinsicHeight * scale
        // Spare width is where the parallax lives: pan slides the crop across it.
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

    /**
     * Fills the whole canvas with a blurred, darkened copy of the picture, so the space the
     * fitted image does not reach is still part of the same image rather than a black bar.
     */
    private fun drawBackdrop(canvas: Canvas, drawable: Drawable, alpha: Int) {
        val small = blurredCopy(drawable)
        if (small == null) {
            // Animated drawables have no bitmap to shrink, so the backdrop is the picture itself
            // scaled to cover, then darkened. Same effect, without the softness.
            drawCovering(canvas, drawable, CENTER_PAN, alpha)
        } else {
            val scale = max(
                canvas.width.toFloat() / small.width,
                canvas.height.toFloat() / small.height,
            )
            val srcWidth = (canvas.width / scale).roundToInt().coerceIn(1, small.width)
            val srcHeight = (canvas.height / scale).roundToInt().coerceIn(1, small.height)
            val srcLeft = (small.width - srcWidth) / 2
            val srcTop = (small.height - srcHeight) / 2

            blurSrc.set(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight)
            blurDst.set(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            paint.alpha = alpha
            canvas.drawBitmap(small, blurSrc, blurDst, paint)
        }

        // Knock the backdrop back so the sharp picture in front of it reads clearly.
        placeholderPaint.color = Color.BLACK
        placeholderPaint.alpha = (BACKDROP_DIM * (alpha / 255f)).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), placeholderPaint)
    }

    /** A few-dozen-pixel copy of the picture, cached; null for drawables with no bitmap. */
    private fun blurredCopy(drawable: Drawable): Bitmap? {
        blurCache.get(drawable)?.let { return it }

        val source = (drawable as? BitmapDrawable)?.bitmap ?: return null
        if (source.width <= 0 || source.height <= 0 || source.isRecycled) return null

        val height = max(1, BLUR_WIDTH * source.height / source.width)
        val small = runCatching {
            Bitmap.createScaledBitmap(source, BLUR_WIDTH, height, true)
        }.getOrNull() ?: return null

        blurCache.put(drawable, small)
        return small
    }

    fun clearCaches() = blurCache.evictAll()

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

    private companion object {
        /** Width of the shrunken copy. Small enough that stretching it back out reads as a blur. */
        const val BLUR_WIDTH = 32
        const val BLUR_CACHE_ENTRIES = 4
        const val BACKDROP_DIM = 110f
        const val CENTER_PAN = 0.5f
    }
}
