package com.oneclickalias.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeAndShapeTest {
    @Test
    fun `headings use geist mono, body and labels use geist`() {
        val t = OcaTypography
        listOf(t.displayLarge, t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge)
            .forEach { assertEquals(GeistMono, it.fontFamily) }
        listOf(t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium)
            .forEach { assertEquals(Geist, it.fontFamily) }
    }

    @Test
    fun `radius scale follows the css radius tokens`() {
        assertEquals(10.dp, OcaRadius.base)
        assertEquals(6.dp, OcaRadius.sm)
        assertEquals(8.dp, OcaRadius.md)
        assertEquals(10.dp, OcaRadius.lg)
        assertEquals(14.dp, OcaRadius.xl)
    }
}
