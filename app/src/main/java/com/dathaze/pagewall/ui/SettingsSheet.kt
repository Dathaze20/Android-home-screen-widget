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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
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
import com.dathaze.pagewall.backup.ConflictChoice
import com.dathaze.pagewall.update.UpdateState

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
    onFollowHomeJumpChange: (Boolean) -> Unit,
    onSyncWallpaperTo: (Int) -> Unit,
    onRestartOnboarding: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
    onDismissUpdate: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onConfirmRestore: (ConflictChoice, Boolean) -> Unit,
    onDismissBackup: () -> Unit,
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

            SectionHeading("UPDATES")
            UpdateRow(
                state = state,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onAllowInstalls = onAllowInstalls,
                onDismissUpdate = onDismissUpdate,
            )

            HorizontalDivider()
            SectionHeading("BACKUP")
            BackupRow(state, onExportBackup, onImportBackup, onDismissBackup)

            HorizontalDivider()
            SectionHeading("HOME SCREENS")
            WallpaperRow(state, onApplyWallpaper)
            PageCountRow(state, onPageCountChange, onResetPageCount)
            HomeSyncRow(
                state,
                onDefaultHomePageChange,
                onSyncOnReturnHomeChange,
                onFollowHomeJumpChange,
                onSyncWallpaperTo,
            )

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

/**
 * Check for updates, and install one without leaving the app.
 *
 * The same mechanism as the Music Player Tagger app: the newest GitHub release is compared
 * against this build's versionCode, and its APK is downloaded and handed to Android's installer.
 * Android still shows its own confirmation — nothing installs silently.
 *
 * It sits first in the sheet on purpose: it is the one row that is looked for rather than
 * stumbled upon, and nothing here should need scrolling to reach.
 */
@Composable
private fun UpdateRow(
    state: ConfigUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val update = state.updateState

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check for updates", fontWeight = FontWeight.SemiBold)
                Text(
                    "Version ${state.appVersionName.ifEmpty { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (update is UpdateState.Checking) {
                CircularProgressIndicator(Modifier.height(24.dp).width(24.dp))
            } else {
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = update !is UpdateState.Downloading,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Check", maxLines = 1) }
            }
        }

        when (update) {
            UpdateState.Idle, UpdateState.Checking -> Unit

            UpdateState.UpToDate -> UpdateNote("You are on the latest version.")

            is UpdateState.Available -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UpdateNote(
                    "Version ${update.update.versionName} is available" +
                        update.update.readableSize.let { if (it.isEmpty()) "." else " ($it)." }
                )
                if (update.update.notes.isNotEmpty()) {
                    Text(
                        update.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Installing keeps your screens and settings \u2014 nothing is erased.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDownloadUpdate,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Download and install") }
            }

            is UpdateState.Downloading -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UpdateNote("Downloading\u2026 ${update.percent}%")
                LinearProgressIndicator(
                    progress = { update.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UpdateState.ReadyToInstall ->
                UpdateNote("Downloaded. Confirm the install when Android asks.")

            UpdateState.NeedsInstallPermission -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UpdateNote(
                    "Android needs permission to install from this app. It is a one-time switch."
                )
                OutlinedButton(
                    onClick = onAllowInstalls,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Open that setting") }
            }

            is UpdateState.Failed -> Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    update.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onDismissUpdate,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun UpdateNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What the launcher is actually telling the wallpaper, plus the calibration that copes with it.
 *
 * Lives behind Advanced diagnostics: these numbers matter when the screens are not changing and
 * are noise the rest of the time. The range line is the decisive one — if the narrowest and
 * widest offsets ever seen are the same number, the launcher never moves the wallpaper and no
 * arithmetic can recover a screen from it, which is what Samsung compatibility exists for.
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
    val offsetMoves = state.observedMinOffset >= 0f && span > 0.001f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when {
                !state.wallpaperActive ->
                    "Set this as your wallpaper first, then swipe your home screen."

                state.detectionMode == "TOUCH" && state.touchEventsRaw > 0 ->
                    "Following your swipes, because this launcher reports a fixed wallpaper " +
                        "position. That is expected on One UI."

                state.detectionMode == "TOUCH" ->
                    "Waiting for touches. If this stays at zero after swiping, the launcher " +
                        "forwards nothing to the wallpaper and neither method can follow it."

                offsetMoves -> "Following the launcher's own scroll position."

                else -> "Swipe your home screen a few times, then reopen this."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Plain numbers, so a screenshot of this panel is enough to diagnose a phone.
        Text(
            text = "tracking mode: ${if (state.detectionMode == "TOUCH") "Samsung touch" else "Offset"}\n" +
                "actual wallpaper screen: ${state.displayedPage + 1}\n" +
                "touch events received: ${state.touchEventsRaw}\n" +
                "recognised swipes: ${state.recognisedSwipes}\n" +
                "last recognised swipe: ${state.lastSwipe.ifEmpty { "\u2014" }}\n" +
                "default home screen: ${state.defaultHomePage + 1}\n" +
                "xOffset: ${fmt(state.lastOffset)}\n" +
                "xOffsetStep: ${fmt(state.lastOffsetStep)}\n" +
                "range seen: ${fmt(state.observedMinOffset)} \u2192 ${fmt(state.observedMaxOffset)}\n" +
                "offset reports: ${state.offsetEventCount}\n" +
                "home presses seen: ${state.homeKeyEvents}\n" +
                "offset-derived screen: ${if (state.computedPage >= 0) "${state.computedPage + 1}" else "\u2014"}\n" +
                "screens (manual): ${state.pageCount}\n" +
                "screens (launcher): ${if (state.detectedPageCount > 0) "${state.detectedPageCount}" else "not reported"}\n" +
                "calibration: ${if (state.isCalibrated) "${fmt(state.calibrationMin)} \u2192 ${fmt(state.calibrationMax)}" else "off"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Calibration is for a launcher that only sweeps part of the range: stand on your " +
                "leftmost screen and tap Set left edge, then the rightmost and Set right edge.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCaptureLeftEdge,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Set left edge", maxLines = 1) }
            OutlinedButton(
                onClick = onCaptureRightEdge,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Set right edge", maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onClearCalibration,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Clear calibration") }
            TextButton(
                onClick = onResetDiagnostics,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Reset readings") }
        }
    }
}

/** -1 is the "never reported" marker rather than a real value. */
private fun fmt(value: Float): String =
    if (value < 0f) "none" else String.format("%.3f", value)

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

/**
 * Setting the wallpaper, from the menu.
 *
 * Reachable from here as well as from the main screen, because this is where someone looks when
 * the wallpaper has been replaced by something else and the app looks idle. The reassurance is
 * not decoration: re-applying used to look like it had wiped the screens, and it never has.
 */
/**
 * Export and import, in that order, with everything the restore needs to be safe.
 *
 * Export writes straight into Downloads without asking anything, because there is no decision
 * to make. Import asks twice over: once by showing what the backup actually contains, and again
 * about pages that already have something on them. Nothing is written until both are answered.
 */
@Composable
private fun BackupRow(
    state: ConfigUiState,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val backup = state.backupState

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your pages and settings, in one file", fontWeight = FontWeight.SemiBold)
        Text(
            "The backup holds the actual pictures, GIFs and videos as well as the page " +
                "assignments and settings \u2014 they live inside the app, so nothing else on " +
                "your phone has a copy of them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val busy = backup is BackupState.Working
        Button(
            onClick = onExportBackup,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Export backup to Downloads") }

        OutlinedButton(
            onClick = onImportBackup,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Import backup") }

        // Verification is not a third button, because it is not a third thing: importing
        // already reads and checks every file before it offers to do anything. Saying so is
        // what turns that into something you can use on purpose.
        Text(
            "Import checks the file first and shows you what is inside it. Tapping Cancel at " +
                "that point tells you a backup is complete and readable without changing " +
                "anything \u2014 which is how to check an old backup is still good.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (backup) {
            BackupState.Idle -> Unit

            is BackupState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
                Text(
                    "  ${backup.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is BackupState.Exported -> BackupNote(
                buildString {
                    append("Saved to ")
                    append(backup.result.where)
                    append(" \u2014 ")
                    append(readableSize(backup.result.bytes))
                    if (backup.result.skipped > 0) {
                        append(". ${backup.result.skipped} file(s) were missing from this app ")
                        append("and could not be included.")
                    }
                },
                onDismiss,
            )

            is BackupState.Restored -> BackupNote(
                buildString {
                    val r = backup.result
                    if (r.filled == 0 && r.replaced == 0) {
                        append("Nothing to change \u2014 your pages already match this backup.")
                    } else {
                        append("Restored ")
                        append(listOfNotNull(
                            r.filled.takeIf { it > 0 }?.let { "$it empty page(s) filled" },
                            r.replaced.takeIf { it > 0 }?.let { "$it replaced" },
                        ).joinToString(", "))
                        append(".")
                    }
                    if (r.kept > 0) append(" ${r.kept} of your own page(s) were left alone.")
                    if (r.settings) append(" Settings restored.")
                },
                onDismiss,
            )

            is BackupState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    backup.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Dismiss") }
            }

            is BackupState.Reviewing -> Unit // shown as a dialog by the caller
        }
    }
}

@Composable
private fun BackupNote(text: String, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("OK") }
    }
}

private fun readableSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes > 0 -> "${bytes / 1024} KB"
    else -> "empty"
}

/**
 * What the backup contains, and what restoring it would do, before anything is written.
 *
 * The conflict choice only appears when there is genuinely something at stake. With every page
 * empty there is nothing to lose and nothing to ask about.
 */
@Composable
fun RestoreConfirmDialog(
    review: BackupState.Reviewing,
    onConfirm: (ConflictChoice, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(ConflictChoice.KEEP_MINE) }
    var alsoSettings by rememberSaveable { mutableStateOf(true) }
    val filled = review.manifest.pages.count { it.hasMedia }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Taken ${review.manifest.exported.take(10)} from " +
                        "version ${review.manifest.appVersion}.\n" +
                        "$filled page(s) with media, ${review.manifest.files.size} file(s), " +
                        "${readableSize(review.totalBytes)}.\n" +
                        "Every file was checked and reads back correctly.",
                    style = MaterialTheme.typography.bodySmall,
                )

                if (review.conflicts > 0) {
                    Text(
                        "${review.conflicts} of your pages already have something on them.",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ConflictOption(
                        selected = choice == ConflictChoice.KEEP_MINE,
                        onSelect = { choice = ConflictChoice.KEEP_MINE },
                        title = "Keep my pages",
                        subtitle = "Only empty pages are filled. Nothing you have is touched.",
                    )
                    ConflictOption(
                        selected = choice == ConflictChoice.USE_BACKUP,
                        onSelect = { choice = ConflictChoice.USE_BACKUP },
                        title = "Use the backup's pages",
                        subtitle = "Overwrites those ${review.conflicts} pages with the backup.",
                    )
                } else {
                    Text(
                        "Every page the backup fills is empty here, so nothing of yours will " +
                            "be replaced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoSettings, onCheckedChange = { alsoSettings = it })
                    Column(Modifier.weight(1f)) {
                        Text("Restore settings too", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Crossfade, photo fit, page count and the rest. Your diagnostics " +
                                "are never restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice, alsoSettings) }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConflictOption(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WallpaperRow(state: ConfigUiState, onApplyWallpaper: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (state.wallpaperActive) "Wallpaper is active" else "Not your wallpaper yet",
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (state.wallpaperActive) {
                "Re-apply it if something else has taken over. Your screens and settings are kept."
            } else {
                "Set Page Wallpaper as your wallpaper to start. Choose Home screen when asked."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onApplyWallpaper,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (state.wallpaperActive) "Re-apply wallpaper" else "Set as wallpaper") }
    }
}

@Composable
private fun HomeSyncRow(
    state: ConfigUiState,
    onDefaultHomePageChange: (Int) -> Unit,
    onSyncOnReturnHomeChange: (Boolean) -> Unit,
    onFollowHomeJumpChange: (Boolean) -> Unit,
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

        SettingSwitch(
            title = "Follow the Home button (try it)",
            subtitle = "Pressing Home while you are already on the home screen is the one page " +
                "change Android tells a wallpaper nothing about, which is why the picture can " +
                "end up a screen or two out of step. This asks the system to report the button. " +
                "Off until you turn it on, and nothing is listening while it is off.",
            checked = state.followLauncherHomeJump,
            onCheckedChange = onFollowHomeJumpChange,
        )
        Text(
            if (state.followLauncherHomeJump) {
                "Home presses seen: ${state.homeKeyEvents}" +
                    if (state.homeKeyEvents == 0) {
                        " \u2014 press Home a few times from another screen, then reopen this. " +
                            "If it stays at zero, this phone does not report the button to a " +
                            "wallpaper and nothing here can change that. Switch it back off."
                    } else {
                        " \u2014 working."
                    }
            } else {
                "Not listening."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
