package com.oneclickalias.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.Wordmark
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Shown instead of the app while [AppLockState.locked]: wordmark, one line,
 * **Unlock** (reopens the system prompt) and **Log out** as the way out. A
 * phone without a screen lock any more gets the reason and only Log out.
 */
@Composable
fun LockScreen(state: AppLockState, onUnlock: () -> Unit, onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    val busy = state.prompting || state.loggingOut
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = OcaSpacing.maxContentWidth)
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.xl),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier)
            Column(
                modifier = Modifier.padding(vertical = OcaSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
            ) {
                Wordmark()
                Text(
                    text = if (state.canAuthenticate) {
                        "OneClickAlias is locked. Unlock it with your fingerprint, face or screen lock."
                    } else {
                        "OneClickAlias is locked, and this phone no longer has a screen lock to unlock it. " +
                            "Log out, then log in again with your account number."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
                if (state.canAuthenticate) {
                    OcaButton(
                        text = "Unlock",
                        onClick = onUnlock,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OcaButton(
                    text = if (state.loggingOut) "Logging out…" else "Log out",
                    onClick = onLogOut,
                    enabled = !state.loggingOut,
                    variant = if (state.canAuthenticate) OcaButtonVariant.Outline else OcaButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LockScreenPreview() {
    OcaTheme {
        LockScreen(
            state = AppLockState(ready = true, enabled = true, locked = true, canAuthenticate = true),
            onUnlock = {},
            onLogOut = {}
        )
    }
}
