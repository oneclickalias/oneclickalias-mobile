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

/** Everything the browser sign-in screen can ask for. */
data class BrowserSignInCallbacks(
    val onBack: () -> Unit = {},
    val onScanned: (String) -> Unit = {},
    val onApprove: () -> Unit = {},
    val onDeny: () -> Unit = {},
    val onScanAgain: () -> Unit = {},
    val onRequestCamera: () -> Unit = {},
    val onOpenSettings: () -> Unit = {}
)

private const val EYEBROW = "Sign in a browser"

/**
 * Signs in a browser that shows a code on oneclickalias.com
 * (docs/flows.md, "Sign in a browser"): scan, compare the name, approve.
 *
 * @param cameraPreview the live camera; tests pass a placeholder.
 */
@Composable
fun BrowserSignInScreen(
    state: BrowserSignInUiState,
    camera: CameraAccess,
    callbacks: BrowserSignInCallbacks,
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
            is BrowserSignInUiState.Scanning -> Scanning(state, camera, callbacks, cameraPreview)

            BrowserSignInUiState.Claiming -> {
                OcaScreenHeading(eyebrow = EYEBROW, title = "Checking the code…")
                Progress("Checking the code with the server…")
            }

            is BrowserSignInUiState.Confirm -> Confirm(state)

            is BrowserSignInUiState.Done -> OcaScreenHeading(
                eyebrow = EYEBROW,
                title = "${state.deviceName} is signed in"
            )

            BrowserSignInUiState.Denied -> OcaScreenHeading(eyebrow = EYEBROW, title = "Sign-in denied")

            is BrowserSignInUiState.Failed -> {
                OcaScreenHeading(eyebrow = EYEBROW, title = "Not signed in")
                OcaErrorText(state.message)
            }
        }
    }
}

@Composable
private fun Scanning(
    state: BrowserSignInUiState.Scanning,
    camera: CameraAccess,
    callbacks: BrowserSignInCallbacks,
    cameraPreview: @Composable (onText: (String) -> Unit, modifier: Modifier) -> Unit
) {
    OcaScreenHeading(eyebrow = EYEBROW, title = "Scan the code in your browser")
    Text(
        text = "On your computer, open oneclickalias.com, go to Log in and choose “Sign in with your phone”. " +
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
            body = "Without the camera, the code can't be scanned.",
            action = "Allow camera",
            onAction = callbacks.onRequestCamera
        )

        CameraAccess.Blocked -> OcaNotice(
            title = "Camera access is off",
            body = "Allow the camera for OneClickAlias in the app settings.",
            action = "Open settings",
            onAction = callbacks.onOpenSettings
        )
    }
}

@Composable
private fun Confirm(state: BrowserSignInUiState.Confirm) {
    Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
        Text(
            text = "APPROVE THIS SIGN-IN?",
            style = MaterialTheme.typography.labelMedium,
            color = OcaTheme.colors.mutedForeground
        )
        Text(
            text = state.deviceName,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
    }
    Text(
        text = "Approve only if you opened oneclickalias.com yourself just now and your browser shows the same " +
            "name. Whoever you approve gets full access to your aliases.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.working) Progress("One moment…")
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
        Box(Modifier.size(20.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = OcaTheme.colors.mutedForeground)
    }
}

@Composable
private fun Buttons(state: BrowserSignInUiState, camera: CameraAccess, callbacks: BrowserSignInCallbacks) {
    val full = Modifier.fillMaxWidth()
    when (state) {
        is BrowserSignInUiState.Scanning -> if (camera == CameraAccess.NotAsked) {
            OcaButton(text = "Allow camera", onClick = callbacks.onRequestCamera, modifier = full)
        }

        BrowserSignInUiState.Claiming -> OcaButton(
            text = "Cancel",
            onClick = callbacks.onBack,
            variant = OcaButtonVariant.Outline,
            modifier = full
        )

        is BrowserSignInUiState.Confirm -> {
            OcaButton(text = "Approve", onClick = callbacks.onApprove, enabled = !state.working, modifier = full)
            OcaButton(
                text = "Deny",
                onClick = callbacks.onDeny,
                variant = OcaButtonVariant.Outline,
                enabled = !state.working,
                modifier = full
            )
        }

        is BrowserSignInUiState.Done, BrowserSignInUiState.Denied ->
            OcaButton(text = "Done", onClick = callbacks.onBack, modifier = full)

        is BrowserSignInUiState.Failed -> {
            OcaButton(text = "Scan again", onClick = callbacks.onScanAgain, modifier = full)
            OcaButton(text = "Close", onClick = callbacks.onBack, variant = OcaButtonVariant.Outline, modifier = full)
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun BrowserSignInConfirmPreview() {
    OcaTheme {
        BrowserSignInScreen(
            state = BrowserSignInUiState.Confirm("p", "Casual Hippo"),
            camera = CameraAccess.Granted,
            callbacks = BrowserSignInCallbacks()
        )
    }
}
