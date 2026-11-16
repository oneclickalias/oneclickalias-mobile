package com.oneclickalias.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hex values are the sRGB conversion of the OKLCH tokens in OCA-frontend/src/index.css. */
class ColorTokensTest {
    @Test
    fun `dark tokens match the website`() {
        val t = DarkTokens
        assertEquals(Color(0xFF09090B), t.background)
        assertEquals(Color(0xFFFAFAFA), t.foreground)
        assertEquals(Color(0xFF18181B), t.card)
        assertEquals(Color(0xFF006045), t.primary)
        assertEquals(Color(0xFFECFDF5), t.primaryForeground)
        assertEquals(Color(0xFF27272A), t.secondary)
        assertEquals(Color(0xFFFAFAFA), t.secondaryForeground)
        assertEquals(Color(0xFF27272A), t.muted)
        assertEquals(Color(0xFF9F9FA9), t.mutedForeground)
        assertEquals(Color(0xFFFF6467), t.destructive)
        assertEquals(Color(0x1AFFFFFF), t.border)
        assertEquals(Color(0x26FFFFFF), t.input)
        assertEquals(Color(0xFF71717B), t.ring)
    }

    @Test
    fun `color scheme maps tokens onto material roles`() {
        val t = DarkTokens
        val s = t.toColorScheme()
        assertEquals(t.background, s.background)
        assertEquals(t.foreground, s.onBackground)
        assertEquals(t.background, s.surface)
        assertEquals(t.foreground, s.onSurface)
        assertEquals(t.card, s.surfaceContainer)
        assertEquals(t.card, s.surfaceContainerHigh)
        assertEquals(t.primary, s.primary)
        assertEquals(t.primaryForeground, s.onPrimary)
        assertEquals(t.secondary, s.secondary)
        assertEquals(t.secondaryForeground, s.onSecondary)
        assertEquals(t.muted, s.surfaceVariant)
        assertEquals(t.mutedForeground, s.onSurfaceVariant)
        assertEquals(t.destructive, s.error)
        assertEquals(t.border, s.outline)
        assertEquals(t.border, s.outlineVariant)
        // No tonal elevation tint: surfaces stay flat.
        assertEquals(t.background, s.surfaceTint)
    }
}
