package com.dathaze.pagewall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.data.PhotoFit
import com.dathaze.pagewall.data.TouchCompatibility

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
    onPhotoFitChange: (PhotoFit) -> Unit,
    onTouchCompatibilityChange: (TouchCompatibility) -> Unit,
    onDefaultHomePageChange: (Int) -> Unit,
    onSyncOnReturnHomeChange: (Boolean) -> Unit,
    onSyncWallpaperTo: (Int) -> Unit,
    onRestartOnboarding: () -> Unit,
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

            SectionHeading("HOME SCREENS")
            PageCountRow(state, onPageCountChange, onResetPageCount)
            HomeSyncRow(state, onDefaultHomePageChange, onSyncOnReturnHomeChange, onSyncWallpaperTo)

            HorizontalDivider()
            SectionHeading("APPEARANCE")
            PhotoFitRow(state, onPhotoFitChange)
            Column {
                Text("Crossfade: ${state.crossfadeMillis} ms")
                Text(
                    "Video screens cut instead of fading \u2014 the player owns the whole screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.crossfadeMillis.toFloat(),
                    onValueChange = { onCrossfadeChange(it.toInt()) },
                    valueRange = 0f..PageStore.MAX_CROSSFADE.toFloat(),
                )
            }
            SettingSwitch(
                title = "Parallax drift",
                subtitle = "Let a picture slide a little as you swipe.",
                checked = state.parallaxEnabled,
                onCheckedChange = onParallaxChange,
            )

            HorizontalDivider()
            SectionHeading("MEDIA")
            SettingSwitch(
                title = "Animate GIFs and videos",
                subtitle = "Off holds each one on its first frame and uses far less battery.",
                checked = state.motionEnabled,
                onCheckedChange = onMotionChange,
            )
            if (state.motionEnabled) {
                SettingSwitch(
                    title = "Video sound",
                    subtitle = "Play a video screen's own soundtrack.",
                    checked = state.videoSoundEnabled,
                    onCheckedChange = onVideoSoundChange,
                )
            }
            SettingSwitch(
                title = "Play a song per screen",
                subtitle = "Plays while you are on the home screen and stops when you open an " +
                    "app. Never interrupts music that is already playing.",
                checked = state.audioEnabled,
                onCheckedChange = onAudioEnabledChange,
            )
            if (state.audioEnabled) {
                SettingSwitch(
                    title = "Loop the track",
                    subtitle = "Keep it going while you stay on that screen.",
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
            }

            HorizontalDivider()
            SectionHeading("COMPATIBILITY")
            TouchModeRow(state, onTouchCompatibilityChange)

            HorizontalDivider()
            // Collapsed: the numbers matter when something is wrong and are noise otherwise.
            var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
            TextButton(
                onClick = { diagnosticsOpen = !diagnosticsOpen },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    if (diagnosticsOpen) "Hide advanced diagnostics"
                    else "Advanced diagnostics",
                )
            }
            if (diagnosticsOpen) {
                LauncherReport(
                    state = state,
                    onCaptureLeftEdge = onCaptureLeftEdge,
                    onCaptureRightEdge = onCaptureRightEdge,
                    onClearCalibration = onClearCalibration,
                    onResetDiagnostics = onResetDiagnostics,
                )
                TextButton(
                    onClick = onRestartOnboarding,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Show setup guide again") }
            }
        }
    }
}

/** -1 is the "never reported" marker rather than a real value. */
private fun fmt(value: Float): String =
    if (value < 0f) "none" else String.format("%.3f", value)

/**
 * How a picture is laid out when its shape does not match the screen's.
 *
 * A landscape picture on a tall phone cannot be complete, uncropped, undistorted and reach all
 * four corners at once — the shapes differ, so one of those has to give.
 */
/**
 * Everything to do with the launcher moving without the wallpaper seeing it.
 *
 * Touch tracking only learns about page changes it observes as gestures, so returning from an
 * app, a restarted engine and any launcher behaviour that cannot be observed need an answer here.
 */
/** A quiet all-caps rule, so the groups read as groups without heavy chrome. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = PageWallColors.Cyan,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HomeSyncRow(
    state: ConfigUiState,
    onDefaultHomePageChange: (Int) -> Unit,
    onSyncOnReturnHomeChange: (Boolean) -> Unit,
    onSyncWallpaperTo: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Default Home screen", fontWeight = FontWeight.SemiBold)
        Text(
            "The screen your phone returns to when you press Home. Android never tells an app " +
                "which one that is, so it has to be set here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScreenPicker(state.pageCount, state.defaultHomePage, onDefaultHomePageChange)

        SettingSwitch(
            title = "Sync when returning Home",
            subtitle = "Jump to that screen when you come back from an app. Locking and " +
                "unlocking never triggers it \u2014 your phone has not moved.",
            checked = state.syncOnReturnHome,
            onCheckedChange = onSyncOnReturnHomeChange,
        )

        Text("Sync wallpaper now", fontWeight = FontWeight.SemiBold)
        Text(
            "Out of step? Tap the screen you are actually standing on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScreenPicker(state.pageCount, state.displayedPage, onSyncWallpaperTo)
    }
}

/** A row of screen numbers, wrapping so a high screen count still fits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenPicker(pageCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text("${index + 1}", maxLines = 1) },
            )
        }
    }
}

@Composable
private fun PhotoFitRow(state: ConfigUiState, onPhotoFitChange: (PhotoFit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Photo fit", fontWeight = FontWeight.SemiBold)
        Text(
            text = if (state.photoFit == PhotoFit.FULL_IMAGE) {
                "The whole picture, nothing cropped, over a blurred copy of itself."
            } else {
                "Zoomed until it covers the screen. The edges of a wide picture are cut off."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.photoFit == PhotoFit.FULL_IMAGE,
                onClick = { onPhotoFitChange(PhotoFit.FULL_IMAGE) },
                label = { Text("Full image") },
            )
            FilterChip(
                selected = state.photoFit == PhotoFit.FILL_SCREEN,
                onClick = { onPhotoFitChange(PhotoFit.FILL_SCREEN) },
                label = { Text("Fill screen") },
            )
        }
    }
}

/**
 * Which method follows the pages.
 *
 * One UI reports a fixed wallpaper offset, so there is nothing in it to read a page from. The
 * fallback watches the swipe itself instead and steps the page by hand.
 */
@Composable
private fun TouchModeRow(
    state: ConfigUiState,
    onTouchCompatibilityChange: (TouchCompatibility) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Samsung compatibility", fontWeight = FontWeight.SemiBold)
        Text(
            "On a launcher that never moves the wallpaper offset, the pages are followed by " +
                "watching your swipe instead. Auto turns it on by itself when the offset stays " +
                "put. It only works if your launcher passes touches to the wallpaper \u2014 the " +
                "touch reports line above says whether yours does.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TouchCompatibility.entries.forEach { option ->
                FilterChip(
                    selected = state.touchCompatibility == option,
                    onClick = { onTouchCompatibilityChange(option) },
                    label = {
                        Text(
                            when (option) {
                                TouchCompatibility.AUTO -> "Auto"
                                TouchCompatibility.ON -> "Always on"
                                TouchCompatibility.OFF -> "Off"
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
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
                contentPadding = PaddingValues(0.dp),
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
