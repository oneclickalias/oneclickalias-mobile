package com.oneclickalias.app.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.components.OcaBadge
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaInlineMessage
import com.oneclickalias.app.ui.components.OcaListError
import com.oneclickalias.app.ui.components.OcaListRow
import com.oneclickalias.app.ui.components.OcaListScaffold
import com.oneclickalias.app.ui.components.OcaListStatusLine
import com.oneclickalias.app.ui.components.OcaMessageTone
import com.oneclickalias.app.ui.components.OcaSheet
import com.oneclickalias.app.ui.components.OcaSkeletonRows
import com.oneclickalias.app.ui.components.listItemWidth
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.auth.Device
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.delay

/** Everything the device screen can ask for; the ViewModel provides the real ones. */
data class DevicesCallbacks(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRequestSignOut: (deviceId: String) -> Unit = {},
    val onDismissSignOut: () -> Unit = {},
    val onConfirmSignOut: () -> Unit = {},
    val onSignOutAll: () -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

private val signedInFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * "Signed in Nov 5, 2026" in the phone's time zone, like the website's
 * Sessions page; null for an unreadable timestamp. Names are random and the
 * server keeps no platform or activity data, so the date is all there is.
 */
fun signedInText(createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String? = try {
    "Signed in " + OffsetDateTime.parse(createdAt).atZoneSameInstant(zone).format(signedInFormat)
} catch (e: DateTimeParseException) {
    null
}

/**
 * The devices signed in to the account, this one first and marked. Each can be
 * signed out after a confirmation; "Sign out all devices" (website wording)
 * leads to its own screen, where the account number confirms it.
 */
@Composable
fun DevicesScreen(state: DevicesUiState, callbacks: DevicesCallbacks, modifier: Modifier = Modifier) {
    OcaListScaffold(
        title = "Devices",
        onBack = callbacks.onBack,
        refreshing = state.refreshing,
        onRefresh = callbacks.onRefresh,
        modifier = modifier,
        bottom = state.message?.let { message ->
            {
                when (message) {
                    is DevicesMessage.SignedOut -> OcaInlineMessage(
                        text = "${message.name} signed out",
                        tone = OcaMessageTone.Success,
                        onTimeout = callbacks.onDismissMessage
                    )
                }
            }
        }
    ) {
        item(key = "intro") {
            OcaListStatusLine(
                "Devices signed in to your account. Each gets a random name; no details about the device are stored."
            )
        }
        when (state.list) {
            ListStatus.Failed -> item(key = "failed") {
                OcaListError(
                    text = state.listError ?: AccountMessages.LOAD_DEVICES_FAILED,
                    retryEnabled = !state.loading,
                    onRetry = callbacks.onRetry,
                    compact = false
                )
            }

            ListStatus.Loading -> item(key = "loading") { OcaSkeletonRows(description = "Loading your devices") }

            ListStatus.Ready -> {
                state.listError?.let { error ->
                    item(key = "reload-error") {
                        OcaListError(
                            text = error,
                            retryEnabled = !state.loading,
                            onRetry = callbacks.onRetry,
                            compact = true
                        )
                    }
                }
                item(key = "top-divider") {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = OcaTheme.colors.border,
                        modifier = Modifier.listItemWidth()
                    )
                }
                items(state.devices, key = { it.deviceId }) { device ->
                    Column(Modifier.listItemWidth()) {
                        DeviceRow(device = device, onSignOut = { callbacks.onRequestSignOut(device.deviceId) })
                        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
                    }
                }
            }
        }
        item(key = "sign-out-all") { SignOutAllSection(onSignOutAll = callbacks.onSignOutAll) }
    }
    state.signingOut?.let { signOut ->
        OcaSheet(onDismiss = callbacks.onDismissSignOut) { SignOutContent(signOut = signOut, callbacks = callbacks) }
    }
}

/** Name, sign-in date and a sign-out button, which asks before it signs out. */
@Composable
private fun DeviceRow(device: Device, onSignOut: () -> Unit) {
    val detail = signedInText(device.createdAt)
    OcaListRow(
        title = device.name,
        detail = detail,
        titleExtra = if (device.current) ({ OcaBadge(text = "This device") }) else null,
        textSemantics = {
            contentDescription = listOfNotNull(
                "Device ${device.name}",
                "this device".takeIf { device.current },
                detail?.replaceFirstChar { it.lowercase() }
            ).joinToString(", ")
        },
        trailingIsButton = true,
        trailing = {
            IconButton(onClick = onSignOut) {
                Icon(
                    painter = painterResource(R.drawable.ic_logout),
                    contentDescription = "Sign out ${device.name}",
                    tint = OcaTheme.colors.mutedForeground
                )
            }
        }
    )
}

/** Website wording (OCA-frontend/src/pages/Sessions.tsx). */
@Composable
private fun SignOutAllSection(onSignOutAll: () -> Unit) {
    Column(
        modifier = Modifier.listItemWidth().padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
    ) {
        Text(
            text = "Lost access to a device?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Sign out every device at once — including this one. You'll need your account number to log " +
                "back in.",
            style = MaterialTheme.typography.bodyMedium,
            color = OcaTheme.colors.mutedForeground
        )
        // The way in; the next screen's button, after the account number, is the filled one.
        OcaButton(
            text = "Sign out all devices",
            onClick = onSignOutAll,
            variant = OcaButtonVariant.DestructiveGhost,
            modifier = Modifier.fillMaxWidth().padding(top = OcaSpacing.xs)
        )
    }
}

@Composable
private fun SignOutContent(signOut: DeviceSignOut, callbacks: DevicesCallbacks) {
    val device = signOut.device
    Column(
        modifier = Modifier.padding(horizontal = OcaSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)) {
            Text(
                text = if (device.current) "Sign out this device?" else "Sign out ${device.name}?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (device.current) {
                    "You are signed out on this phone. To log in again, you'll need your account number."
                } else {
                    "${device.name} is signed out right away. To log in there again, you'll need your account number."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OcaTheme.colors.mutedForeground
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
            OcaButton(
                text = "Cancel",
                onClick = callbacks.onDismissSignOut,
                variant = OcaButtonVariant.Outline,
                enabled = !signOut.working,
                modifier = Modifier.weight(1f)
            )
            OcaButton(
                text = if (signOut.working) "Signing out…" else "Sign out",
                onClick = callbacks.onConfirmSignOut,
                variant = OcaButtonVariant.Destructive,
                enabled = !signOut.working,
                modifier = Modifier.weight(1f)
            )
        }
        signOut.error?.let { OcaErrorText(text = it) }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun DevicesScreenPreview() {
    OcaTheme {
        DevicesScreen(
            state = DevicesUiState(
                devices = listOf(
                    Device("d1", "brave otter", "2026-11-05T10:00:00Z", current = true),
                    Device("d2", "quiet heron", "2026-11-06T10:00:00Z", current = false)
                ),
                list = ListStatus.Ready
            ),
            callbacks = DevicesCallbacks()
        )
    }
}
