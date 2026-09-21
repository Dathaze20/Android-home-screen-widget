package com.dathaze.pagewall.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dathaze.pagewall.data.ImportResult
import com.dathaze.pagewall.data.MediaImporter
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.DetectionMode
import com.dathaze.pagewall.data.PageAssignment
import com.dathaze.pagewall.data.PhotoFit
import com.dathaze.pagewall.data.TouchCompatibility
import com.dathaze.pagewall.data.PageConfig
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.wallpaper.PageWallpaperService
import com.dathaze.pagewall.widget.PageWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** UI state for the settings screen. */
data class ConfigUiState(
    val pages: List<PageConfig> = emptyList(),
    val pageCount: Int = PageStore.DEFAULT_PAGE_COUNT,
    /** What the launcher reported, or 0 if it has not been seen yet. */
    val detectedPageCount: Int = 0,
    /** True when [pageCount] came from the launcher rather than a guess or a manual override. */
    val pageCountIsDetected: Boolean = false,
    val crossfadeMillis: Int = PageStore.DEFAULT_CROSSFADE,
    val parallaxEnabled: Boolean = true,
    val motionEnabled: Boolean = true,
    val videoSoundEnabled: Boolean = false,
    val audioEnabled: Boolean = false,
    val audioLooping: Boolean = false,
    val audioVolume: Float = 0.5f,
    val wallpaperActive: Boolean = false,
    val scrollingDetected: Boolean = false,
    /** How many scroll reports the launcher has sent the wallpaper. 0 means none, ever. */
    val offsetEventCount: Int = 0,
    /** Last horizontal offset reported, or -1 if none. */
    val lastOffset: Float = -1f,
    /** Last scroll step reported, or -1 if none. 0 means "nothing to scroll". */
    val lastOffsetStep: Float = -1f,
    /** Narrowest and widest offsets ever reported. Equal means the launcher never moves. */
    val observedMinOffset: Float = -1f,
    val observedMaxOffset: Float = -1f,
    /** The page the engine last worked out from an offset, or -1 before any arrived. */
    val computedPage: Int = -1,
    val calibrationMin: Float = -1f,
    val calibrationMax: Float = -1f,
    val isCalibrated: Boolean = false,
    /** Offset when the launcher moves it, touch when it does not. */
    val detectionMode: String = DetectionMode.OFFSET.name,
    val touchCompatibility: TouchCompatibility = TouchCompatibility.AUTO,
    val touchEventCount: Int = 0,
    val lastSwipe: String = "",
    val photoFit: PhotoFit = PhotoFit.FULL_IMAGE,
    val busy: Boolean = false,
    /** Set when an import failed, e.g. a video over the size limit. Cleared once shown. */
    val errorMessage: String? = null,
    /** Neutral confirmation of what an action actually did. Cleared once shown. */
    val noticeMessage: String? = null,
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PageStore(application)

    var uiState by mutableStateOf(ConfigUiState())
        private set

    /** Display size, used to decide how large imported photos need to be. */
    private var screenWidth = 1080
    private var screenHeight = 2400

    init {
        refresh()
    }

    fun setScreenSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            screenWidth = width
            screenHeight = height
        }
    }

    fun refresh() {
        uiState = uiState.copy(
            pages = store.pages(),
            pageCount = store.pageCount,
            detectedPageCount = store.detectedPageCount,
            pageCountIsDetected = store.pageCountIsDetected,
            crossfadeMillis = store.crossfadeMillis,
            parallaxEnabled = store.parallaxEnabled,
            motionEnabled = store.motionEnabled,
            videoSoundEnabled = store.videoSoundEnabled,
            audioEnabled = store.audioEnabled,
            audioLooping = store.audioLooping,
            audioVolume = store.audioVolume,
            wallpaperActive = isWallpaperActive(),
            scrollingDetected = store.sawScrollOffsets,
            offsetEventCount = store.offsetEventCount,
            lastOffset = store.lastOffset,
            lastOffsetStep = store.lastOffsetStep,
            observedMinOffset = store.observedMinOffset,
            observedMaxOffset = store.observedMaxOffset,
            computedPage = store.lastComputedPage,
            calibrationMin = store.calibrationMin,
            calibrationMax = store.calibrationMax,
            isCalibrated = store.isCalibrated,
            detectionMode = store.activeDetectionMode,
            touchCompatibility = store.touchCompatibility,
            touchEventCount = store.touchEventCount,
            lastSwipe = store.lastSwipe,
            photoFit = store.photoFit,
        )
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = null, noticeMessage = null)
    }

    /** Imports a photo, GIF or video and assigns it to [pageIndex]. */
    fun assignMedia(pageIndex: Int, uri: Uri) {
        uiState = uiState.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaImporter.importMedia(
                    context = getApplication(),
                    store = store,
                    uri = uri,
                    pageIndex = pageIndex,
                    targetWidth = screenWidth,
                    targetHeight = screenHeight,
                )
            }
            when (result) {
                is ImportResult.Success ->
                    store.setMedia(pageIndex, result.fileName, result.kind, result.posterFile)
                is ImportResult.Failure ->
                    uiState = uiState.copy(errorMessage = result.message)
            }
            uiState = uiState.copy(busy = false)
            afterChange()
        }
    }

    fun assignAudio(pageIndex: Int, uri: Uri) {
        uiState = uiState.copy(busy = true, errorMessage = null)
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                MediaImporter.importAudio(getApplication(), store, uri, pageIndex)
            }
            if (imported != null) {
                store.setAudio(pageIndex, imported.first, imported.second)
            } else {
                uiState = uiState.copy(errorMessage = "Could not read that track")
            }
            uiState = uiState.copy(busy = false)
            afterChange()
        }
    }

    fun removeMedia(pageIndex: Int) {
        store.setMedia(pageIndex, null, MediaKind.IMAGE, null)
        afterChange()
    }

    fun removeAudio(pageIndex: Int) {
        store.setAudio(pageIndex, null, null)
        afterChange()
    }

    fun clearPage(pageIndex: Int) {
        store.clearPage(pageIndex)
        afterChange()
    }

    /** Manual correction, used only when the launcher's own count is wrong. */
    fun setPageCount(count: Int) {
        // Pages outside the new count keep their files until explicitly cleared, so dialling the
        // count down and back up again does not lose a photo.
        store.pageCountOverride = count
        afterChange()
    }

    /** Drops a manual correction and goes back to what the launcher reports. */
    fun resetPageCount() {
        store.pageCountOverride = 0
        afterChange()
    }

    /**
     * Fills pages from a multi-select, in the order they were picked.
     *
     * This is the one-shot path: choose several photos, they land on page 1, 2, 3 and so on.
     * Extra picks beyond the page count are ignored rather than silently dropped onto nothing.
     */
    fun fillPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uiState = uiState.copy(busy = true, errorMessage = null, noticeMessage = null)
        viewModelScope.launch {
            val pageCount = store.pageCount
            val usable = uris.take(pageCount)

            val outcome = withContext(Dispatchers.IO) {
                val assignments = mutableListOf<PageAssignment>()
                val problems = mutableListOf<String>()
                usable.forEachIndexed { pageIndex, uri ->
                    when (
                        val result = MediaImporter.importMedia(
                            context = getApplication(),
                            store = store,
                            uri = uri,
                            pageIndex = pageIndex,
                            targetWidth = screenWidth,
                            targetHeight = screenHeight,
                        )
                    ) {
                        is ImportResult.Success -> assignments += PageAssignment(
                            index = pageIndex,
                            fileName = result.fileName,
                            kind = result.kind,
                            posterFile = result.posterFile,
                        )

                        is ImportResult.Failure -> problems += result.message
                    }
                }
                // One write for the whole batch, so a failure part-way cannot strand the rest.
                store.setMediaBatch(assignments)
                assignments.size to problems
            }

            val (placed, problems) = outcome
            uiState = uiState.copy(
                busy = false,
                errorMessage = problems.firstOrNull(),
                // Say plainly what landed. A picker that hands back fewer photos than were
                // tapped used to look like the app losing them.
                noticeMessage = when {
                    placed == 0 -> null
                    placed < pageCount ->
                        "Set $placed of $pageCount pages. Tap an empty page to add one."

                    else -> "All $placed pages set."
                },
            )
            afterChange()
        }
    }

    fun setCrossfade(millis: Int) {
        store.crossfadeMillis = millis
        afterChange()
    }

    fun setParallax(enabled: Boolean) {
        store.parallaxEnabled = enabled
        afterChange()
    }

    fun setPhotoFit(fit: PhotoFit) {
        store.photoFit = fit
        afterChange()
    }

    fun setTouchCompatibility(mode: TouchCompatibility) {
        store.touchCompatibility = mode
        afterChange()
    }

    fun setMotion(enabled: Boolean) {
        store.motionEnabled = enabled
        afterChange()
    }

    fun setVideoSound(enabled: Boolean) {
        store.videoSoundEnabled = enabled
        afterChange()
    }

    fun setAudioEnabled(enabled: Boolean) {
        store.audioEnabled = enabled
        afterChange()
    }

    fun setAudioLooping(enabled: Boolean) {
        store.audioLooping = enabled
        afterChange()
    }

    fun setAudioVolume(volume: Float) {
        store.audioVolume = volume
        afterChange()
    }

    fun thumbnailFor(page: PageConfig): File? = store.fileFor(page.thumbnailFile)

    /**
     * Records the offset the launcher is reporting right now as one edge of the real scroll
     * range, for launchers that only sweep part of 0..1.
     *
     * The user stands on the leftmost or rightmost home screen, opens this app, and taps. The
     * engine wrote the last offset it saw, so that is the value captured.
     */
    fun captureLeftEdge() {
        val offset = store.lastOffset
        if (offset < 0f) {
            uiState = uiState.copy(errorMessage = "No scroll reported yet \u2014 swipe first.")
            return
        }
        store.calibrationMin = offset
        uiState = uiState.copy(noticeMessage = "Left edge set to ${"%.3f".format(offset)}")
        afterChange()
    }

    fun captureRightEdge() {
        val offset = store.lastOffset
        if (offset < 0f) {
            uiState = uiState.copy(errorMessage = "No scroll reported yet \u2014 swipe first.")
            return
        }
        store.calibrationMax = offset
        uiState = uiState.copy(noticeMessage = "Right edge set to ${"%.3f".format(offset)}")
        afterChange()
    }

    fun clearCalibration() {
        store.clearCalibration()
        uiState = uiState.copy(noticeMessage = "Calibration cleared")
        afterChange()
    }

    /** Wipes the recorded launcher behaviour so a fresh test starts from zero. */
    fun resetDiagnostics() {
        store.resetDiagnostics()
        uiState = uiState.copy(noticeMessage = "Diagnostics reset \u2014 swipe your home screen")
        afterChange()
    }

    private fun afterChange() {
        refresh()
        PageWidgetProvider.notifyPageChanged(getApplication(), store.currentPage)
    }

    private fun isWallpaperActive(): Boolean {
        val info = WallpaperManager.getInstance(getApplication()).wallpaperInfo ?: return false
        val expected = ComponentName(
            getApplication<Application>().packageName,
            PageWallpaperService::class.java.name,
        )
        return info.component == expected
    }
}
