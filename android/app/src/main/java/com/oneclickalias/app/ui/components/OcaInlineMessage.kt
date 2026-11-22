package com.oneclickalias.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import kotlinx.coroutines.delay

/** Kind of an [OcaInlineMessage]: success and info close themselves, an error stays. */
enum class OcaMessageTone { Success, Info, Error }

/** How long a success message stays. */
const val OCA_MESSAGE_MILLIS = 3_000L

/**
 * One feedback line in a pinned bottom area: icon, at most two lines of text
 * and an optional trailing action (e.g. Retry). A success or info line calls
 * [onTimeout] after [OCA_MESSAGE_MILLIS]; an error stays until the next
 * action. Screen readers announce it when it appears.
 */
@Composable
fun OcaInlineMessage(
    text: String,
    tone: OcaMessageTone,
    modifier: Modifier = Modifier,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    onTimeout: (() -> Unit)? = null
) {
    val c = OcaTheme.colors
    val color = if (tone == OcaMessageTone.Error) c.destructive else c.mutedForeground
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
    ) {
        // Decorative: the text carries the message.
        Icon(
            painter = painterResource(
                when (tone) {
                    OcaMessageTone.Success -> R.drawable.ic_check
                    OcaMessageTone.Info -> R.drawable.ic_info
                    OcaMessageTone.Error -> R.drawable.ic_error
                }
            ),
            contentDescription = null,
            tint = when (tone) {
                OcaMessageTone.Success -> c.primary
                OcaMessageTone.Info -> c.mutedForeground
                OcaMessageTone.Error -> c.destructive
            },
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (tone == OcaMessageTone.Error) FontWeight.Medium else FontWeight.Normal
            ),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            OcaButton(text = action, onClick = onAction, variant = OcaButtonVariant.Ghost, enabled = actionEnabled)
        }
    }
    if (tone != OcaMessageTone.Error && onTimeout != null) {
        LaunchedEffect(text) {
            delay(OCA_MESSAGE_MILLIS)
            onTimeout()
        }
    }
}
