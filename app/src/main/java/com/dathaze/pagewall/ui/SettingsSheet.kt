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
import androidx.compose.material3.OutlinedButton
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
    onCaptureLeftEdge: () -> Unit,
    onCaptureRightEdge: () -> Unit,
    onClearCalibration: () -> Unit,
    onResetDiagnostics: () -> Unit,
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

            LauncherReport(
                state = state,
                onCaptureLeftEdge = onCaptureLeftEdge,
                onCaptureRightEdge = onCaptureRightEdge,
                onClearCalibration = onClearCalibration,
                onResetDiagnostics = onResetDiagnostics,
            )

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
 * What the launcher is actually telling the wallpaper, and the calibration that copes with it.
 *
 * Pages that never change look identical whether the launcher reports nothing, reports one fixed
 * position, or reports fine and something else is wrong. The range line separates them: if the
 * narrowest and widest offsets ever seen are the same number, the launcher is not moving the
 * wallpaper at all and no arithmetic can recover a page from it.
 */
@Composable
private fun LauncherReport(
    state: ConfigUiState,
    onCaptureLeftEdge: () -> Unit,
    onCaptureRightEdge: () -> Unit,
    onClearCalibration: () -> Unit,
    onResetDiagnostics: () -> Unit,
) {
    val span = state.observedMaxOffset - state.observedMinOffset
    val movesAtAll = state.observedMinOffset >= 0f && span > 0.001f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What your launcher reports", fontWeight = FontWeight.SemiBold)
        Text(
            text = when {
                !state.wallpaperActive ->
                    "Set this as your wallpaper first, then swipe your home screen."

                state.offsetEventCount == 0 ->
                    "Nothing yet. Swipe across your home screen a few times, then reopen this."

                !movesAtAll ->
                    "Your launcher reports a position, but the same one every time. It is not " +
                        "moving the wallpaper as you swipe, so no live wallpaper on this phone " +
                        "can tell the pages apart. Try Set left edge and Set right edge below; " +
                        "if the range still does not move, the stock launcher cannot do this."

                span < 0.2f ->
                    "Your launcher only sweeps part of the range. Calibrate with the two buttons " +
                        "below so the whole range maps across your pages."

                else -> "Working — the offset moves across the range as you swipe."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Plain numbers, so a screenshot of this panel is enough to diagnose the phone.
        Text(
            text = "xOffset: ${fmt(state.lastOffset)}\n" +
                "xOffsetStep: ${fmt(state.lastOffsetStep)}\n" +
                "range seen: ${fmt(state.observedMinOffset)} → ${fmt(state.observedMaxOffset)}\n" +
                "pages (manual): ${state.pageCount}\n" +
                "pages (launcher): ${if (state.detectedPageCount > 0) "${state.detectedPageCount}" else "not reported"}\n" +
                "current page: ${if (state.computedPage >= 0) "${state.computedPage + 1}" else "—"}\n" +
                "picture shown: ${if (state.computedPage >= 0) "${state.computedPage + 1}" else "—"}\n" +
                "reports: ${state.offsetEventCount}\n" +
                "calibration: ${if (state.isCalibrated) "${fmt(state.calibrationMin)} → ${fmt(state.calibrationMax)}" else "off"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Calibration: stand on your leftmost home screen, open this and tap Set left edge. " +
                "Then go to your rightmost home screen and tap Set right edge.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCaptureLeftEdge, modifier = Modifier.weight(1f)) {
                Text("Set left edge", maxLines = 1)
            }
            OutlinedButton(onClick = onCaptureRightEdge, modifier = Modifier.weight(1f)) {
                Text("Set right edge", maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClearCalibration) { Text("Clear calibration") }
            TextButton(onClick = onResetDiagnostics) { Text("Reset readings") }
        }
    }
}

/** -1 is the "never reported" marker rather than a real value. */
private fun fmt(value: Float): String =
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
