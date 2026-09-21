package com.dathaze.pagewall.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.MediaKind
import com.dathaze.pagewall.data.PageConfig
import com.dathaze.pagewall.data.PageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

/** Transitions stay in this range: present enough to read, short enough not to feel slow. */
private const val TRANSITION_MS = 200

/**
 * The control centre: every home screen page as a panel, and one primary action.
 *
 * Sized to the display rather than scrolled, so the whole set is visible at once. The grid drops
 * to two columns up to six screens so the pictures stay large, and only goes to three beyond that
 * — the alternative was uniformly tiny thumbnails.
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
    onOpenPageDetails: (Int) -> Unit,
    onApplyWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    thumbnailFor: (PageConfig) -> File?,
) {
    var pendingPage by rememberSaveable { mutableIntStateOf(0) }
    var confirmClearPage by remember { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current

    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onAssignMedia(pendingPage, it) } }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PageStore.MAX_PAGES)
    ) { uris -> if (uris.isNotEmpty()) onFillPages(uris) }

    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusPage) {
        if (focusPage != null && !focusHandled && focusPage < state.pageCount) {
            focusHandled = true
            pendingPage = focusPage
            singlePicker.launch(request)
        }
    }

    val assignedCount = state.pages.count { it.hasMedia }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusHeader(state, assignedCount, onOpenSettings)

        PageGrid(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = state,
            thumbnailFor = thumbnailFor,
            onTapPage = { index ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (state.pages.getOrNull(index)?.hasMedia == true) {
                    // A configured screen opens its details rather than dropping straight into a
                    // picker, so changing a soundtrack does not mean replacing the picture.
                    onOpenPageDetails(index)
                } else {
                    pendingPage = index
                    singlePicker.launch(request)
                }
            },
            onLongPressPage = { index ->
                if (state.pages.getOrNull(index)?.hasMedia == true) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    confirmClearPage = index
                }
            },
        )

        val banner = state.errorMessage ?: state.noticeMessage
        if (banner != null) {
            MessageBanner(banner, state.errorMessage != null, onDismissError)
        }

        PrimaryActions(
            state = state,
            assignedCount = assignedCount,
            onApplyWallpaper = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onApplyWallpaper()
            },
            onChangeMedia = { multiPicker.launch(request) },
            onOpenSettings = onOpenSettings,
        )
    }

    // A long press is easy to trigger by accident, so clearing a screen asks first.
    confirmClearPage?.let { index ->
        AlertDialog(
            onDismissRequest = { confirmClearPage = null },
            title = { Text("Clear screen ${index + 1}?") },
            text = { Text("The picture and any soundtrack on this screen will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearPage(index)
                    confirmClearPage = null
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPage = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun StatusHeader(state: ConfigUiState, assignedCount: Int, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "PAGE WALLPAPER",
                style = MaterialTheme.typography.labelSmall,
                color = PageWallColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.wallpaperActive) "Active" else "Not set up",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PageWallColors.TextPrimary,
                )
                if (state.wallpaperActive) {
                    Spacer(Modifier.width(10.dp))
                    StatusPill()
                }
            }
            Text(
                text = statusLine(state, assignedCount),
                style = MaterialTheme.typography.bodySmall,
                color = PageWallColors.TextSecondary,
            )
        }
        // 48dp so the target is reachable even though the glyph is small.
        OutlinedButton(
            onClick = onOpenSettings,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Tune, contentDescription = "Settings", tint = PageWallColors.Cyan)
        }
    }
}

/** Text as well as colour: the state must not be carried by the glow alone. */
@Composable
private fun StatusPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, PageWallColors.Violet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "ON",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun statusLine(state: ConfigUiState, assignedCount: Int): String = when {
    state.wallpaperActive && state.detectionMode == "TOUCH" ->
        "${state.pageCount} screens · Samsung compatibility"

    state.wallpaperActive && !state.scrollingDetected ->
        "${state.pageCount} screens · swipe your home screen to begin tracking"

    state.wallpaperActive -> "${state.pageCount} screens · offset tracking"
    assignedCount == 0 -> "Choose a picture for each screen, then set it once"
    else -> "$assignedCount of ${state.pageCount} screens ready"
}

@Composable
private fun PageGrid(
    modifier: Modifier,
    state: ConfigUiState,
    thumbnailFor: (PageConfig) -> File?,
    onTapPage: (Int) -> Unit,
    onLongPressPage: (Int) -> Unit,
) {
    val pages = state.pages
    // Two columns keeps the pictures large; three only once there are too many to fit otherwise.
    val columns = if (pages.size <= 6) 2 else 3
    val rows = ceil(pages.size / columns.toFloat()).toInt().coerceAtLeast(1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    if (index < pages.size) {
                        PageCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            page = pages[index],
                            thumbnail = thumbnailFor(pages[index]),
                            isTracked = state.wallpaperActive && state.displayedPage == index,
                            onTap = { onTapPage(index) },
                            onLongPress = { onLongPressPage(index) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One home screen, drawn as a miniature panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCard(
    modifier: Modifier,
    page: PageConfig,
    thumbnail: File?,
    isTracked: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val bitmap by rememberPageBitmap(thumbnail)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(TRANSITION_MS),
        label = "cardPress",
    )

    val description = buildString {
        append("Screen ${page.index + 1}, ")
        append(if (page.hasMedia) page.kindLabel else "empty")
        if (page.hasAudio) append(", has a soundtrack")
        if (isTracked) append(", currently showing")
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isTracked) {
                    Modifier.border(2.dp, PageWallColors.AccentSweep, shape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                }
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .semantics { contentDescription = description },
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Keeps the label legible over a bright picture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        )
                    )
            )
        } else {
            Box(Modifier.fillMaxSize().background(PageWallColors.GlassSheen))
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = PageWallColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page.hasMedia) {
                Badge(Icons.Default.Check, "Configured", PageWallColors.Violet)
            }
            when (page.mediaKind) {
                MediaKind.VIDEO -> if (page.hasMedia) Badge(Icons.Default.PlayCircle, "Video", null)
                MediaKind.GIF -> if (page.hasMedia) Badge(Icons.Default.Gif, "GIF", null)
                MediaKind.IMAGE -> Unit
            }
            if (page.hasAudio) Badge(Icons.Default.MusicNote, "Has soundtrack", null)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                "SCREEN ${page.index + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isTracked) "Showing now" else if (page.hasMedia) page.kindLabel else "Tap to add",
                style = MaterialTheme.typography.bodySmall,
                color = if (isTracked) PageWallColors.Cyan else Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color?,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint ?: Color.White,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun PrimaryActions(
    state: ConfigUiState,
    assignedCount: Int,
    onApplyWallpaper: () -> Unit,
    onChangeMedia: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.wallpaperActive) {
            // Already working: this is a status, not the thing to press next.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, PageWallColors.AccentSweep, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "WALLPAPER ACTIVE",
                    style = MaterialTheme.typography.titleMedium,
                    color = PageWallColors.TextPrimary,
                    maxLines = 1,
                )
            }
        } else {
            Button(
                onClick = onApplyWallpaper,
                enabled = !state.busy && assignedCount > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PageWallColors.Violet),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("SET WALLPAPER", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onChangeMedia,
                enabled = !state.busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Change media", maxLines = 1, overflow = TextOverflow.Ellipsis) }

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Settings", maxLines = 1) }
        }

        if (!state.wallpaperActive && assignedCount > 0) {
            Text(
                "Your phone will ask where to put it — choose Home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = PageWallColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

/** Decodes a card thumbnail off the main thread, re-running when the file behind it changes. */
@Composable
internal fun rememberPageBitmap(file: File?, sampleSize: Int = 4): State<ImageBitmap?> {
    val stamp = file?.lastModified() ?: 0L
    return produceState<ImageBitmap?>(null, file?.path, stamp, sampleSize) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(
                        file.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}
