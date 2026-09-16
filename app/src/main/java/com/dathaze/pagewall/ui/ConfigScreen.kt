package com.dathaze.pagewall.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.PageConfig
import com.dathaze.pagewall.data.PageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    focusPage: Int?,
    thumbnailFor: (PageConfig) -> File?,
    onAssignMedia: (Int, Uri) -> Unit,
    onAssignAudio: (Int, Uri) -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onRemoveAudio: (Int) -> Unit,
    onClearPage: (Int) -> Unit,
    onPageCountChange: (Int) -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onParallaxChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
    onVideoSoundChange: (Boolean) -> Unit,
    onAudioEnabledChange: (Boolean) -> Unit,
    onAudioLoopChange: (Boolean) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onApplyWallpaper: () -> Unit,
    onDismissError: () -> Unit,
) {
    // Which page a picker was opened for; the result callback has no other way to know.
    var pendingPage by rememberSaveable { mutableIntStateOf(0) }

    // One picker for photos, GIFs and videos — the system photo picker handles all three and
    // needs no storage permission.
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onAssignMedia(pendingPage, it) } }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onAssignAudio(pendingPage, it) } }

    val listState = rememberLazyListState()
    LaunchedEffect(focusPage, state.pages.size) {
        // Opened from the widget or a wallpaper tap: jump to the page in question.
        if (focusPage != null && focusPage < state.pages.size) {
            listState.scrollToItem(focusPage + HEADER_ITEMS)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Page Wallpaper",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item { StatusCard(state, onApplyWallpaper, onDismissError) }

        items(state.pages, key = { it.index }) { page ->
            PageCard(
                page = page,
                thumbnail = thumbnailFor(page),
                onPickMedia = {
                    pendingPage = page.index
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onPickAudio = {
                    pendingPage = page.index
                    audioPicker.launch("audio/*")
                },
                onRemoveMedia = { onRemoveMedia(page.index) },
                onRemoveAudio = { onRemoveAudio(page.index) },
                onClear = { onClearPage(page.index) },
                audioSectionVisible = state.audioEnabled,
            )
        }

        item {
            SettingsCard(
                state = state,
                onPageCountChange = onPageCountChange,
                onCrossfadeChange = onCrossfadeChange,
                onParallaxChange = onParallaxChange,
                onMotionChange = onMotionChange,
                onVideoSoundChange = onVideoSoundChange,
                onAudioEnabledChange = onAudioEnabledChange,
                onAudioLoopChange = onAudioLoopChange,
                onAudioVolumeChange = onAudioVolumeChange,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusCard(
    state: ConfigUiState,
    onApplyWallpaper: () -> Unit,
    onDismissError: () -> Unit,
) {
    // One root node: a LazyColumn item stacks siblings on top of each other rather than below.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.errorMessage, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismissError) { Text("OK") }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !state.wallpaperActive -> {
                        Text("Not your wallpaper yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Assign something to each page below, then apply the wallpaper. " +
                                "After that it runs on its own \u2014 you never need to open this " +
                                "app again unless you want to swap a page.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onApplyWallpaper) { Text("Set as wallpaper") }
                    }

                    !state.scrollingDetected -> {
                        Text(
                            "Turn on wallpaper scrolling",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "The wallpaper is active, but your launcher has not reported a page " +
                                "scroll yet. On One UI: long-press the home screen \u2192 " +
                                "Settings \u2192 turn on \u201cWallpaper scrolling\u201d (some " +
                                "versions call it the parallax effect). Without it Android never " +
                                "tells any wallpaper which page you are on.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onApplyWallpaper) { Text("Re-apply wallpaper") }
                    }

                    else -> {
                        Text("Active", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Swipe between home screens to see each page. Changes here apply " +
                                "instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCard(
    page: PageConfig,
    thumbnail: File?,
    onPickMedia: () -> Unit,
    onPickAudio: () -> Unit,
    onRemoveMedia: () -> Unit,
    onRemoveAudio: () -> Unit,
    onClear: () -> Unit,
    audioSectionVisible: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageThumbnail(thumbnail, page.mediaKind, page.hasMedia)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Page ${page.index + 1}", style = MaterialTheme.typography.titleMedium)
                Text(page.kindLabel, style = MaterialTheme.typography.bodySmall)
                if (audioSectionVisible) {
                    Text(
                        page.audioTitle ?: "No track",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickMedia) {
                        Icon(Icons.Default.Photo, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (page.hasMedia) "Change" else "Choose")
                    }
                    if (audioSectionVisible) {
                        OutlinedButton(onClick = onPickAudio) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Song")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (page.hasMedia) {
                        TextButton(onClick = onRemoveMedia) { Text("Remove") }
                    }
                    if (audioSectionVisible && page.hasAudio) {
                        TextButton(onClick = onRemoveAudio) { Text("Remove song") }
                    }
                    if (page.hasMedia || page.hasAudio) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            }
        }
    }
}

/**
 * Decodes the page thumbnail off the main thread, re-running when the file is replaced.
 * For a video this is the poster frame saved at import, so no decoder is started here.
 */
@Composable
private fun PageThumbnail(file: File?, kind: MediaKind, hasMedia: Boolean) {
    val stamp = file?.lastModified() ?: 0L
    val bitmap by produceState<ImageBitmap?>(null, file?.path, stamp) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = 8 },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(72.dp, 110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp, 110.dp),
            )
        }
        if (!hasMedia) {
            Icon(Icons.Default.Photo, contentDescription = null)
        } else {
            // A badge in the corner so video and GIF pages are identifiable at a glance.
            val badge = when (kind) {
                MediaKind.VIDEO -> Icons.Default.PlayCircle to "Video"
                MediaKind.GIF -> Icons.Default.Gif to "GIF"
                MediaKind.IMAGE -> null
            }
            if (badge != null) {
                Icon(
                    imageVector = badge.first,
                    contentDescription = badge.second,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    state: ConfigUiState,
    onPageCountChange: (Int) -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onParallaxChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
    onVideoSoundChange: (Boolean) -> Unit,
    onAudioEnabledChange: (Boolean) -> Unit,
    onAudioLoopChange: (Boolean) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Home screen pages", Modifier.weight(1f))
                IconButton(
                    onClick = { onPageCountChange(state.pageCount - 1) },
                    enabled = state.pageCount > PageStore.MIN_PAGES,
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("${state.pageCount}", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { onPageCountChange(state.pageCount + 1) },
                    enabled = state.pageCount < PageStore.MAX_PAGES,
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }

            SettingSwitch(
                title = "Animate GIFs and videos",
                subtitle = "Off holds each one on its first frame, which uses far less battery.",
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

            Column {
                Text("Crossfade: ${state.crossfadeMillis} ms")
                Text(
                    "Photo and GIF pages fade into each other. Video pages cut, because the " +
                        "video player takes over the whole screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = state.crossfadeMillis.toFloat(),
                    onValueChange = { onCrossfadeChange(it.toInt()) },
                    valueRange = 0f..PageStore.MAX_CROSSFADE.toFloat(),
                )
            }

            SettingSwitch(
                title = "Parallax drift",
                subtitle = "Let a photo slide a little as you swipe.",
                checked = state.parallaxEnabled,
                onCheckedChange = onParallaxChange,
            )

            SettingSwitch(
                title = "Play a song per page",
                subtitle = "Plays while you are on the home screen, and stops when you open an " +
                    "app or the screen turns off. Never interrupts music already playing.",
                checked = state.audioEnabled,
                onCheckedChange = onAudioEnabledChange,
            )

            if (state.audioEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop the track", Modifier.weight(1f))
                    Switch(checked = state.audioLooping, onCheckedChange = onAudioLoopChange)
                }
                Column {
                    Text("Volume: ${(state.audioVolume * 100).toInt()}%")
                    Slider(
                        value = state.audioVolume,
                        onValueChange = onAudioVolumeChange,
                        valueRange = 0f..1f,
                    )
                }
            }
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Items rendered above the page list, used to translate a page index into a list position. */
private const val HEADER_ITEMS = 2
