package com.oneclickalias.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/** [OcaBadge] colors: quiet for neutral facts, the primary icon for a good state. */
enum class OcaBadgeTone { Neutral, Primary }

/**
 * Small square label with a 1dp border for a state (confirmed, pending, this
 * device, a count): uppercase, tracked, optional leading icon.
 */
@Composable
fun OcaBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: OcaBadgeTone = OcaBadgeTone.Neutral,
    @DrawableRes icon: Int? = null
) {
    val c = OcaTheme.colors
    val textColor = if (tone == OcaBadgeTone.Primary) c.foreground else c.mutedForeground
    Row(
        modifier = modifier
            .border(1.dp, c.border)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
    ) {
        if (icon != null) {
            // Decorative: the text says the same.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (tone == OcaBadgeTone.Primary) c.primary else c.mutedForeground,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing
            ),
            color = textColor,
            maxLines = 1
        )
    }
}
