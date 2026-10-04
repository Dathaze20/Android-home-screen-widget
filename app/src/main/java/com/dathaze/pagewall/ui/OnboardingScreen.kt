package com.dathaze.pagewall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dathaze.pagewall.data.PageStore

/**
 * Three steps, shown once.
 *
 * The Samsung sentence is here because the system wallpaper picker offering a single Home screen
 * slot reads as the app having failed, when it is simply how One UI works.
 *
 * The layout earns its keep more than the words do. The steps scroll and the way out is pinned to
 * the bottom, because the previous version was a plain Column: on a phone with the display font
 * turned up, step 3 and the Done button were laid out past the bottom of the screen with no
 * scroll to reach them, and the app could not be got out of at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    pageCount: Int,
    assignedCount: Int,
    wallpaperActive: Boolean,
    onPageCountChange: (Int) -> Unit,
    onChooseMedia: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Always on screen, whatever the font scale does to everything below it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PAGE WALLPAPER",
                    style = MaterialTheme.typography.labelSmall,
                    color = PageWallColors.TextSecondary,
                )
                Text(
                    "Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PageWallColors.TextPrimary,
                )
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (wallpaperActive) "Done" else "Skip") }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "A different picture on every home screen",
                style = MaterialTheme.typography.headlineSmall,
                color = PageWallColors.TextPrimary,
            )

            Step(
                number = 1,
                title = "How many home screens do you have?",
                done = true,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (PageStore.MIN_PAGES..PageStore.MAX_PAGES).forEach { count ->
                        FilterChip(
                            selected = pageCount == count,
                            onClick = { onPageCountChange(count) },
                            label = { Text("$count", maxLines = 1) },
                        )
                    }
                }
            }

            Step(
                number = 2,
                title = "Choose a picture for each screen",
                done = assignedCount > 0,
            ) {
                Text(
                    if (assignedCount == 0) {
                        "Pick several at once and they land on screens 1, 2, 3 and so on."
                    } else {
                        "$assignedCount of $pageCount ready. You can change any of them later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PageWallColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onChooseMedia,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PageWallColors.Violet),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (assignedCount == 0) "Choose pictures" else "Choose more") }
            }

            Step(
                number = 3,
                title = "Set it as your wallpaper",
                done = wallpaperActive,
            ) {
                Text(
                    "Samsung shows one wallpaper slot. That is normal — Page Wallpaper changes " +
                        "the picture itself as you move between screens. Choose Home screen when asked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PageWallColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onApplyWallpaper,
                    enabled = assignedCount > 0 && !wallpaperActive,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PageWallColors.Violet),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (wallpaperActive) "Already active" else "Set wallpaper") }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        // Pinned, not the last child of a scrolling column: this is the way out, and it has to be
        // reachable without depending on anything above it fitting.
        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PageWallColors.Violet),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .heightIn(min = 52.dp),
        ) {
            Text(
                if (wallpaperActive) "Done — open my screens" else "Go to my screens",
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    done: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (done) PageWallColors.Violet else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (done) Color.White else PageWallColors.TextSecondary,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = PageWallColors.TextPrimary,
            )
        }
        content()
    }
}
