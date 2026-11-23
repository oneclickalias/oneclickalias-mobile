package com.oneclickalias.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * One row of a list (account overview, recipients, devices): title, muted
 * [detail] or [detailContent] below it, [trailing] at the end, at least 64dp
 * high, padded by the page gutter. [onClick] makes the whole row a button.
 * [textSemantics] replaces what screen readers hear for the text part.
 *
 * Pass [trailingIsButton] when [trailing] is a 48dp IconButton: its own
 * padding then stands in for most of the end gutter. [horizontalPadding] 0
 * suits a row inside an already padded column.
 */
@Composable
fun OcaListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailContent: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    titleExtra: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    textSemantics: (SemanticsPropertyReceiver.() -> Unit)? = null,
    trailingIsButton: Boolean = false,
    horizontalPadding: Dp = OcaSpacing.gutter,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 64.dp)
            .padding(
                start = horizontalPadding,
                end = if (trailingIsButton) OcaSpacing.xxs else horizontalPadding,
                top = OcaSpacing.sm,
                bottom = OcaSpacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (textSemantics != null) Modifier.clearAndSetSemantics(textSemantics) else Modifier),
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                titleExtra?.invoke(this)
            }
            when {
                detailContent != null -> detailContent()

                detail != null -> Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = OcaTheme.colors.mutedForeground
                )
            }
        }
        trailing?.invoke()
    }
}
