package com.dathaze.pagewall.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A dark-first control-panel palette.
 *
 * Deep charcoal ground, near-black surfaces lifted by a hair of blue, and electric violet through
 * cyan reserved for whatever is active. Dynamic colour is deliberately not used here: the app is
 * a control surface for pictures, and letting it borrow the wallpaper's own colours made it
 * compete with the thumbnails it exists to show.
 */
object PageWallColors {
    val Ground = Color(0xFF07080C)
    val Surface = Color(0xFF101219)
    val SurfaceRaised = Color(0xFF171A24)
    val Outline = Color(0xFF262A38)

    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF4C7DF0)
    val Cyan = Color(0xFF22D3EE)

    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9BA3B7)
    val Danger = Color(0xFFF2555A)

    /** For the accent hairline on an active card. Restrained: two stops, no rainbow. */
    val AccentSweep = Brush.linearGradient(listOf(Violet, Cyan))

    /** A barely-there lift behind glass surfaces, so depth reads without a visible gradient. */
    val GlassSheen = Brush.verticalGradient(
        listOf(Color(0x14FFFFFF), Color(0x05FFFFFF), Color(0x00FFFFFF)),
    )
}

private val Scheme = darkColorScheme(
    primary = PageWallColors.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFD9CCFF),
    secondary = PageWallColors.Cyan,
    onSecondary = Color(0xFF00242B),
    secondaryContainer = Color(0xFF14323A),
    onSecondaryContainer = Color(0xFFAEEFFB),
    tertiary = PageWallColors.Blue,
    background = PageWallColors.Ground,
    onBackground = PageWallColors.TextPrimary,
    surface = PageWallColors.Surface,
    onSurface = PageWallColors.TextPrimary,
    surfaceVariant = PageWallColors.SurfaceRaised,
    onSurfaceVariant = PageWallColors.TextSecondary,
    outline = PageWallColors.Outline,
    outlineVariant = Color(0xFF1C2130),
    error = PageWallColors.Danger,
    onError = Color.White,
    errorContainer = Color(0xFF3A1417),
    onErrorContainer = Color(0xFFFFB4B6),
)

/**
 * Tight, slightly wide-tracked type. Sizes come from the Material scale so system font scaling
 * still applies; only weight and letter spacing are adjusted.
 */
private val PageWallTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

/**
 * Always dark. [isSystemInDarkTheme] is read only so a future light variant has a hook; the
 * design is dark-first by intent, not by following the system.
 */
@Composable
fun PageWallTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = PageWallTypography, content = content)
}
