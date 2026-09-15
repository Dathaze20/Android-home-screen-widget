package com.dathaze.pagewall.wallpaper

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.dathaze.pagewall.audio.PageAudioController
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.widget.PageWidgetProvider
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The live wallpaper that swaps the picture as you swipe between home screen pages.
 *
 * A widget cannot do this job: widgets only paint inside their own cell on the launcher's
 * surface. The wallpaper surface sits underneath the launcher, and [Engine.onOffsetsChanged] is
 * the only callback Android gives an app that reports where the launcher has scrolled to. Once
 * this wallpaper is set, everything below runs with no further interaction.
 */
class PageWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PageEngine()

    inner class PageEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val store = PageStore(this@PageWallpaperService)
        private val cache = PageBitmapCache()
        private val renderer = PageRenderer(cache)
        private val audio = PageAudioController(this@PageWallpaperService)

        private var visible = false
        private var currentPage = 0
        private var outgoingFile: File? = null
        private var fadeStartedAt = 0L
        private var pan = CENTER_PAN
        private var lastReportedOffset = Float.NaN
        private var previewPage = 0

        private val frameRunnable = Runnable { drawFrame() }
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
            audio.stop()
            cache.clear()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                drawFrame()
                if (isPreview) handler.postDelayed(previewRunnable, PREVIEW_INTERVAL_MS)
                playAudioForCurrentPage()
            } else {
                handler.removeCallbacksAndMessages(null)
                // Audio is tied to the home screen being on screen; leaving it stops the track.
                audio.stop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.visible = false
            handler.removeCallbacksAndMessages(null)
            audio.stop()
        }

        /**
         * Where the launcher has scrolled to.
         *
         * [xOffsetStep] is the fraction of the total scroll range one page occupies, so
         * `1 / xOffsetStep + 1` is the launcher's page count and `xOffset / xOffsetStep` is the
         * page index. A launcher with wallpaper scrolling switched off reports a step of 0 and a
         * fixed offset — there is nothing to derive a page from in that case, which is what
         * [PageStore.sawScrollOffsets] tracks so the config screen can say so out loud.
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

            val page = if (xOffsetStep > 0f && xOffsetStep.isFinite()) {
                (xOffset / xOffsetStep).roundToInt()
            } else {
                currentPage
            }.coerceIn(0, store.pageCount - 1)

            if (page != currentPage) {
                switchToPage(page, animate = true, playAudio = true)
            } else if (store.parallaxEnabled) {
                // Redraw during the swipe so the picture drifts with your finger.
                drawFrame()
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
                PageStore.KEY_CURRENT_PAGE, PageStore.KEY_SAW_OFFSETS -> return
                PageStore.KEY_PAGES -> cache.clear()
                PageStore.KEY_PAGE_COUNT -> {
                    currentPage = currentPage.coerceIn(0, store.pageCount - 1)
                }
            }
            outgoingFile = null
            fadeStartedAt = 0L
            drawFrame()
        }

        private fun switchToPage(page: Int, animate: Boolean, playAudio: Boolean) {
            outgoingFile = imageFileFor(currentPage).takeIf { animate && store.crossfadeMillis > 0 }
            currentPage = page
            fadeStartedAt = if (outgoingFile != null) SystemClock.uptimeMillis() else 0L

            store.currentPage = page
            PageWidgetProvider.notifyPageChanged(this@PageWallpaperService, page)

            if (playAudio) playAudioForCurrentPage() else audio.stop()
            drawFrame()
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

        private fun imageFileFor(page: Int): File? = store.fileFor(store.page(page).imageFile)

        private fun drawFrame() {
            handler.removeCallbacks(frameRunnable)
            if (!visible) return

            val holder = surfaceHolder
            if (!holder.surface.isValid) return

            val fadeDuration = store.crossfadeMillis
            val progress = when {
                outgoingFile == null || fadeDuration <= 0 -> 1f
                else -> ((SystemClock.uptimeMillis() - fadeStartedAt).toFloat() / fadeDuration)
                    .coerceIn(0f, 1f)
            }

            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderer.draw(
                        canvas = canvas,
                        current = imageFileFor(currentPage),
                        outgoing = outgoingFile,
                        progress = progress,
                        pan = pan,
                        currentPageLabel = currentPage,
                    )
                }
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }

            if (progress < 1f) {
                handler.postDelayed(frameRunnable, FRAME_INTERVAL_MS)
            } else if (outgoingFile != null) {
                outgoingFile = null
            }
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
