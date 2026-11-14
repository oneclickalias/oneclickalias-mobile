package com.oneclickalias.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.aliases.HomeHeader
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaFlowScaffold
import com.oneclickalias.app.ui.components.OcaSwitchRow
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * The "Account" tab of home: recipients, devices, app lock, billing (website)
 * and account deletion, then log out and the app version.
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    versionName: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecipients: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    /** "Sign in a browser"; null hides it (no camera). */
    onSignInBrowser: (() -> Unit)? = null,
    onOpenBilling: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    appLock: AppLockState = AppLockState(ready = true, canAuthenticate = true),
    onAppLockChange: (Boolean) -> Unit = {}
) {
    OcaFlowScaffold(
        topBar = { HomeHeader(title = "Account") },
        modifier = modifier,
        bottom = {
            state.message?.let { OcaErrorText(it) }
            OcaButton(
                text = if (state.loggingOut) "Logging out…" else "Log out",
                onClick = onLogout,
                enabled = !state.loggingOut,
                variant = OcaButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = OcaTheme.colors.mutedForeground,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    ) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
            AccountEntry("Recipients", "Addresses your aliases forward to", onOpenRecipients)
            AccountEntry("Devices", "Devices signed in to your account", onOpenDevices)
            onSignInBrowser?.let { AccountEntry("Sign in a browser", "Scan the code on oneclickalias.com", it) }
            AppLockEntry(appLock, onAppLockChange)
            AccountEntry(
                title = "Billing",
                detail = "Opens the website",
                onClick = onOpenBilling,
                icon = R.drawable.ic_open_in_new
            )
            AccountEntry(
                title = "Delete account",
                detail = "Deletes the account with all aliases and recipients",
                onClick = onDeleteAccount,
                titleColor = OcaTheme.colors.destructive
            )
        }
    }
}

/**
 * App lock switch. Turning it on runs the system prompt first (in [AppLockState.prompting]);
 * without a screen lock on the phone it cannot be turned on, but can still be turned off.
 */
@Composable
private fun AppLockEntry(appLock: AppLockState, onChange: (Boolean) -> Unit) {
    OcaSwitchRow(
        title = "App lock",
        detail = if (appLock.canAuthenticate || appLock.enabled) {
            "Asks for your fingerprint, face or screen lock when you open the app."
        } else {
            "Set up a screen lock on this phone to use app lock."
        },
        checked = appLock.enabled,
        enabled = !appLock.prompting && (appLock.enabled || appLock.canAuthenticate),
        onChange = onChange,
        modifier = Modifier.padding(vertical = OcaSpacing.sm)
    )
    HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
}

/** One row of the overview: title, muted detail, trailing chevron (or [icon]). */
@Composable
private fun AccountEntry(
    title: String,
    detail: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_chevron_right,
    titleColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = OcaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = OcaTheme.colors.mutedForeground)
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = OcaTheme.colors.mutedForeground,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
}

@Preview(showSystemUi = true)
@Composable
private fun AccountScreenPreview() {
    OcaTheme { AccountScreen(state = AccountUiState(), versionName = "0.1.0", onLogout = {}) }
}
