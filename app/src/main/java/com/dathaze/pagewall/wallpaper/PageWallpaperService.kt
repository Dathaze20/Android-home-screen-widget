package com.dathaze.pagewall.wallpaper

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.dathaze.pagewall.audio.PageAudioController
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.widget.PageWidgetProvider
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The live wallpaper that swaps what you see as you swipe between home screen pages.
 *
 * A widget cannot do this job: widgets only paint inside their own cell on the launcher's
 * surface. The wallpaper surface sits underneath the launcher, and [Engine.onOffsetsChanged] is
 * the only callback Android gives an app that reports where the launcher has scrolled to. Once
 * this wallpaper is set, everything below runs with no further interaction.
 *
 * The engine has two modes, because a surface can be driven by a canvas or by a video decoder but
 * never both:
 *  - **Canvas mode** for photos and GIFs, with crossfades and parallax.
 *  - **Video mode**, where MediaPlayer owns the surface and the engine stops drawing.
 */
class PageWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PageEngine()

    inner class PageEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val store = PageStore(this@PageWallpaperService)
        private val cache = PageMediaCache()
        private val renderer = PageRenderer()
        private val audio = PageAudioController(this@PageWallpaperService)
        private val video = VideoPageController()

        private var visible = false
        private var currentPage = 0
        private var outgoing: Drawable? = null
        private var fadeStartedAt = 0L
        private var pan = CENTER_PAN
        private var lastReportedOffset = Float.NaN
        private var previewPage = 0
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        /** The GIF currently animating, kept so it can be stopped when the page changes. */
        private var animating: AnimatedImageDrawable? = null

        /** True while MediaPlayer holds the surface; the canvas must not be locked in that state. */
        private var videoMode = false

        private var framePending = false
        private val frameRunnable = Runnable {
            framePending = false
            drawFrame()
        }

        /**
         * Animated drawables need somewhere to send their "next frame is ready" signal. Without a
         * callback an [AnimatedImageDrawable] simply never advances.
         */
        private val drawableCallback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) = requestFrame(0L)

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                handler.postAtTime(what, who, `when`)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                handler.removeCallbacks(what, who)
            }
        }

        private val previewRunnable = object : Runnable {
            override fun run() {
                // The wallpaper picker never scrolls, so cycle the pages to show off the setup.
                val count = store.pageCount
                if (count > 1) {
                    previewPage = (previewPage + 1) % count
                    switchToPage(previewPage, animate = true, playAudio = false)
                }
                handler.postDelayed(this, PREVIEW_INTERVAL_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            store.registerListener(this)
            currentPage = store.currentPage.coerceIn(0, store.pageCount - 1)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            store.unregisterListener(this)
            stopAnimation()
            video.stop()
            audio.stop()
            cache.clear()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                render()
                if (isPreview) handler.postDelayed(previewRunnable, PREVIEW_INTERVAL_MS)
                playAudioForCurrentPage()
            } else {
                handler.removeCallbacksAndMessages(null)
                framePending = false
                stopAnimation()
                // Keep the surface but stop burning battery on frames nobody can see.
                video.pause()
                audio.stop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width
                surfaceHeight = height
                // Decode targets are sized from the surface, so the cache is stale after a resize.
                cache.clear()
                outgoing = null
            }
            render()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            if (!videoMode) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.visible = false
            handler.removeCallbacksAndMessages(null)
            framePending = false
            stopAnimation()
            // The surface is going away, so MediaPlayer must let go of it now, not lazily.
            video.stop()
            videoMode = false
            audio.stop()
        }

        /**
         * Where the launcher has scrolled to.
         *
         * [xOffsetStep] is the fraction of the total scroll range one page occupies, so
         * `xOffset / xOffsetStep` is the page index. A launcher with wallpaper scrolling switched
         * off reports a step of 0 and a fixed offset — there is nothing to derive a page from in
         * that case, which is what [PageStore.sawScrollOffsets] tracks so the settings screen can
         * say so out loud.
         */
        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            if (isPreview) return

            if (!lastReportedOffset.isNaN() && abs(xOffset - lastReportedOffset) > OFFSET_EPSILON) {
                if (!store.sawScrollOffsets) store.sawScrollOffsets = true
            }
            lastReportedOffset = xOffset

            pan = if (store.parallaxEnabled) xOffset.coerceIn(0f, 1f) else CENTER_PAN

            // One page occupies xOffsetStep of the scroll range, so the launcher's page count
            // falls out of it. This is the only moment any app is told how many home screens
            // exist, which is why the count cannot be known before the wallpaper is applied.
            if (xOffsetStep > 0f && xOffsetStep.isFinite()) {
                val launcherPages = ((1f / xOffsetStep).roundToInt() + 1)
                    .coerceIn(PageStore.MIN_PAGES, PageStore.MAX_PAGES)
                if (launcherPages != store.detectedPageCount) {
                    store.detectedPageCount = launcherPages
                }
            }

            val page = if (xOffsetStep > 0f && xOffsetStep.isFinite()) {
                (xOffset / xOffsetStep).roundToInt()
            } else {
                currentPage
            }.coerceIn(0, store.pageCount - 1)

            if (page != currentPage) {
                switchToPage(page, animate = true, playAudio = true)
            } else if (store.parallaxEnabled && !videoMode) {
                // Redraw during the swipe so the picture drifts with your finger.
                requestFrame(0L)
            }
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean,
        ): Bundle? {
            if (action == COMMAND_TAP) {
                // Launchers send this on a double tap on empty space: a shortcut into the setup
                // screen without hunting for the app icon.
                PageWidgetProvider.launchConfig(this@PageWallpaperService, currentPage)
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            when (key) {
                // Written by this engine; reacting to it would loop.
                PageStore.KEY_CURRENT_PAGE,
                PageStore.KEY_SAW_OFFSETS,
                PageStore.KEY_DETECTED_PAGES -> return
                PageStore.KEY_PAGES -> {
                    stopAnimation()
                    cache.clear()
                    // The media on this page may have been replaced under the player's feet.
                    video.stop()
                    videoMode = false
                }
                PageStore.KEY_PAGE_COUNT -> currentPage = currentPage.coerceIn(0, store.pageCount - 1)
                PageStore.KEY_MOTION, PageStore.KEY_VIDEO_SOUND -> {
                    video.stop()
                    videoMode = false
                }
            }
            outgoing = null
            fadeStartedAt = 0L
            render()
        }

        private fun switchToPage(page: Int, animate: Boolean, playAudio: Boolean) {
            val next = store.page(page)
            // A video takes over the whole surface, so fading into or out of one is not possible.
            val canFade = animate &&
                store.crossfadeMillis > 0 &&
                !videoMode &&
                !(next.mediaKind == MediaKind.VIDEO && store.motionEnabled)

            outgoing = if (canFade) canvasDrawableFor(currentPage) else null
            currentPage = page
            fadeStartedAt = if (outgoing != null) SystemClock.uptimeMillis() else 0L

            store.currentPage = page
            PageWidgetProvider.notifyPageChanged(this@PageWallpaperService, page)

            if (playAudio) playAudioForCurrentPage() else audio.stop()
            render()
        }

        /** Picks the mode the current page needs, then hands off to the video player or the canvas. */
        private fun render() {
            if (!visible) return

            val config = store.page(currentPage)
            val videoFile = if (config.mediaKind == MediaKind.VIDEO && store.motionEnabled) {
                store.fileFor(config.mediaFile)
            } else {
                null
            }

            if (videoFile != null) {
                enterVideoMode(videoFile)
            } else {
                exitVideoMode()
                drawFrame()
            }
        }

        private fun enterVideoMode(file: File) {
            handler.removeCallbacks(frameRunnable)
            framePending = false
            stopAnimation()
            videoMode = true
            video.ensurePlaying(file, surfaceHolder, store.videoSoundEnabled)
        }

        private fun exitVideoMode() {
            if (!videoMode) return
            video.stop()
            videoMode = false
        }

        private fun playAudioForCurrentPage() {
            if (!store.audioEnabled || !visible) {
                audio.stop()
                return
            }
            val track = store.fileFor(store.page(currentPage).audioFile)
            if (track == null) {
                audio.stop()
            } else {
                audio.play(track, store.audioVolume, store.audioLooping)
            }
        }

        /**
         * The drawable the canvas should show for [page]: the media itself for a photo or GIF, and
         * the saved poster frame for a video (used when motion is switched off).
         */
        private fun canvasDrawableFor(page: Int): Drawable? {
            val config = store.page(page)
            val name = if (config.mediaKind == MediaKind.VIDEO) config.posterFile else config.mediaFile
            val file = store.fileFor(name) ?: return null
            return cache.get(file, surfaceWidth, surfaceHeight, drawableCallback)
        }

        /** Coalesced redraw request, so a burst of invalidations still costs one frame. */
        private fun requestFrame(delayMillis: Long) {
            if (!visible || videoMode || framePending) return
            framePending = true
            handler.postDelayed(frameRunnable, delayMillis)
        }

        private fun drawFrame() {
            if (!visible || videoMode) return

            val holder = surfaceHolder
            if (!holder.surface.isValid) return

            val current = canvasDrawableFor(currentPage)
            syncAnimation(current)

            val fadeDuration = store.crossfadeMillis
            val progress = when {
                outgoing == null || fadeDuration <= 0 -> 1f
                else -> ((SystemClock.uptimeMillis() - fadeStartedAt).toFloat() / fadeDuration)
                    .coerceIn(0f, 1f)
            }

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderer.draw(
                        canvas = canvas,
                        current = current,
                        outgoing = outgoing,
                        progress = progress,
                        pan = pan,
                        currentPageLabel = currentPage,
                    )
                }
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }

            if (progress < 1f) {
                requestFrame(FRAME_INTERVAL_MS)
            } else {
                if (outgoing != null) outgoing = null
                // A running GIF keeps asking for frames through the drawable callback, but a
                // steady tick keeps it going even if a callback is missed. A GIF held on its
                // first frame is not running, and must not spin the loop.
                if (animating?.isRunning == true) requestFrame(FRAME_INTERVAL_MS)
            }
        }

        /** Starts the current page's animation and stops whatever was animating before it. */
        private fun syncAnimation(current: Drawable?) {
            val next = current as? AnimatedImageDrawable
            if (animating !== next) stopAnimation()
            if (next == null) return
            animating = next
            if (store.motionEnabled) {
                if (!next.isRunning) next.start()
            } else if (next.isRunning) {
                next.stop()
            }
        }

        private fun stopAnimation() {
            animating?.let { runCatching { it.stop() } }
            animating = null
        }
    }

    private companion object {
        const val COMMAND_TAP = "android.wallpaper.tap"
        const val FRAME_INTERVAL_MS = 16L
        const val PREVIEW_INTERVAL_MS = 2_500L
        const val OFFSET_EPSILON = 0.001f
        const val CENTER_PAN = 0.5f
    }
}
