package com.oneclickalias.app.ui.pairing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.platform.QrCameraPreview
import com.oneclickalias.app.ui.components.OcaBackBar
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaFlowScaffold
import com.oneclickalias.app.ui.components.OcaNotice
import com.oneclickalias.app.ui.components.OcaScreenHeading
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/** What the app may do with the camera, as far as this screen knows. */
enum class CameraAccess {
    /** Not granted, not asked on this screen yet: explain first, ask on tap. */
    NotAsked,
    Granted,

    /** Refused; the system dialog can be shown again. */
    Denied,

    /** Refused for good ("Don't ask again"): only the app settings can change it. */
    Blocked
}

/** Wires [PairingScreen]; navigation and the permission request stay with the caller. */
data class PairingCallbacks(
    val onBack: () -> Unit = {},
    val onScanned: (String) -> Unit = {},
    val onScanAgain: () -> Unit = {},
    val onUseAccountNumber: () -> Unit = {},
    val onRequestCamera: () -> Unit = {},
    val onOpenSettings: () -> Unit = {}
)

/**
 * QR login (docs/flows.md, "Log in with a code"): scan the code the dashboard
 * shows, then compare the device name and wait for the approval there. No
 * `FLAG_SECURE`: nothing secret is on screen (the device name is shown in the
 * dashboard too, and a scanned code is claimed, so worthless to others, before
 * the camera stops).
 *
 * @param cameraPreview the live camera; tests pass a placeholder.
 */
@Composable
fun PairingScreen(
    state: PairingUiState,
    camera: CameraAccess,
    callbacks: PairingCallbacks,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable (onText: (String) -> Unit, modifier: Modifier) -> Unit = { onText, previewModifier ->
        QrCameraPreview(onText = onText, modifier = previewModifier)
    }
) {
    OcaFlowScaffold(
        topBar = { OcaBackBar(onBack = callbacks.onBack) },
        modifier = modifier,
        bottom = { Buttons(state, camera, callbacks) }
    ) {
        when (state) {
            is PairingUiState.Scanning -> Scanning(state, camera, callbacks, cameraPreview)

            PairingUiState.Claiming -> {
                OcaScreenHeading(eyebrow = EYEBROW, title = "Checking the code…")
                Progress("Checking the code with the server…")
            }

            is PairingUiState.Waiting -> Waiting(state.deviceName)

            is PairingUiState.Failed -> {
                OcaScreenHeading(eyebrow = EYEBROW, title = "Not logged in")
                OcaErrorText(state.message)
            }
        }
    }
}

private const val EYEBROW = "Log in with a code"

@Composable
private fun Scanning(
    state: PairingUiState.Scanning,
    camera: CameraAccess,
    callbacks: PairingCallbacks,
    cameraPreview: @Composable (onText: (String) -> Unit, modifier: Modifier) -> Unit
) {
    OcaScreenHeading(eyebrow = EYEBROW, title = "Scan the code from your dashboard")
    Text(
        text = "On your computer, open the OneClickAlias dashboard and choose “Sign in on your phone”. " +
            "Then point the camera at the code.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    when (camera) {
        CameraAccess.Granted -> {
            cameraPreview(
                callbacks.onScanned,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(1.dp, OcaTheme.colors.border, RectangleShape)
                    .semantics { contentDescription = "Camera view for scanning the code" }
            )
            state.problem?.let { OcaErrorText(it) }
        }

        CameraAccess.NotAsked -> Text(
            text = "The app uses the camera only to read this code. Nothing is recorded or sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = OcaTheme.colors.mutedForeground
        )

        CameraAccess.Denied -> OcaNotice(
            title = "No camera access",
            body = "Without the camera, the code can't be scanned. You can log in with your account number instead.",
            action = "Allow camera",
            onAction = callbacks.onRequestCamera
        )

        CameraAccess.Blocked -> OcaNotice(
            title = "Camera access is off",
            body = "Allow the camera for OneClickAlias in the app settings, or log in with your account number.",
            action = "Open settings",
            onAction = callbacks.onOpenSettings
        )
    }
}

@Composable
private fun Waiting(deviceName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
        Text(
            text = "CONFIRM IN YOUR BROWSER",
            style = MaterialTheme.typography.labelMedium,
            color = OcaTheme.colors.mutedForeground
        )
        Text(
            text = deviceName,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
    }
    Text(
        text = "This phone signs in as “$deviceName”. Approve the sign-in in your browser only if it shows the " +
            "same name.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Progress("Waiting for your confirmation…")
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
        Box(Modifier.size(20.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = OcaTheme.colors.mutedForeground)
    }
}

@Composable
private fun Buttons(state: PairingUiState, camera: CameraAccess, callbacks: PairingCallbacks) {
    when (state) {
        is PairingUiState.Scanning -> {
            if (camera == CameraAccess.NotAsked) {
                OcaButton(
                    text = "Allow camera",
                    onClick = callbacks.onRequestCamera,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            UseAccountNumber(callbacks)
        }

        PairingUiState.Claiming, is PairingUiState.Waiting -> OcaButton(
            text = "Cancel",
            onClick = callbacks.onBack,
            variant = OcaButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth()
        )

        is PairingUiState.Failed -> {
            OcaButton(text = "Scan again", onClick = callbacks.onScanAgain, modifier = Modifier.fillMaxWidth())
            UseAccountNumber(callbacks)
        }
    }
}

@Composable
private fun UseAccountNumber(callbacks: PairingCallbacks) {
    OcaButton(
        text = "Use account number",
        onClick = callbacks.onUseAccountNumber,
        variant = OcaButtonVariant.Outline,
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showSystemUi = true)
@Composable
private fun PairingWaitingPreview() {
    OcaTheme {
        PairingScreen(
            state = PairingUiState.Waiting("Casual Hippo"),
            camera = CameraAccess.Granted,
            callbacks = PairingCallbacks()
        )
    }
}
