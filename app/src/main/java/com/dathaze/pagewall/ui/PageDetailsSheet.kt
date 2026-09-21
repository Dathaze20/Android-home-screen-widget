package com.dathaze.pagewall.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.PageConfig
import java.io.File

/**
 * Everything you can do to one home screen, without guessing which tap does what.
 *
 * Tapping a configured screen used to drop straight into a picker, which made changing a
 * soundtrack or clearing a screen impossible to reach without replacing the picture first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDetailsSheet(
    page: PageConfig,
    thumbnail: File?,
    isTracked: Boolean,
    audioEnabled: Boolean,
    onDismiss: () -> Unit,
    onChangeMedia: () -> Unit,
    onChangeAudio: () -> Unit,
    onRemoveAudio: () -> Unit,
    onClearPage: () -> Unit,
    onSetAsCurrent: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bitmap by rememberPageBitmap(thumbnail, sampleSize = 2)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SCREEN ${page.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PageWallColors.TextSecondary,
                    )
                    Text(
                        if (page.hasMedia) page.kindLabel else "Nothing assigned",
                        style = MaterialTheme.typography.titleMedium,
                        color = PageWallColors.TextPrimary,
                    )
                }
                if (isTracked) {
                    Text(
                        "SHOWING NOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PageWallColors.Cyan,
                    )
                }
            }

            // A preview large enough to actually recognise the picture.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Preview of screen ${page.index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = PageWallColors.TextSecondary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            if (page.hasMedia) {
                SheetAction(Icons.Default.Photo, "Change media", onChangeMedia)
                if (audioEnabled) {
                    SheetAction(
                        icon = Icons.Default.MusicNote,
                        label = if (page.hasAudio) "Change soundtrack" else "Add soundtrack",
                        onClick = onChangeAudio,
                    )
                    if (page.hasAudio) {
                        SheetAction(Icons.Default.MusicNote, "Remove soundtrack", onRemoveAudio)
                    }
                }
                SheetAction(
                    icon = Icons.Default.MyLocation,
                    label = "Set as current tracker screen",
                    subtitle = "Tell the wallpaper this is the screen you are on",
                    onClick = onSetAsCurrent,
                )
                HorizontalDivider()
                SheetAction(
                    icon = Icons.Default.Delete,
                    label = "Clear screen",
                    onClick = onClearPage,
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                SheetAction(Icons.Default.Photo, "Add media", onChangeMedia)
            }
        }
    }
}

/** A 48dp-tall row so every action is comfortably tappable. */
@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    tint: Color? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint ?: PageWallColors.Cyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint ?: PageWallColors.TextPrimary,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PageWallColors.TextSecondary,
                    )
                }
            }
        }
    }
}
