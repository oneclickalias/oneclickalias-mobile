package com.oneclickalias.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaRadius
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Labelled text field like the website's login and register forms: label above,
 * 48dp box with a 2dp border (foreground at 50%, primary when focused,
 * destructive on error), helper or error text below.
 *
 * Pass [textStyle] with `GeistMono` for account numbers and aliases.
 * [contentType] and [sensitive] feed Android autofill (e.g. `ContentType.Password`
 * for the account number); [trailing] sits inside the box, right of the text.
 */
@Composable
fun OcaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentType: ContentType? = null,
    sensitive: Boolean = false,
    fieldModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = OcaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> c.destructive
        focused -> c.primary
        else -> c.foreground.copy(alpha = 0.5f)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = c.foreground),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            modifier = fieldModifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(2.dp, borderColor, RoundedCornerShape(OcaRadius.md))
                .semantics {
                    contentDescription = label
                    if (isError && supportingText != null) error(supportingText)
                    if (contentType != null) this.contentType = contentType
                    if (sensitive) isSensitiveData = true
                },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = OcaSpacing.md, vertical = OcaSpacing.sm),
                        contentAlignment = Alignment.CenterStart
                    ) { inner() }
                    trailing?.invoke()
                }
            }
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) c.destructive else c.mutedForeground
            )
        }
    }
}
