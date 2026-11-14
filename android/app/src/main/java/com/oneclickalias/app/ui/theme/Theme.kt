package com.oneclickalias.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalOcaColors = staticCompositionLocalOf { LightTokens }

/**
 * The OneClickAlias theme: fixed brand palette (no dynamic color), light or dark
 * following the system, Geist / Geist Mono type, flat surfaces.
 */
@Composable
fun OcaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    CompositionLocalProvider(LocalOcaColors provides tokens) {
        MaterialTheme(
            colorScheme = tokens.toColorScheme(),
            typography = OcaTypography,
            shapes = OcaShapes
        ) {
            // MaterialTheme does not set a content color; without this, text outside
            // a Surface falls back to black and disappears in dark mode.
            CompositionLocalProvider(LocalContentColor provides tokens.foreground, content = content)
        }
    }
}

object OcaTheme {
    /** The raw website tokens, for roles Material has no slot for (input, ring, ...). */
    val colors: OcaColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalOcaColors.current
}
