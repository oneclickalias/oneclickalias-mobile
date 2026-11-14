package com.oneclickalias.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/** `--card-spacing` on the website: 32px by default, 20px for `size="sm"`. */
enum class OcaCardSize(val spacing: Dp) {
    Default(OcaSpacing.xxl),
    Small(OcaSpacing.lg)
}

/**
 * Flat card: `card` surface with a 1dp `border` outline, square corners, no
 * shadow and no tonal elevation. Children are spaced like the website card.
 */
@Composable
fun OcaCard(
    modifier: Modifier = Modifier,
    size: OcaCardSize = OcaCardSize.Default,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = OcaTheme.colors
    Surface(
        modifier = modifier,
        shape = RectangleShape,
        color = c.card,
        contentColor = c.cardForeground,
        border = BorderStroke(1.dp, c.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(size.spacing),
            verticalArrangement = Arrangement.spacedBy(size.spacing),
            content = content
        )
    }
}
