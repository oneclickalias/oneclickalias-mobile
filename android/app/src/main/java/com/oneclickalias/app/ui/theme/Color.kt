package com.oneclickalias.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The website's design tokens (OCA-frontend/src/index.css, `:root`; the app is
 * dark only like the website), converted from OKLCH to sRGB. Keep the names in sync with the CSS variables.
 */
@Immutable
data class OcaColorTokens(
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

val DarkTokens = OcaColorTokens(
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
fun OcaColorTokens.toColorScheme(): ColorScheme = darkColorScheme().copy(
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
    errorContainer = destructive.copy(alpha = 0.2f),
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
