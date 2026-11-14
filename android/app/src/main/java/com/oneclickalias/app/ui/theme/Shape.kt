package com.oneclickalias.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** `--radius` (0.625rem) and the steps derived from it in index.css. */
object OcaRadius {
    val base = 10.dp
    val sm = base * 0.6f
    val md = base * 0.8f
    val lg = base
    val xl = base * 1.4f
    val xxl = base * 1.8f
}

/**
 * Material shape roles on the radius scale. Note that the website's base-sera
 * style keeps buttons and cards square (rounded-none); OcaButton and OcaCard
 * do the same, rounded shapes are for inputs, dialogs and sheets.
 */
val OcaShapes = Shapes(
    extraSmall = RoundedCornerShape(OcaRadius.sm),
    small = RoundedCornerShape(OcaRadius.md),
    medium = RoundedCornerShape(OcaRadius.lg),
    large = RoundedCornerShape(OcaRadius.xl),
    extraLarge = RoundedCornerShape(OcaRadius.xxl)
)
