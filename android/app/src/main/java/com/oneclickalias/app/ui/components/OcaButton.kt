package com.oneclickalias.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/** Mirrors the website's button variants (OCA-frontend/src/components/ui/button.tsx). */
enum class OcaButtonVariant { Primary, Secondary, Outline, Destructive, Ghost }

object OcaButtonDefaults {
    /** 48dp instead of the website's 40px: the Android minimum touch target. */
    val MinHeight = 48.dp
    val ContentPadding = PaddingValues(horizontal = OcaSpacing.xl)
}

/**
 * Square, flat button with uppercase, widely tracked label, like base-sera on
 * the website. No elevation, no shadow; [OcaButtonVariant.Outline] draws a 1dp
 * border in the `border` token.
 */
@Composable
fun OcaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OcaButtonVariant = OcaButtonVariant.Primary,
    enabled: Boolean = true
) {
    val c = OcaTheme.colors
    val (container, content) = when (variant) {
        OcaButtonVariant.Primary -> c.primary to c.primaryForeground
        OcaButtonVariant.Secondary -> c.secondary to c.secondaryForeground
        OcaButtonVariant.Outline -> Color.Transparent to c.foreground
        OcaButtonVariant.Destructive -> c.destructive.copy(alpha = if (c.isDark) 0.2f else 0.1f) to c.destructive
        OcaButtonVariant.Ghost -> Color.Transparent to c.foreground
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = OcaButtonDefaults.MinHeight),
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // disabled:opacity-50
            disabledContainerColor = container.copy(alpha = container.alpha * 0.5f),
            disabledContentColor = content.copy(alpha = 0.5f)
        ),
        elevation = null,
        border = if (variant == OcaButtonVariant.Outline) BorderStroke(1.dp, c.border) else null,
        contentPadding = OcaButtonDefaults.ContentPadding
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
private fun OcaButtonPreview() {
    OcaTheme {
        OcaButton(text = "Create account", onClick = {})
    }
}
