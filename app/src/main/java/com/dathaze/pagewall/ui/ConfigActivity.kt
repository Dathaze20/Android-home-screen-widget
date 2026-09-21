package com.dathaze.pagewall.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dathaze.pagewall.wallpaper.PageWallpaperService
import kotlinx.coroutines.launch

/**
 * The app: one screen showing every home screen page as a tile.
 *
 * After the wallpaper is applied this is only needed to change a page; the wallpaper keeps
 * working with the app closed.
 */
class ConfigActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val metrics = resources.displayMetrics
        viewModel.setScreenSize(metrics.widthPixels, metrics.heightPixels)

        val focusPage = intent?.getIntExtra(EXTRA_PAGE, -1)?.takeIf { it >= 0 }

        setContent {
            PageWallTheme {
                var settingsOpen by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        // The grid is sized to the space it gets, so it has to be told about the
                        // status bar and gesture bar rather than drawing underneath them.
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        state = viewModel.uiState,
                        focusPage = focusPage,
                        onAssignMedia = viewModel::assignMedia,
                        onFillPages = viewModel::fillPages,
                        onClearPage = viewModel::clearPage,
                        onApplyWallpaper = ::applyWallpaper,
                        onOpenSettings = { settingsOpen = true },
                        onDismissError = viewModel::dismissError,
                        thumbnailFor = viewModel::thumbnailFor,
                    )
                }

                if (settingsOpen) {
                    SettingsSheet(
                        state = viewModel.uiState,
                        onDismiss = { settingsOpen = false },
                        onPageCountChange = viewModel::setPageCount,
                        onResetPageCount = viewModel::resetPageCount,
                        onCrossfadeChange = viewModel::setCrossfade,
                        onParallaxChange = viewModel::setParallax,
                        onMotionChange = viewModel::setMotion,
                        onVideoSoundChange = viewModel::setVideoSound,
                        onAudioEnabledChange = viewModel::setAudioEnabled,
                        onAudioLoopChange = viewModel::setAudioLooping,
                        onAudioVolumeChange = viewModel::setAudioVolume,
                        onApplyWallpaper = ::applyWallpaper,
                        onCaptureLeftEdge = viewModel::captureLeftEdge,
                        onCaptureRightEdge = viewModel::captureRightEdge,
                        onClearCalibration = viewModel::clearCalibration,
                        onResetDiagnostics = viewModel::resetDiagnostics,
                        onPhotoFitChange = viewModel::setPhotoFit,
                        onTouchCompatibilityChange = viewModel::setTouchCompatibility,
                    )
                }
            }
        }

        // The wallpaper can be applied or removed outside the app, and the engine writes back the
        // detected page count, so re-read state every time we come forward.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
        }
    }

    /**
     * Opens the system picker with this wallpaper preselected. Some launchers block the direct
     * intent, so fall back to the plain live wallpaper chooser.
     */
    private fun applyWallpaper() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, PageWallpaperService::class.java),
        )
        val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        runCatching { startActivity(direct) }
            .recoverCatching { startActivity(fallback) }
    }

    companion object {
        /** Page to focus when opened from the widget or a tap on the wallpaper. */
        const val EXTRA_PAGE = "page"
    }
}
