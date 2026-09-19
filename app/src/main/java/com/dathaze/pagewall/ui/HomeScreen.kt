package com.dathaze.pagewall.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.PageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

/**
 * The whole app, on one screen.
 *
 * Every page is a tile in a grid that is sized to fit the display exactly, so there is nothing to
 * scroll: you see all your home screens at once, with what is on each. Tap a tile to change that
 * page, long-press to empty it, or use the button at the bottom to fill every page at once.
 * Settings live behind the icon in the corner, because you touch them roughly never.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: ConfigUiState,
    /** Set when opened from the widget: that page's picker opens straight away. */
    focusPage: Int? = null,
    onAssignMedia: (Int, Uri) -> Unit,
    onFillPages: (List<Uri>) -> Unit,
    onClearPage: (Int) -> Unit,
    onApplyWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    thumbnailFor: (PageConfig) -> File?,
) {
    // Which tile a single-page picker was opened for; the result callback has no other way to know.
    var pendingPage by rememberSaveable { mutableIntStateOf(0) }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onAssignMedia(pendingPage, it) } }

    // Filling every page in one go is the main path: pick several, they land in order.
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(state.pageCount.coerceAtLeast(2))
    ) { uris -> if (uris.isNotEmpty()) onFillPages(uris) }

    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    val assignedCount = state.pages.count { it.hasMedia }

    // Arriving from the widget means "change this page", so skip the screen and open the picker.
    // Guarded by a saved flag so a rotation does not reopen it.
    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusPage) {
        if (focusPage != null && !focusHandled && focusPage < state.pageCount) {
            focusHandled = true
            pendingPage = focusPage
            singlePicker.launch(request)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state, assignedCount, onOpenSettings)

        PageGrid(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = state,
            thumbnailFor = thumbnailFor,
            onTapPage = { index ->
                pendingPage = index
                singlePicker.launch(request)
            },
            onLongPressPage = onClearPage,
        )

        if (state.errorMessage != null) {
            ErrorBanner(state.errorMessage, onDismissError)
        }

        PrimaryAction(
            state = state,
            assignedCount = assignedCount,
            onPickAll = { multiPicker.launch(request) },
            onApplyWallpaper = onApplyWallpaper,
        )
    }
}

@Composable
private fun Header(state: ConfigUiState, assignedCount: Int, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your home screens",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (state.wallpaperActive) {
                    Spacer(Modifier.size(8.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = statusLine(state, assignedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Tune, contentDescription = "Settings")
        }
    }
}

/** One short line that says the only thing worth saying right now. */
private fun statusLine(state: ConfigUiState, assignedCount: Int): String = when {
    // Not an error: the launcher only reports its scroll position once you actually swipe.
    state.wallpaperActive && !state.scrollingDetected ->
        "Swipe across your home screen once to finish"

    state.wallpaperActive ->
        "Active · ${state.pageCount} pages · tap a tile to change it"

    assignedCount == 0 ->
        "Pick your photos, then set it once and you are done"

    state.pageCountIsDetected ->
        "$assignedCount of ${state.pageCount} filled · ${state.pageCount} pages detected"

    else ->
        "$assignedCount of ${state.pageCount} filled · page count adjusts itself once set"
}

/**
 * A grid sized to the space it is given, never scrollable.
 *
 * Weighted rows rather than a lazy grid: a lazy grid would happily overflow and reintroduce the
 * scrolling this screen exists to avoid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageGrid(
    modifier: Modifier,
    state: ConfigUiState,
    thumbnailFor: (PageConfig) -> File?,
    onTapPage: (Int) -> Unit,
    onLongPressPage: (Int) -> Unit,
) {
    val pages = state.pages
    val columns = if (pages.size <= 4) 2 else 3
    val rows = ceil(pages.size / columns.toFloat()).toInt().coerceAtLeast(1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    if (index < pages.size) {
                        PageTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            page = pages[index],
                            thumbnail = thumbnailFor(pages[index]),
                            onTap = { onTapPage(index) },
                            onLongPress = { onLongPressPage(index) },
                        )
                    } else {
                        // Keeps the last row's tiles the same width as every other row's.
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTile(
    modifier: Modifier,
    page: PageConfig,
    thumbnail: File?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val bitmap by rememberPageBitmap(thumbnail)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (page.hasMedia) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                }
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Page ${page.index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!page.hasMedia) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add to page ${page.index + 1}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }

        // Page number, always legible: a dark pill rather than text straight on the photo.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${page.index + 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        val badge = when {
            !page.hasMedia -> null
            page.mediaKind == MediaKind.VIDEO -> Icons.Default.PlayCircle
            page.mediaKind == MediaKind.GIF -> Icons.Default.Gif
            else -> null
        }
        if (badge != null) {
            Icon(
                imageVector = badge,
                contentDescription = page.kindLabel,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    state: ConfigUiState,
    assignedCount: Int,
    onPickAll: () -> Unit,
    onApplyWallpaper: () -> Unit,
) {
    val readyToApply = assignedCount > 0 && !state.wallpaperActive

    Button(
        onClick = if (readyToApply) onApplyWallpaper else onPickAll,
        enabled = !state.busy,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = when {
                    readyToApply -> "Set as wallpaper"
                    state.wallpaperActive -> "Change photos"
                    else -> "Choose photos"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // A longer label than this was being clipped mid-word inside the button.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val hint = when {
        readyToApply -> "Your phone will ask where to put it — choose Home screen."
        state.wallpaperActive && !state.scrollingDetected ->
            "Still the same on every page? Tap the settings icon."
        else -> null
    }
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

/** Decodes a tile thumbnail off the main thread, re-running when the file behind it changes. */
@Composable
private fun rememberPageBitmap(file: File?): State<ImageBitmap?> {
    val stamp = file?.lastModified() ?: 0L
    return produceState<ImageBitmap?>(null, file?.path, stamp) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = 4 },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}
