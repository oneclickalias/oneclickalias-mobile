package com.oneclickalias.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.tooling.preview.Preview
import com.oneclickalias.app.ui.account.AccountNumberField
import com.oneclickalias.app.ui.components.OcaBackBar
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaFlowScaffold
import com.oneclickalias.app.ui.components.OcaScreenHeading
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/** Texts of one [NumberConfirmScreen]. */
data class NumberConfirmTexts(
    val eyebrow: String,
    val title: String,
    val paragraphs: List<String>,
    val button: String,
    val busyButton: String
) {
    companion object {
        /** Website wording (OCA-frontend/src/revoke-all-dialog.tsx). */
        val RevokeAll = NumberConfirmTexts(
            eyebrow = "Devices",
            title = "Sign out all devices",
            paragraphs = listOf(
                "This signs out every device on your account, including this phone. You'll need your account " +
                    "number to log back in.",
                "Enter it below to confirm."
            ),
            button = "Sign out all devices",
            busyButton = "Signing out…"
        )

        /** Website wording (OCA-frontend/src/delete-account-dialog.tsx). */
        val DeleteAccount = NumberConfirmTexts(
            eyebrow = "Account",
            title = "Delete account",
            paragraphs = listOf(
                "Your account will be permanently deleted in 12 hours, together with all aliases and recipients. " +
                    "Your aliases keep working until then.",
                "All devices are signed out right away. If you log back in within 12 hours, the deletion is " +
                    "cancelled; after that it can't be undone.",
                "Enter your account number to confirm."
            ),
            button = "Delete account",
            busyButton = "Deleting…"
        )
    }
}

/**
 * A destructive step confirmed with the account number (sign out all devices,
 * delete account). A full screen rather than a sheet: the number field needs
 * the activity window for `FLAG_SECURE`. Autofill may fill the number but is
 * never asked to save it here.
 */
@Composable
fun NumberConfirmScreen(
    texts: NumberConfirmTexts,
    state: NumberConfirmUiState,
    onInput: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autofill = LocalAutofillManager.current
    DisposableEffect(autofill) { onDispose { autofill?.cancel() } }
    OcaFlowScaffold(
        topBar = { OcaBackBar(onBack = onBack) },
        modifier = modifier,
        bottom = {
            state.error?.let { OcaErrorText(it) }
            OcaButton(
                text = if (state.submitting) texts.busyButton else texts.button,
                onClick = onSubmit,
                enabled = state.canSubmit,
                variant = OcaButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        OcaScreenHeading(eyebrow = texts.eyebrow, title = texts.title)
        Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
            texts.paragraphs.forEach {
                Text(text = it, style = MaterialTheme.typography.bodyLarge, color = OcaTheme.colors.mutedForeground)
            }
        }
        AccountNumberField(
            digits = state.digits,
            onInput = onInput,
            revealed = state.revealed,
            onToggleReveal = onToggleReveal,
            enabled = !state.submitting,
            onDone = { if (state.canSubmit) onSubmit() }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun NumberConfirmScreenPreview() {
    OcaTheme {
        NumberConfirmScreen(
            texts = NumberConfirmTexts.DeleteAccount,
            state = NumberConfirmUiState(digits = "47110000"),
            onInput = {},
            onToggleReveal = {},
            onSubmit = {},
            onBack = {}
        )
    }
}
