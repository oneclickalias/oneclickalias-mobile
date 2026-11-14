package com.oneclickalias.app.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing steps on the website's 4px (Tailwind `--spacing`) grid. */
object OcaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal page gutter. */
    val gutter = xl

    /** Content never grows wider than this (tablets, landscape). */
    val maxContentWidth = 480.dp
}
