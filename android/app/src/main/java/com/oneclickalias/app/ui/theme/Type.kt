package com.oneclickalias.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oneclickalias.app.R

// Geist and Geist Mono ship as bundled variable fonts (res/font, SIL OFL 1.1,
// license in assets/licenses). Each weight is one instance of the wght axis.
private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private fun variableFamily(resId: Int) = FontFamily(
    weights.map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

/** Body text, like `--font-sans` on the website. */
val Geist: FontFamily = variableFamily(R.font.geist)

/** Headings, account numbers and aliases, like `--font-heading` / `--font-mono`. */
val GeistMono: FontFamily = variableFamily(R.font.geist_mono)

private fun heading(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = GeistMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

private fun body(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

/** Sizes follow the website's Tailwind scale (text-xs ... text-5xl). */
val OcaTypography = Typography(
    displayLarge = heading(48, 60, FontWeight.Bold),
    displayMedium = heading(36, 44, FontWeight.Bold),
    displaySmall = heading(30, 36),
    headlineLarge = heading(30, 36),
    headlineMedium = heading(24, 32),
    headlineSmall = heading(20, 28),
    titleLarge = heading(18, 28),
    titleMedium = heading(16, 24, FontWeight.Medium),
    titleSmall = heading(14, 20, FontWeight.Medium),
    // leading-relaxed for running text, as on the website
    bodyLarge = body(16, 26),
    bodyMedium = body(14, 22),
    bodySmall = body(12, 16),
    // Button text: text-xs font-semibold tracking-widest (uppercase is applied by OcaButton)
    labelLarge = body(12, 16, FontWeight.SemiBold).copy(letterSpacing = 0.1.em),
    labelMedium = body(12, 16, FontWeight.Medium).copy(letterSpacing = 0.025.em),
    labelSmall = body(11, 16, FontWeight.Medium).copy(letterSpacing = 0.025.em)
)
