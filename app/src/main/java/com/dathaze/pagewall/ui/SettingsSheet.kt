package com.dathaze.pagewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.PageStore

/**
 * Everything that is not "pick a photo", tucked behind one icon.
 *
 * These are set-once-and-forget knobs, so they do not belong on the main screen competing with
 * the thing the app is actually for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: ConfigUiState,
    onDismiss: () -> Unit,
    onPageCountChange: (Int) -> Unit,
    onResetPageCount: () -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onParallaxChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
    onVideoSoundChange: (Boolean) -> Unit,
    onAudioEnabledChange: (Boolean) -> Unit,
    onAudioLoopChange: (Boolean) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (state.wallpaperActive && !state.scrollingDetected) {
                ScrollingWarning(onApplyWallpaper)
                HorizontalDivider()
            }

            PageCountRow(state, onPageCountChange, onResetPageCount)

            HorizontalDivider()

            SettingSwitch(
                title = "Animate GIFs and videos",
                subtitle = "Off holds each one on its first frame and uses far less battery.",
                checked = state.motionEnabled,
                onCheckedChange = onMotionChange,
            )

            if (state.motionEnabled) {
                SettingSwitch(
                    title = "Video sound",
                    subtitle = "Play a video page's own soundtrack.",
                    checked = state.videoSoundEnabled,
                    onCheckedChange = onVideoSoundChange,
                )
            }

            SettingSwitch(
                title = "Parallax drift",
                subtitle = "Let a photo slide a little as you swipe.",
                checked = state.parallaxEnabled,
                onCheckedChange = onParallaxChange,
            )

            Column {
                Text("Crossfade: ${state.crossfadeMillis} ms")
                Text(
                    "Video pages cut instead of fading — the player owns the whole screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.crossfadeMillis.toFloat(),
                    onValueChange = { onCrossfadeChange(it.toInt()) },
                    valueRange = 0f..PageStore.MAX_CROSSFADE.toFloat(),
                )
            }

            HorizontalDivider()

            SettingSwitch(
                title = "Play a song per page",
                subtitle = "Plays while you are on the home screen and stops when you open an " +
                    "app. Never interrupts music that is already playing.",
                checked = state.audioEnabled,
                onCheckedChange = onAudioEnabledChange,
            )

            if (state.audioEnabled) {
                SettingSwitch(
                    title = "Loop the track",
                    subtitle = "Keep it going while you stay on that page.",
                    checked = state.audioLooping,
                    onCheckedChange = onAudioLoopChange,
                )
                Column {
                    Text("Volume: ${(state.audioVolume * 100).toInt()}%")
                    Slider(
                        value = state.audioVolume,
                        onValueChange = onAudioVolumeChange,
                        valueRange = 0f..1f,
                    )
                }
                Text(
                    "Assign a track by sharing it from your music app to Page Wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScrollingWarning(onApplyWallpaper: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Turn on wallpaper scrolling", fontWeight = FontWeight.SemiBold)
        Text(
            "Your launcher has not reported a page scroll yet. Long-press the home screen " +
                "→ Settings → turn on “Wallpaper scrolling”. Without it " +
                "Android never tells any wallpaper which page you are on, so every page shows " +
                "the same thing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onApplyWallpaper,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("Re-apply wallpaper") }
    }
}

@Composable
private fun PageCountRow(
    state: ConfigUiState,
    onPageCountChange: (Int) -> Unit,
    onResetPageCount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Home screen pages")
                Text(
                    if (state.pageCountIsDetected) {
                        "Detected from your launcher."
                    } else {
                        "Set by hand. It detects itself once the wallpaper has been swiped."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { onPageCountChange(state.pageCount - 1) },
                enabled = state.pageCount > PageStore.MIN_PAGES,
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text("${state.pageCount}", style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = { onPageCountChange(state.pageCount + 1) },
                enabled = state.pageCount < PageStore.MAX_PAGES,
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        if (!state.pageCountIsDetected && state.detectedPageCount > 0) {
            TextButton(
                onClick = onResetPageCount,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("Use the detected ${state.detectedPageCount}") }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
