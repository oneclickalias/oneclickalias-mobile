package com.oneclickalias.app.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.oneclickalias.app.platform.SecureWindow
import com.oneclickalias.app.ui.components.NoPersonalizedLearning
import com.oneclickalias.app.ui.components.OcaTextField
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/**
 * The account-number input, used wherever the number is typed (login, sign out
 * all devices, delete account). It behaves like a password field, as on the
 * website: masked by default with a show/hide toggle, autofill content type
 * `Password`, number-password keyboard (no suggestions, no autocorrect, no
 * personalised learning), grouped in fours. While the number is revealed the
 * window is secure (`FLAG_SECURE`).
 *
 * [digits] must come from a ViewModel, never from saved instance state.
 * [hint] is the line under the field; null leaves it out.
 */
@Composable
fun AccountNumberField(
    digits: String,
    onInput: (String) -> Unit,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    hint: String? = "16 digits"
) {
    SecureWindow(enabled = revealed && digits.isNotEmpty())
    NoPersonalizedLearning {
        OcaTextField(
            value = digits,
            onValueChange = onInput,
            label = "Account number",
            supportingText = hint,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            visualTransformation = AccountNumberVisualTransformation(masked = !revealed),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = GeistMono, letterSpacing = 0.05.em),
            contentType = ContentType.Password,
            sensitive = true,
            modifier = modifier,
            fieldModifier = fieldModifier,
            trailing = { RevealToggle(revealed = revealed, onToggle = onToggleReveal) }
        )
    }
}

@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (revealed) "Hide account number" else "Show account number" }
            .padding(horizontal = OcaSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = (if (revealed) "Hide" else "Show").uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            color = OcaTheme.colors.primary
        )
    }
}
