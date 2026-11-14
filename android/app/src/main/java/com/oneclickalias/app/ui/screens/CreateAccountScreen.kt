package com.oneclickalias.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.platform.SecureWindow
import com.oneclickalias.app.ui.account.AccountNumberDisplay
import com.oneclickalias.app.ui.components.OcaBackBar
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaCheckboxRow
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaFlowScaffold
import com.oneclickalias.app.ui.components.OcaScreenHeading
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/**
 * Sign-up: shows the new account number once, then confirm and log in.
 *
 * @param onSaveToPasswordManager hands the number to the password manager
 *   (autofill save dialog); null hides the button (no password manager set up).
 */
@Composable
fun CreateAccountScreen(
    state: CreateAccountUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onSavedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onLogIn: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveToPasswordManager: (() -> Unit)? = null
) {
    // No screenshots or app-switcher thumbnail while the number is on screen.
    SecureWindow(enabled = state is CreateAccountUiState.Ready)
    OcaFlowScaffold(
        topBar = { OcaBackBar(onBack = onBack) },
        modifier = modifier,
        bottom = {
            when (state) {
                is CreateAccountUiState.Ready -> {
                    OcaCheckboxRow(
                        checked = state.saved,
                        onCheckedChange = onSavedChange,
                        text = "I saved my account number",
                        enabled = !state.submitting
                    )
                    OcaButton(
                        text = if (state.submitting) "Logging in…" else "Continue",
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { OcaErrorText(it) }
                }

                is CreateAccountUiState.CreateFailed ->
                    OcaButton(
                        text = "Try again",
                        onClick = onRetry,
                        variant = OcaButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                CreateAccountUiState.Interrupted -> {
                    OcaButton(text = "Log in", onClick = onLogIn, modifier = Modifier.fillMaxWidth())
                    OcaButton(
                        text = "Create a new account",
                        onClick = onStartOver,
                        variant = OcaButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CreateAccountUiState.Creating -> Unit
            }
        }
    ) {
        when (state) {
            CreateAccountUiState.Creating -> {
                OcaScreenHeading(eyebrow = "Your account", title = "Creating your account number")
                CircularProgressIndicator(
                    color = OcaTheme.colors.primary,
                    modifier = Modifier.semantics { contentDescription = "Creating your account" }
                )
            }

            is CreateAccountUiState.CreateFailed -> {
                OcaScreenHeading(eyebrow = "Your account", title = "Creating your account number")
                OcaErrorText(state.message)
            }

            is CreateAccountUiState.Ready -> {
                OcaScreenHeading(eyebrow = "Your account", title = "This is your account number")
                AccountNumberDisplay(digits = state.accountNumber, onCopy = onCopy, copied = state.copied)
                // The main way to keep the number: copying stays as the icon in the frame.
                if (onSaveToPasswordManager != null) {
                    OcaButton(
                        text = "Save to password manager",
                        onClick = onSaveToPasswordManager,
                        enabled = !state.submitting,
                        variant = OcaButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SaveWarning()
            }

            CreateAccountUiState.Interrupted -> {
                OcaScreenHeading(eyebrow = "Your account", title = "Your account number is no longer shown")
                Text(
                    text = "The app was closed while your account number was on screen. For your security " +
                        "it is never stored on this device, so it can't be shown again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "If you saved it, log in with it. Otherwise, create a new account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The one thing to understand on this screen, framed like the website's notices. */
@Composable
private fun SaveWarning() {
    val c = OcaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, c.destructive.copy(alpha = 0.4f), RectangleShape)
            .padding(OcaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
    ) {
        Text(
            text = "Save it now".uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            color = c.destructive
        )
        Text(
            text = "Your account number is the only way to log in. We can't show it again or recover it — " +
                "without it, your account is lost. Keep it in a password manager or write it down.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CreateAccountScreenPreview() {
    OcaTheme {
        CreateAccountScreen(
            state = CreateAccountUiState.Ready("4711000012345678"),
            onBack = {},
            onRetry = {},
            onCopy = {},
            onSavedChange = {},
            onSubmit = {},
            onLogIn = {},
            onStartOver = {}
        )
    }
}
