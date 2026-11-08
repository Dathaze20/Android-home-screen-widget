package com.dathaze.pagewall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dathaze.pagewall.update.AvailableUpdate
import com.dathaze.pagewall.update.UpdateNotice

/**
 * The colours of the strip, lifted from the banner the Music Player Tagger app already uses.
 *
 * Not taken from this app's own palette on purpose. The point of the strip is that it reads as a
 * notice rather than as part of the screen behind it, and this is the same notice, in the same
 * slate-and-teal, as the one on the other app — so anyone who has seen it there knows what it is
 * before reading a word of it.
 */
private object NoticeColors {
    val Ground = Color(0xFF1E293B)
    val Edge = Color(0xFF334155)
    val Title = Color(0xFFE2E8F0)
    val Detail = Color(0xFF94A3B8)
    val Teal = Color(0xFF00A89E)
    val Dismiss = Color(0xFF64748B)
}

/** The teal used for the action and for the dot beside Check for updates, so the two match. */
internal val UpdateAccent = NoticeColors.Teal

/**
 * A strip that says a newer version is out, with the two things to do about it.
 *
 * Shown only once a check has actually found something, so an app that is up to date shows
 * nothing at all. **Update Now** opens the settings sheet on the update row that has always done
 * the downloading — this strip never downloads or installs anything itself, and the download
 * still begins on a press there. The ✕ holds that version back for a day; the dot beside **Check
 * for updates** stays, so dismissing is not the last anyone hears of it.
 *
 * It sits above the action bar rather than floating over it. Floating would have been a closer
 * copy of the web banner it is modelled on, but it would also have covered **Set as wallpaper**
 * on a short screen, and a notice that hides the app's one essential button is worse than a
 * notice that sits a few pixels lower.
 */
@Composable
fun UpdateBanner(
    visible: Boolean,
    update: AvailableUpdate?,
    installedVersionName: String,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && update != null,
        enter = slideInVertically(animationSpec = tween(350)) { it } + fadeIn(tween(350)),
        exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        // Drawn from whatever is in hand, so the strip still has something to show during the
        // exit animation after the state behind it has gone.
        update?.let { shown ->
            NoticeStrip(
                update = shown,
                installedVersionName = installedVersionName,
                onUpdateNow = onUpdateNow,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun NoticeStrip(
    update: AvailableUpdate,
    installedVersionName: String,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Its own margins, so the strip takes up no room at all while it is down.
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NoticeColors.Ground)
            .border(1.dp, NoticeColors.Edge, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "\u21BB ${UpdateNotice.bannerTitle(update.tag)}",
                color = NoticeColors.Title,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Text(
                UpdateNotice.bannerDetail(installedVersionName, update.readableSize),
                color = NoticeColors.Detail,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
            )
        }
        Button(
            onClick = onUpdateNow,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NoticeColors.Teal,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Update Now", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Later",
                tint = NoticeColors.Dismiss,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The dot beside **Check for updates** once something newer is known.
 *
 * Teal, like the strip's action, and it outlives a dismissal: the strip is the news arriving, and
 * this is the news still being true.
 */
@Composable
internal fun UpdateDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(UpdateAccent),
    )
}
