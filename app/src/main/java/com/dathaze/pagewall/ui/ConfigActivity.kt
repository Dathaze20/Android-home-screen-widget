package com.dathaze.pagewall.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dathaze.pagewall.wallpaper.PageWallpaperService
import kotlinx.coroutines.launch

/**
 * The one-time setup screen: assign a photo (and optionally a track) to each home screen page.
 *
 * After the wallpaper is applied this screen is only needed to change an assignment; the
 * wallpaper keeps working with the app closed.
 */
class ConfigActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val metrics = resources.displayMetrics
        viewModel.setScreenSize(metrics.widthPixels, metrics.heightPixels)

        val focusPage = intent?.getIntExtra(EXTRA_PAGE, -1)?.takeIf { it >= 0 }

        setContent {
            PageWallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ConfigScreen(
                            state = viewModel.uiState,
                            focusPage = focusPage,
                            thumbnailFor = viewModel::thumbnailFor,
                            onAssignMedia = viewModel::assignMedia,
                            onAssignAudio = viewModel::assignAudio,
                            onRemoveMedia = viewModel::removeMedia,
                            onRemoveAudio = viewModel::removeAudio,
                            onClearPage = viewModel::clearPage,
                            onPageCountChange = viewModel::setPageCount,
                            onCrossfadeChange = viewModel::setCrossfade,
                            onParallaxChange = viewModel::setParallax,
                            onMotionChange = viewModel::setMotion,
                            onVideoSoundChange = viewModel::setVideoSound,
                            onAudioEnabledChange = viewModel::setAudioEnabled,
                            onAudioLoopChange = viewModel::setAudioLooping,
                            onAudioVolumeChange = viewModel::setAudioVolume,
                            onApplyWallpaper = ::applyWallpaper,
                            onDismissError = viewModel::dismissError,
                        )
                        if (viewModel.uiState.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // The wallpaper can be applied or removed outside the app, and the engine writes back
        // whether it has seen scroll offsets, so re-read state every time we come forward.
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
        const val EXTRA_PAGE = "page"
    }
}
