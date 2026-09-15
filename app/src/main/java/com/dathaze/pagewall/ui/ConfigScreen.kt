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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.PageConfig
import com.dathaze.pagewall.data.PageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    focusPage: Int?,
    imageFileFor: (PageConfig) -> File?,
    onAssignImage: (Int, Uri) -> Unit,
    onAssignAudio: (Int, Uri) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onRemoveAudio: (Int) -> Unit,
    onClearPage: (Int) -> Unit,
    onPageCountChange: (Int) -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onParallaxChange: (Boolean) -> Unit,
    onAudioEnabledChange: (Boolean) -> Unit,
    onAudioLoopChange: (Boolean) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    // Which page a picker was opened for; the result callback has no other way to know.
    var pendingPage by rememberSaveable { mutableIntStateOf(0) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onAssignImage(pendingPage, it) } }

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

        item { StatusCard(state, onApplyWallpaper) }

        items(state.pages, key = { it.index }) { page ->
            PageCard(
                page = page,
                imageFile = imageFileFor(page),
                onPickImage = {
                    pendingPage = page.index
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickAudio = {
                    pendingPage = page.index
                    audioPicker.launch("audio/*")
                },
                onRemoveImage = { onRemoveImage(page.index) },
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
                onAudioEnabledChange = onAudioEnabledChange,
                onAudioLoopChange = onAudioLoopChange,
                onAudioVolumeChange = onAudioVolumeChange,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusCard(state: ConfigUiState, onApplyWallpaper: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.wallpaperActive) {
                Text("Not your wallpaper yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Assign photos below, then apply the wallpaper. After that it runs on its " +
                        "own — you never need to open this app again unless you want to swap a photo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onApplyWallpaper) { Text("Set as wallpaper") }
            } else if (!state.scrollingDetected) {
                Text("Turn on wallpaper scrolling", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The wallpaper is active, but your launcher has not reported a page scroll " +
                        "yet. On One UI: long-press the home screen → Settings → turn on " +
                        "“Wallpaper scrolling” (some versions call it parallax effect). " +
                        "Without it Android never tells any wallpaper which page you are on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onApplyWallpaper) { Text("Re-apply wallpaper") }
            } else {
                Text("Active", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Swipe between home screens to see each photo. Changes here apply instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PageCard(
    page: PageConfig,
    imageFile: File?,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveAudio: () -> Unit,
    onClear: () -> Unit,
    audioSectionVisible: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageThumbnail(imageFile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Page ${page.index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (page.hasImage) "Photo set" else "No photo",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (audioSectionVisible) {
                    Text(
                        page.audioTitle ?: "No track",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickImage) {
                        Icon(Icons.Default.Photo, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (page.hasImage) "Change" else "Photo")
                    }
                    if (audioSectionVisible) {
                        OutlinedButton(onClick = onPickAudio) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Song")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (page.hasImage) {
                        TextButton(onClick = onRemoveImage) { Text("Remove photo") }
                    }
                    if (audioSectionVisible && page.hasAudio) {
                        TextButton(onClick = onRemoveAudio) { Text("Remove song") }
                    }
                    if (page.hasImage || page.hasAudio) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                }
            }
        }
    }
}

/** Decodes the page thumbnail off the main thread, re-running when the file is replaced. */
@Composable
private fun PageThumbnail(file: File?) {
    val stamp = file?.lastModified() ?: 0L
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.path, stamp) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                        inSampleSize = 8
                    })?.asImageBitmap()
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
        } else {
            Icon(Icons.Default.Photo, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsCard(
    state: ConfigUiState,
    onPageCountChange: (Int) -> Unit,
    onCrossfadeChange: (Int) -> Unit,
    onParallaxChange: (Boolean) -> Unit,
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

            Column {
                Text("Crossfade: ${state.crossfadeMillis} ms")
                Slider(
                    value = state.crossfadeMillis.toFloat(),
                    onValueChange = { onCrossfadeChange(it.toInt()) },
                    valueRange = 0f..PageStore.MAX_CROSSFADE.toFloat(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Parallax drift")
                    Text(
                        "Let the photo slide a little as you swipe.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.parallaxEnabled, onCheckedChange = onParallaxChange)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Play a song per page")
                    Text(
                        "Plays while you are on the home screen, and stops when you open an app " +
                            "or the screen turns off. Never interrupts music already playing.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.audioEnabled, onCheckedChange = onAudioEnabledChange)
            }

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

/** Items rendered above the page list, used to translate a page index into a list position. */
private const val HEADER_ITEMS = 2
