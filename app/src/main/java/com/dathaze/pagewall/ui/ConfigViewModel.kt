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
import com.dathaze.pagewall.data.MediaImporter
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
    val crossfadeMillis: Int = PageStore.DEFAULT_CROSSFADE,
    val parallaxEnabled: Boolean = true,
    val audioEnabled: Boolean = false,
    val audioLooping: Boolean = false,
    val audioVolume: Float = 0.5f,
    val wallpaperActive: Boolean = false,
    val scrollingDetected: Boolean = false,
    val busy: Boolean = false,
)

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val store = PageStore(application)

    var uiState by mutableStateOf(ConfigUiState())
        private set

    /** Display size, used to decide how large imported pictures need to be. */
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
            crossfadeMillis = store.crossfadeMillis,
            parallaxEnabled = store.parallaxEnabled,
            audioEnabled = store.audioEnabled,
            audioLooping = store.audioLooping,
            audioVolume = store.audioVolume,
            wallpaperActive = isWallpaperActive(),
            scrollingDetected = store.sawScrollOffsets,
        )
    }

    fun assignImage(pageIndex: Int, uri: Uri) = runImport {
        val name = MediaImporter.importImage(
            context = getApplication(),
            store = store,
            uri = uri,
            pageIndex = pageIndex,
            targetWidth = screenWidth,
            targetHeight = screenHeight,
        )
        if (name != null) store.setImage(pageIndex, name)
    }

    fun assignAudio(pageIndex: Int, uri: Uri) = runImport {
        val imported = MediaImporter.importAudio(getApplication(), store, uri, pageIndex)
        if (imported != null) store.setAudio(pageIndex, imported.first, imported.second)
    }

    fun removeImage(pageIndex: Int) {
        store.fileFor(store.page(pageIndex).imageFile)?.delete()
        store.setImage(pageIndex, null)
        afterChange()
    }

    fun removeAudio(pageIndex: Int) {
        store.fileFor(store.page(pageIndex).audioFile)?.delete()
        store.setAudio(pageIndex, null, null)
        afterChange()
    }

    fun clearPage(pageIndex: Int) {
        store.clearPage(pageIndex)
        afterChange()
    }

    fun setPageCount(count: Int) {
        // Pages that fall outside the new count keep their files until explicitly cleared, so
        // dialling the count back down and up again does not lose a photo.
        store.pageCount = count
        afterChange()
    }

    fun setCrossfade(millis: Int) {
        store.crossfadeMillis = millis
        afterChange()
    }

    fun setParallax(enabled: Boolean) {
        store.parallaxEnabled = enabled
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

    fun imageFileFor(page: PageConfig): File? = store.fileFor(page.imageFile)

    private fun runImport(block: suspend () -> Unit) {
        uiState = uiState.copy(busy = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            uiState = uiState.copy(busy = false)
            afterChange()
        }
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
