package com.oneclickalias.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * What an empty screen shows: a quiet icon in a bordered square, a title, one
 * muted sentence and an optional action, centred. The caller places it.
 */
@Composable
fun OcaEmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    val c = OcaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Box(
            modifier = Modifier.size(40.dp).border(1.dp, c.border),
            contentAlignment = Alignment.Center
        ) {
            // Decorative: the title says what is empty.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = c.mutedForeground,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = c.mutedForeground,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            OcaButton(
                text = action,
                onClick = onAction,
                variant = OcaButtonVariant.Outline,
                modifier = Modifier.padding(top = OcaSpacing.xxs)
            )
        }
    }
}
