package com.oneclickalias.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The website's design tokens (OCA-frontend/src/index.css, `:root` and `.dark`),
 * converted from OKLCH to sRGB. Keep the names in sync with the CSS variables.
 */
@Immutable
data class OcaColorTokens(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    val ring: Color
)

val LightTokens = OcaColorTokens(
    isDark = false,
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF09090B),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF09090B),
    primary = Color(0xFF007A55),
    primaryForeground = Color(0xFFECFDF5),
    secondary = Color(0xFFF4F4F5),
    secondaryForeground = Color(0xFF18181B),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717B),
    accent = Color(0xFFF4F4F5),
    accentForeground = Color(0xFF18181B),
    destructive = Color(0xFFE7000B),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    ring = Color(0xFF9F9FA9)
)

val DarkTokens = OcaColorTokens(
    isDark = true,
    background = Color(0xFF09090B),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF18181B),
    cardForeground = Color(0xFFFAFAFA),
    primary = Color(0xFF006045),
    primaryForeground = Color(0xFFECFDF5),
    secondary = Color(0xFF27272A),
    secondaryForeground = Color(0xFFFAFAFA),
    muted = Color(0xFF27272A),
    mutedForeground = Color(0xFF9F9FA9),
    accent = Color(0xFF27272A),
    accentForeground = Color(0xFFFAFAFA),
    destructive = Color(0xFFFF6467),
    // oklch(1 0 0 / 10%) and / 15%: white with alpha, like the website.
    border = Color(0x1AFFFFFF),
    input = Color(0x26FFFFFF),
    ring = Color(0xFF71717B)
)

/**
 * Maps the tokens onto Material 3 roles so stock Material components pick up the
 * brand palette. Every surface container is the flat `card` color and
 * `surfaceTint` equals the background, so tonal elevation never tints anything.
 */
fun OcaColorTokens.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = primaryForeground,
        primaryContainer = primary,
        onPrimaryContainer = primaryForeground,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = secondaryForeground,
        secondaryContainer = secondary,
        onSecondaryContainer = secondaryForeground,
        tertiary = accent,
        onTertiary = accentForeground,
        tertiaryContainer = accent,
        onTertiaryContainer = accentForeground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceTint = background,
        inverseSurface = foreground,
        inverseOnSurface = background,
        error = destructive,
        onError = background,
        errorContainer = destructive.copy(alpha = if (isDark) 0.2f else 0.1f),
        onErrorContainer = destructive,
        outline = border,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = card,
        surfaceDim = background,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card
    )
}
