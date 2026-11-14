package com.oneclickalias.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Calm card with a title, a muted explanation and one full-width action, e.g.
 * the payment notice. Screen readers announce it when it appears.
 */
@Composable
fun OcaNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionVariant: OcaButtonVariant = OcaButtonVariant.Outline,
    actionEnabled: Boolean = true
) {
    OcaCard(size = OcaCardSize.Small, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = OcaTheme.colors.mutedForeground)
        }
        OcaButton(
            text = action,
            onClick = onAction,
            variant = actionVariant,
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
