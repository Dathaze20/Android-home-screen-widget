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

            LauncherReport(state)

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
        Text("Pages not changing as you swipe?", fontWeight = FontWeight.SemiBold)
        Text(
            "Swipe across your home screen first — the launcher only reports where it is " +
                "once you do. If every page still looks identical, long-press the home screen " +
                "→ Settings and turn on “Wallpaper scrolling”. Not every One UI " +
                "version has that switch; where it is missing, scrolling is already on and the " +
                "problem is something else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onApplyWallpaper,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("Re-apply wallpaper") }
    }
}

/**
 * What the launcher is actually telling the wallpaper.
 *
 * Pages that never change look identical whether the launcher reports nothing, reports a single
 * fixed position, or reports fine but something else is wrong. This says which, so the problem
 * can be identified from the phone rather than guessed at.
 */
@Composable
private fun LauncherReport(state: ConfigUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("What your launcher reports", fontWeight = FontWeight.SemiBold)
        Text(
            text = when {
                !state.wallpaperActive ->
                    "Set this as your wallpaper first, then swipe your home screen."

                state.offsetEventCount == 0 ->
                    "Nothing yet. Swipe across your home screen a few times, then reopen this. " +
                        "If it still says nothing, your launcher never tells wallpapers where " +
                        "it has scrolled to, and no live wallpaper on this phone can change " +
                        "per page."

                state.lastOffsetStep <= 0f ->
                    "Reports arriving, but with no page step — the launcher is treating the " +
                        "home screen as one single page."

                else -> "Working."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Plain numbers, so a screenshot of this panel is enough to diagnose the phone.
        Text(
            text = "reports: ${state.offsetEventCount}  ·  " +
                "step: ${formatOffset(state.lastOffsetStep)}  ·  " +
                "offset: ${formatOffset(state.lastOffset)}  ·  " +
                "pages: ${state.detectedPageCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** -1 is the "never reported" marker rather than a real value. */
private fun formatOffset(value: Float): String =
    if (value < 0f) "none" else String.format("%.3f", value)

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
