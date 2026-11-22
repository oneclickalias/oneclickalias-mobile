package com.oneclickalias.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.account.AccountNumberField
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaCheckboxRow
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaNotice
import com.oneclickalias.app.ui.components.Wordmark
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/** Below this height (small phone, or the keyboard is open) the page drops what is not needed to log in. */
private val CompactHeight = 520.dp

/**
 * The first screen without a session: log in with the account number, scan a
 * code from the dashboard, or create an account (at the bottom of the page).
 *
 * The number field behaves like a password, as on the website
 * (`type=password`, `autocomplete=current-password`): masked by default,
 * autofill content type `Password`, number-password keyboard (no suggestions,
 * no autocorrect, no personalised learning). It does not take the focus by
 * itself, so the keyboard stays closed and the other ways in stay visible;
 * the keyboard's Done key logs in. Autofill is committed only after a
 * successful login, so password managers offer to save the number then, and
 * the session is cancelled when the screen is left without one.
 *
 * @param notice why the user just landed here signed out, if they did.
 * @param onScanCode opens the QR login; null hides it (phone without a camera).
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onInput: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onRevokeAllAndLogIn: () -> Unit = {},
    onScanCode: (() -> Unit)? = null
) {
    val autofill = LocalAutofillManager.current
    val signedIn by rememberUpdatedState(state.signedIn)
    LaunchedEffect(state.signedIn) { if (state.signedIn) autofill?.commit() }
    DisposableEffect(autofill) { onDispose { if (!signedIn) autofill?.cancel() } }

    val busy = state.submitting || state.signedIn
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Includes the keyboard: the page ends right above it.
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        val compact = maxHeight < CompactHeight
        Column(
            modifier = Modifier
                .widthIn(max = OcaSpacing.maxContentWidth)
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OcaSpacing.gutter, vertical = if (compact) OcaSpacing.md else OcaSpacing.xl),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo and form together at the top, "Create account" at the bottom: no empty band
            // between the name and the field on a tall phone.
            Column {
                Column(
                    modifier = Modifier.padding(top = if (compact) OcaSpacing.xxs else OcaSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
                ) {
                    Wordmark(modifier = Modifier.semantics { heading() })
                    if (!compact) {
                        Text(
                            text = "Email aliases in one click. No username, no password.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(vertical = if (compact) OcaSpacing.md else OcaSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
                ) {
                    if (notice != null) SignedOutNotice(notice)
                    AccountNumberField(
                        digits = state.digits,
                        onInput = onInput,
                        revealed = state.revealed,
                        onToggleReveal = onToggleReveal,
                        enabled = !busy,
                        onDone = { if (state.canSubmit) onSubmit() },
                        hint = null
                    )
                    if (state.needsConfirmation) {
                        Text(
                            text = AuthMessages.CONFIRM_TO_CONTINUE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OcaTheme.colors.mutedForeground,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        OcaCheckboxRow(
                            checked = state.confirmed,
                            onCheckedChange = onConfirmedChange,
                            text = "I saved my account number",
                            enabled = !state.submitting
                        )
                    }
                    // Next to the button, so both stay in view above the keyboard.
                    state.error?.let { OcaErrorText(it) }
                    OcaButton(
                        text = when {
                            busy -> "Logging in…"
                            state.needsConfirmation -> "Confirm and log in"
                            else -> "Log in"
                        },
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.deviceLimitReached) {
                        // Website wording (OCA-frontend/src/login-card.tsx).
                        OcaNotice(
                            title = "Device limit reached",
                            body = "Every device slot for this account is in use. You can sign out all devices — " +
                                "including any you can't physically reach — and then log in again on this one.",
                            action = if (state.submitting) {
                                "Signing out all devices…"
                            } else {
                                "Sign out all devices & log in"
                            },
                            onAction = onRevokeAllAndLogIn,
                            actionVariant = OcaButtonVariant.Destructive,
                            actionEnabled = state.canSubmit
                        )
                    }
                    if (onScanCode != null) {
                        // QR login: signed in on the website, no number to type.
                        OrLine()
                        OcaButton(
                            text = "Scan code from dashboard",
                            onClick = onScanCode,
                            enabled = !busy,
                            variant = OcaButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
                Text(
                    text = "No account yet?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OcaTheme.colors.mutedForeground
                )
                OcaButton(
                    text = "Create account",
                    onClick = onCreateAccount,
                    enabled = !busy,
                    variant = OcaButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Bordered box with the reason for being signed out; announced when it appears. */
@Composable
private fun SignedOutNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OcaTheme.colors.border, RectangleShape)
            .padding(OcaSpacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** A line with "or" in the middle, between the two ways to log in. */
@Composable
private fun OrLine() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = OcaTheme.colors.border)
        Text(text = "or", style = MaterialTheme.typography.bodySmall, color = OcaTheme.colors.mutedForeground)
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = OcaTheme.colors.border)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    OcaTheme {
        LoginScreen(
            state = LoginUiState(digits = "47110000"),
            onInput = {},
            onToggleReveal = {},
            onConfirmedChange = {},
            onSubmit = {},
            onCreateAccount = {},
            onScanCode = {}
        )
    }
}
