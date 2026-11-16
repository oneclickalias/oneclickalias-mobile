package com.oneclickalias.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalOcaColors = staticCompositionLocalOf { DarkTokens }

/**
 * The OneClickAlias theme: fixed brand palette (no dynamic color), always dark
 * regardless of the system setting, Geist / Geist Mono type, flat surfaces.
 */
@Composable
fun OcaTheme(content: @Composable () -> Unit) {
    val tokens = DarkTokens
    CompositionLocalProvider(LocalOcaColors provides tokens) {
        MaterialTheme(
            colorScheme = tokens.toColorScheme(),
            typography = OcaTypography,
            shapes = OcaShapes
        ) {
            // MaterialTheme does not set a content color; without this, text outside
            // a Surface falls back to black and disappears on the dark background.
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
