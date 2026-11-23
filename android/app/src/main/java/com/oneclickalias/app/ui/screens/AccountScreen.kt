package com.oneclickalias.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.aliases.CardGutter
import com.oneclickalias.app.ui.aliases.HomeHeader
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaCard
import com.oneclickalias.app.ui.components.OcaCardSize
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaFlowScaffold
import com.oneclickalias.app.ui.components.OcaListRow
import com.oneclickalias.app.ui.components.OcaSkeleton
import com.oneclickalias.app.ui.components.OcaSkeletonBlock
import com.oneclickalias.app.ui.components.OcaSwitchRow
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/**
 * The "Account" tab of home: the account status (paid until when), then
 * grouped areas (mail, security, account), "Delete account" apart in its own
 * danger zone, then log out and the app version. Same side gutter as the two
 * alias tabs, so the title stays in place when switching tabs.
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
        topBar = { HomeHeader(title = "Account", horizontalPadding = CardGutter) },
        modifier = modifier,
        horizontalPadding = CardGutter,
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
        when {
            state.paidState != null -> StatusCard(state.paidState, onOpenBilling)

            // Keeps the card's place, so the rows below do not jump (or move under a finger) when it arrives.
            !state.statusLoaded -> StatusCardSkeleton()
        }
        Section("Mail") {
            AccountEntry("Recipients", "Addresses your aliases forward to", onOpenRecipients)
        }
        Section("Security") {
            AccountEntry("Devices", "Devices signed in to your account", onOpenDevices)
            onSignInBrowser?.let { AccountEntry("Sign in a browser", "Scan the code on oneclickalias.com", it) }
            AppLockEntry(appLock, onAppLockChange)
        }
        Section("Account") {
            AccountEntry(
                title = "Billing",
                detail = "Opens the website",
                onClick = onOpenBilling,
                icon = R.drawable.ic_open_in_new
            )
        }
        Section("Danger zone") {
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
 * Paid until when, as the website's header says it; an inactive account gets
 * the way to the website's billing page.
 */
@Composable
private fun StatusCard(paid: PaidState, onOpenBilling: () -> Unit) {
    val (title, body, action) = when (paid) {
        is PaidState.Active -> Triple(
            paid.until?.let { "Active until $it" } ?: "Active",
            "You can create and change aliases. Renewals are on the website.",
            null
        )

        is PaidState.Expired -> Triple(
            paid.on?.let { "Expired on $it" } ?: "Expired",
            "Your aliases keep forwarding mail. Creating and changing aliases needs an active account.",
            "Renew on the website"
        )

        PaidState.NotActive -> Triple(
            "Not active yet",
            "Creating and changing aliases needs an active account.",
            "Activate on the website"
        )
    }
    OcaCard(size = OcaCardSize.Small, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
            Eyebrow("Status")
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = OcaTheme.colors.mutedForeground)
        }
        if (action != null) {
            OcaButton(
                text = action,
                onClick = onOpenBilling,
                variant = OcaButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The status card's frame while the status loads, about as tall as the card of a paid account. */
@Composable
private fun StatusCardSkeleton() {
    OcaCard(size = OcaCardSize.Small, modifier = Modifier.fillMaxWidth()) {
        OcaSkeleton(
            description = "Loading the account status",
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Eyebrow("Status")
            Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                OcaSkeletonBlock(height = 16.dp, width = 192.dp)
            }
            Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                OcaSkeletonBlock(height = 12.dp)
            }
            Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                OcaSkeletonBlock(height = 12.dp, width = 160.dp)
            }
        }
    }
}

/** A group of rows under a small uppercase label, with a line above every row. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        Eyebrow(label, modifier = Modifier.padding(bottom = OcaSpacing.xs).semantics { heading() })
        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        content()
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelMedium,
        color = OcaTheme.colors.mutedForeground,
        modifier = modifier
    )
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
    OcaListRow(
        title = title,
        detail = detail,
        titleColor = titleColor,
        onClick = onClick,
        horizontalPadding = 0.dp,
        trailing = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = OcaTheme.colors.mutedForeground,
                modifier = Modifier.size(20.dp)
            )
        }
    )
    HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
}

@Preview(showSystemUi = true)
@Composable
private fun AccountScreenPreview() {
    OcaTheme {
        AccountScreen(
            state = AccountUiState(paidState = PaidState.Active("Dec 10, 2026")),
            versionName = "0.1.0",
            onLogout = {}
        )
    }
}
