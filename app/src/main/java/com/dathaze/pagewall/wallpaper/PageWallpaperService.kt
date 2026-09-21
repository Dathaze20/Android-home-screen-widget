package com.dathaze.pagewall.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.dathaze.pagewall.audio.PageAudioController
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.DetectionMode
import com.dathaze.pagewall.data.GestureTracker
import com.dathaze.pagewall.data.SyncPolicy
import com.dathaze.pagewall.data.PageMath
import com.dathaze.pagewall.data.SwipeDirection
import com.dathaze.pagewall.data.SwipeMath
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.widget.PageWidgetProvider
import java.io.File
import kotlin.math.abs

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
        private var offsetEvents = 0
        private var lastDiagnosticsWrite = 0L
        private var minOffsetSeen = -1f
        private var maxOffsetSeen = -1f

        // Touch fallback, for launchers whose offset never moves.
        private val gesture = GestureTracker()
        private var rawTouchEvents = 0
        private var recognisedSwipes = 0
        private var lastSwipeName = ""
        private var lastTouchDiagnosticsWrite = 0L

        /**
         * Set when the display turned off while the wallpaper was hidden, so that unlocking is
         * not mistaken for returning to the launcher from an app.
         */
        private var screenWasOff = false

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    screenWasOff = true
                    // A press held when the screen went off must not complete later.
                    gesture.reset()
                }
            }
        }
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
            // The one call that asks the system to deliver scroll positions to this wallpaper.
            // The documentation says notifications are on by default; in practice several
            // launchers send nothing unless a wallpaper asks for them explicitly, which leaves
            // every page showing the same picture.
            setOffsetNotificationsEnabled(true)
            store.registerListener(this)
            // Seed the decode target from the display so a first frame arriving before
            // onSurfaceChanged does not decode at full resolution for nothing.
            resources.displayMetrics.let {
                surfaceWidth = it.widthPixels
                surfaceHeight = it.heightPixels
            }
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

            // A recreated engine cannot recover the launcher's page from a frozen offset, and the
            // stored page may be arbitrarily stale, so touch tracking starts from the configured
            // home screen instead of trusting it.
            currentPage = SyncPolicy.pageOnEngineStart(
                mode = currentDetectionMode(),
                defaultHomePage = store.defaultHomePage,
                storedPage = store.currentPage,
                pageCount = store.pageCount,
            )
            store.currentPage = currentPage
            store.displayedPage = currentPage
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacksAndMessages(null)
            store.unregisterListener(this)
            runCatching { unregisterReceiver(screenReceiver) }
            gesture.reset()
            stopAnimation()
            renderer.clearCaches()
            video.stop()
            audio.stop()
            cache.clear()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                if (!isPreview) syncOnBecomingVisible()
                render()
                if (isPreview) handler.postDelayed(previewRunnable, PREVIEW_INTERVAL_MS)
                playAudioForCurrentPage()
            } else {
                handler.removeCallbacksAndMessages(null)
                framePending = false
                // A press in progress must never complete against a release that arrives after
                // the wallpaper has been away.
                gesture.reset()
                stopAnimation()
                // Keep the surface but stop burning battery on frames nobody can see.
                video.pause()
                audio.stop()
            }
        }

        /**
         * Catches up with a page change that happened while the wallpaper could not see it.
         *
         * Returning to the launcher from an app can land on the launcher's own home page without
         * any swipe the wallpaper could observe. Android offers no non-invasive way to tell the
         * Home button from Back, so this is driven by a setting rather than a guess, and skipped
         * entirely when the display merely turned off and on.
         */
        private fun syncOnBecomingVisible() {
            val wasOff = screenWasOff
            screenWasOff = false

            val target = SyncPolicy.pageOnVisible(
                mode = currentDetectionMode(),
                syncOnReturnHome = store.syncOnReturnHome,
                screenWasOff = wasOff,
                defaultHomePage = store.defaultHomePage,
                currentPage = currentPage,
                pageCount = store.pageCount,
            )
            if (target != currentPage) {
                // No crossfade: the correct picture should already be there as the launcher appears.
                switchToPage(target, animate = false, playAudio = false)
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
            gesture.reset()
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

            if (xOffset.isFinite()) {
                minOffsetSeen = if (minOffsetSeen < 0f) xOffset else minOf(minOffsetSeen, xOffset)
                maxOffsetSeen = if (maxOffsetSeen < 0f) xOffset else maxOf(maxOffsetSeen, xOffset)
            }

            pan = if (store.parallaxEnabled) {
                PageMath.normalize(xOffset, store.calibrationMin, store.calibrationMax)
            } else {
                CENTER_PAN
            }

            // Only a hint, and only from launchers that report a usable step. The page below is
            // worked out from the user's page count either way, because a launcher reporting no
            // step used to leave the page frozen and every home screen showing one picture.
            PageMath.launcherPageCount(xOffsetStep, PageStore.MAX_PAGES)?.let { launcherPages ->
                if (launcherPages != store.detectedPageCount) {
                    store.detectedPageCount = launcherPages
                }
            }

            val page = PageMath.pageFor(
                xOffset = xOffset,
                pageCount = store.pageCount,
                calibrationMin = store.calibrationMin,
                calibrationMax = store.calibrationMax,
            )

            // Offsets arrive many times per swipe, so this is throttled rather than written per
            // event. It is what lets the settings screen say whether the launcher reports
            // anything, over what range, and which page that works out to.
            offsetEvents++
            val now = SystemClock.uptimeMillis()
            if (now - lastDiagnosticsWrite > DIAGNOSTICS_INTERVAL_MS) {
                lastDiagnosticsWrite = now
                store.recordOffsets(
                    count = offsetEvents,
                    offset = xOffset,
                    step = xOffsetStep,
                    minSeen = minOffsetSeen,
                    maxSeen = maxOffsetSeen,
                    computedPage = page,
                )
            }

            if (currentDetectionMode() != DetectionMode.OFFSET) {
                // Touch is driving the page; following the offset too would fight it.
                store.activeDetectionMode = DetectionMode.TOUCH.name
                return
            }
            store.activeDetectionMode = DetectionMode.OFFSET.name

            if (page != currentPage) {
                switchToPage(page, animate = true, playAudio = true)
            } else if (store.parallaxEnabled && !videoMode) {
                // Redraw during the swipe so the picture drifts with your finger.
                requestFrame(0L)
            }
        }

        /**
         * Raw touch events, enabled in [onCreate] via setTouchEventsEnabled.
         *
         * This is the fallback for One UI and any other launcher that reports a fixed wallpaper
         * offset: the page cannot be read from the offset, so the horizontal flick that changed
         * the page is used to step it directly. Offsets stay the preferred method — this only
         * runs when [SwipeMath.detectionMode] says the offsets are not moving.
         *
         * Nothing is consumed here. A wallpaper only observes these events; the launcher has
         * already handled the gesture by the time it forwards them.
         */
        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (isPreview) return

            // Counted for every event, whatever comes of it: a launcher forwarding touches that
            // never amount to a swipe has to look different from one forwarding nothing at all.
            rawTouchEvents++

            val mode = currentDetectionMode()
            if (mode != DetectionMode.TOUCH) {
                gesture.reset()
                recordTouchDiagnostics(mode, force = false)
                return
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    gesture.onDown(event.x, event.y, SystemClock.uptimeMillis())

                // A pinch or a two-finger launcher gesture is not a page swipe.
                MotionEvent.ACTION_POINTER_DOWN -> gesture.onExtraPointer()

                MotionEvent.ACTION_UP -> {
                    val direction = gesture.onUp(
                        x = event.x,
                        y = event.y,
                        timeMs = SystemClock.uptimeMillis(),
                        screenWidth = surfaceWidth,
                    )
                    if (direction != SwipeDirection.NONE) {
                        recognisedSwipes++
                        lastSwipeName = direction.name
                        val page = SwipeMath.nextPage(currentPage, direction, store.pageCount)
                        if (page != currentPage) {
                            switchToPage(page, animate = true, playAudio = true)
                        }
                        recordTouchDiagnostics(mode, force = true)
                        return
                    }
                }

                MotionEvent.ACTION_CANCEL -> gesture.reset()
            }

            recordTouchDiagnostics(mode, force = false)
        }

        /**
         * Persists the touch counters, throttled.
         *
         * Writing on every event would hammer SharedPreferences during a swipe, but writing only
         * when a swipe is recognised left the panel reading zero on a launcher that forwards
         * touches which never qualify — the exact case worth being able to see.
         */
        private fun recordTouchDiagnostics(mode: DetectionMode, force: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastTouchDiagnosticsWrite <= DIAGNOSTICS_INTERVAL_MS) return
            lastTouchDiagnosticsWrite = now
            store.recordTouch(
                rawEvents = rawTouchEvents,
                swipes = recognisedSwipes,
                lastSwipe = lastSwipeName,
                mode = mode,
                displayedPage = currentPage,
            )
        }

        /** Offsets when they move, touch when they do not, or whatever the user forced. */
        private fun currentDetectionMode(): DetectionMode = SwipeMath.detectionMode(
            setting = store.touchCompatibility,
            observedMinOffset = minOffsetSeen,
            observedMaxOffset = maxOffsetSeen,
        )

        private fun recordTouchDiagnostics(mode: DetectionMode) {
            val now = SystemClock.uptimeMillis()
            if (now - lastTouchDiagnosticsWrite <= DIAGNOSTICS_INTERVAL_MS) return
            lastTouchDiagnosticsWrite = now
            store.recordTouch(touchEvents, store.lastSwipe, mode)
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
                PageStore.KEY_DETECTED_PAGES,
                PageStore.KEY_OFFSET_EVENTS,
                PageStore.KEY_LAST_OFFSET,
                PageStore.KEY_LAST_STEP,
                PageStore.KEY_MIN_SEEN,
                PageStore.KEY_MAX_SEEN,
                PageStore.KEY_COMPUTED_PAGE,
                PageStore.KEY_TOUCH_RAW,
                PageStore.KEY_SWIPES,
                PageStore.KEY_DISPLAYED_PAGE,
                PageStore.KEY_SYNC_PAGE -> return
                PageStore.KEY_PAGES -> {
                    stopAnimation()
                    cache.clear()
                    renderer.clearCaches()
                    // The media on this page may have been replaced under the player's feet.
                    video.stop()
                    videoMode = false
                }
                PageStore.KEY_PAGE_COUNT,
                PageStore.KEY_CALIBRATION_MIN,
                PageStore.KEY_CALIBRATION_MAX -> {
                    // Under touch tracking the offset is a frozen constant, so recomputing from
                    // it would teleport to whatever that constant maps to — the middle page, on a
                    // launcher reporting 0.5. The tracked page is kept and merely clamped.
                    currentPage = SyncPolicy.pageOnPageCountChange(
                        mode = currentDetectionMode(),
                        currentPage = currentPage,
                        pageCount = store.pageCount,
                        lastOffset = if (lastReportedOffset.isNaN()) -1f else lastReportedOffset,
                        calibrationMin = store.calibrationMin,
                        calibrationMax = store.calibrationMax,
                    )
                    store.currentPage = currentPage
                }

                // The escape hatch: the app says which screen the launcher is really on.
                PageStore.KEY_SYNC_NONCE -> {
                    val target = store.manualSyncPage.coerceIn(0, store.pageCount - 1)
                    if (target != currentPage) {
                        switchToPage(target, animate = false, playAudio = true)
                    }
                    return
                }
                PageStore.KEY_PHOTO_FIT -> renderer.clearCaches()
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
            // animate = false is a correction rather than a navigation: the launcher is already
            // showing a different screen, so the picture should catch up instantly.
            // A video takes over the whole surface, so fading into or out of one is not possible.
            val canFade = animate &&
                store.crossfadeMillis > 0 &&
                !videoMode &&
                !(next.mediaKind == MediaKind.VIDEO && store.motionEnabled)

            outgoing = if (canFade) canvasDrawableFor(currentPage) else null
            currentPage = page
            fadeStartedAt = if (outgoing != null) SystemClock.uptimeMillis() else 0L

            // The picker's preview cycles pages to show the setup off. Persisting that would
            // leave the real wallpaper starting on whatever page the preview happened to stop on.
            if (!isPreview) {
                store.currentPage = page
                store.displayedPage = page
                PageWidgetProvider.notifyPageChanged(this@PageWallpaperService, page)
            }

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
                    // Never let one unusual file kill the engine. An exception escaping here
                    // takes the whole wallpaper service down, and the system restarts it into
                    // the same failure, so the home screen stays black until the app is
                    // reinstalled. A logged bad frame is always the better outcome.
                    runCatching {
                        renderer.draw(
                            canvas = canvas,
                            current = current,
                            outgoing = outgoing,
                            progress = progress,
                            pan = pan,
                            currentPageLabel = currentPage,
                            fit = store.photoFit,
                        )
                    }.onFailure { Log.w(TAG, "Could not draw page $currentPage", it) }
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
        const val TAG = "PageWallpaper"
        const val COMMAND_TAP = "android.wallpaper.tap"
        const val FRAME_INTERVAL_MS = 16L
        const val PREVIEW_INTERVAL_MS = 2_500L
        const val OFFSET_EPSILON = 0.001f
        const val DIAGNOSTICS_INTERVAL_MS = 400L
        const val CENTER_PAN = 0.5f
    }
}
