package com.oneclickalias.app.ui.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.auth.Device
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DevicesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val phone = Device("d-phone", "brave otter", "2026-11-05T10:00:00Z", current = true)
    private val laptop = Device("d-laptop", "quiet heron", "2026-11-06T23:30:00+01:00", current = false)
    private val ready = DevicesUiState(devices = listOf(phone, laptop), list = ListStatus.Ready)
    private val calls = mutableListOf<String>()
    private val callbacks = DevicesCallbacks(
        onBack = { calls += "back" },
        onRequestSignOut = { calls += "sign out $it" },
        onDismissSignOut = { calls += "dismiss" },
        onConfirmSignOut = { calls += "confirm" },
        onSignOutAll = { calls += "all" }
    )

    private fun show(state: DevicesUiState) {
        compose.setContent { OcaTheme { DevicesScreen(state = state, callbacks = callbacks) } }
    }

    @Test
    fun `signed-in date in the phone's time zone`() {
        assertEquals("Signed in Nov 6, 2026", signedInText("2026-11-06T23:30:00+01:00", ZoneOffset.UTC))
        assertEquals("Signed in Nov 7, 2026", signedInText("2026-11-06T23:30:00Z", ZoneOffset.ofHours(2)))
        assertNull(signedInText("yesterday"))
    }

    @Test
    fun `this device is marked, each device has its own sign-out button`() {
        show(ready)

        compose.onNodeWithContentDescription("Device brave otter, this device", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Sign out quiet heron").performClick()

        assertEquals(listOf("sign out d-laptop"), calls)
    }

    @Test
    fun `sign out all devices leads on`() {
        show(ready)

        compose.onNodeWithText("Sign out all devices", ignoreCase = true).performScrollTo().performClick()

        assertEquals(listOf("all"), calls)
    }

    @Test
    fun `signing out this device says it is a logout`() {
        show(ready.copy(signingOut = DeviceSignOut(phone)))

        compose.onNodeWithText("Sign out this device?").assertIsDisplayed()
        compose.onNodeWithText("You are signed out on this phone", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sign out sheet confirms, cancels and shows errors`() {
        show(ready.copy(signingOut = DeviceSignOut(laptop, error = AccountMessages.SIGN_OUT_FAILED)))

        compose.onNodeWithText("Sign out quiet heron?").assertIsDisplayed()
        compose.onNodeWithText(AccountMessages.SIGN_OUT_FAILED).assertIsDisplayed()
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `working sheet disables its buttons`() {
        show(ready.copy(signingOut = DeviceSignOut(laptop, working = true)))

        compose.onNodeWithText("Signing out…", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    fun `failed first load offers retry`() {
        var retries = 0
        compose.setContent {
            OcaTheme {
                DevicesScreen(
                    state = DevicesUiState(list = ListStatus.Failed, listError = AccountMessages.LOAD_DEVICES_FAILED),
                    callbacks = DevicesCallbacks(onRetry = { retries++ })
                )
            }
        }

        compose.onNodeWithText("Retry", ignoreCase = true).performClick()

        assertEquals(1, retries)
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show(ready)

        compose.onNodeWithText("Devices").assertIsDisplayed()
    }
}
