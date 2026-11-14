package com.oneclickalias.app.ui.pairing

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<PairingUiState>(PairingUiState.Scanning())
    private var camera by mutableStateOf(CameraAccess.Granted)
    private val calls = mutableListOf<String>()
    private var previewShown = false

    private fun show() = compose.setContent {
        OcaTheme {
            PairingScreen(
                state = state,
                camera = camera,
                callbacks = PairingCallbacks(
                    onBack = { calls += "back" },
                    onScanned = { calls += "scanned=$it" },
                    onScanAgain = { calls += "scan again" },
                    onUseAccountNumber = { calls += "account number" },
                    onRequestCamera = { calls += "request camera" },
                    onOpenSettings = { calls += "settings" }
                ),
                cameraPreview = { onText, modifier ->
                    previewShown = true
                    // Stands in for a camera frame with a code in it.
                    Box(modifier) { Button(onClick = { onText("OCA-PAIR-1-X") }) { Text("fake frame") } }
                }
            )
        }
    }

    private fun isSecure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `nothing is asked before the tap, then the system dialog is requested`() {
        camera = CameraAccess.NotAsked
        show()

        compose.onNodeWithText("Nothing is recorded or sent", substring = true).assertIsDisplayed()
        assertFalse(previewShown)
        assertTrue(calls.isEmpty())

        compose.onNodeWithText("Allow camera", ignoreCase = true).performClick()
        assertEquals(listOf("request camera"), calls)
    }

    @Test
    fun `granted shows the camera, and a recognised text goes to the callback`() {
        show()

        compose.onNodeWithContentDescription("Camera view for scanning the code").assertIsDisplayed()
        compose.onNodeWithText("fake frame").performClick()

        assertEquals(listOf("scanned=OCA-PAIR-1-X"), calls)
        assertFalse(isSecure())
    }

    @Test
    fun `a wrong code shows its hint under the camera`() {
        state = PairingUiState.Scanning(problem = PairingMessages.NOT_OURS)
        show()

        compose.onNodeWithText(PairingMessages.NOT_OURS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `denied explains and offers both ways`() {
        camera = CameraAccess.Denied
        show()

        compose.onNodeWithText("No camera access").assertIsDisplayed()
        compose.onNodeWithText("Allow camera", ignoreCase = true).performScrollTo().performClick()
        compose.onNodeWithText("Use account number", ignoreCase = true).performClick()
        assertEquals(listOf("request camera", "account number"), calls)
        assertFalse(previewShown)
    }

    @Test
    fun `blocked points to the app settings`() {
        camera = CameraAccess.Blocked
        show()

        compose.onNodeWithText("Camera access is off").assertIsDisplayed()
        compose.onNodeWithText("Open settings", ignoreCase = true).performScrollTo().performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun `waiting shows the device name large, no camera, and cancel goes back`() {
        state = PairingUiState.Waiting("Casual Hippo")
        show()

        compose.onNodeWithText("Casual Hippo").assertIsDisplayed()
        compose.onNodeWithText("Confirm in your browser", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Waiting for your confirmation…").performScrollTo().assertIsDisplayed()
        assertFalse(previewShown)
        assertFalse(isSecure())

        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `claiming has no camera`() {
        state = PairingUiState.Claiming
        show()

        compose.onNodeWithText("Checking the code…").assertIsDisplayed()
        assertFalse(previewShown)
    }

    @Test
    fun `failure shows the text with both ways back`() {
        state = PairingUiState.Failed(PairingMessages.USED)
        show()

        compose.onNodeWithText(PairingMessages.USED).assertIsDisplayed()
        compose.onNodeWithText("Scan again", ignoreCase = true).performClick()
        compose.onNodeWithText("Use account number", ignoreCase = true).performClick()
        assertEquals(listOf("scan again", "account number"), calls)
        assertFalse(previewShown)
    }
}
