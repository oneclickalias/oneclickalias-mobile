package com.oneclickalias.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AccountScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(
        state: AccountUiState = AccountUiState(),
        onLogout: () -> Unit = {},
        appLock: AppLockState = AppLockState(ready = true, canAuthenticate = true)
    ) {
        compose.setContent {
            OcaTheme {
                AccountScreen(
                    state = state,
                    versionName = "0.1.0",
                    onLogout = onLogout,
                    appLock = appLock,
                    onAppLockChange = { calls += "app lock $it" },
                    onOpenRecipients = { calls += "recipients" },
                    onOpenDevices = { calls += "devices" },
                    onOpenBilling = { calls += "billing" },
                    onDeleteAccount = { calls += "delete account" }
                )
            }
        }
    }

    @Test
    fun `lists the account areas, log out and the app version`() {
        var logouts = 0
        show(onLogout = { logouts++ })

        compose.onNodeWithText("Account").assertIsDisplayed()
        compose.onNodeWithText("Recipients").performClick()
        compose.onNodeWithText("Devices").performClick()
        compose.onNodeWithText("Billing").performScrollTo().performClick()
        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        compose.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        compose.onNodeWithText("Log out", ignoreCase = true).performClick()

        assertEquals(listOf("recipients", "devices", "billing", "delete account"), calls)
        assertEquals(1, logouts)
    }

    @Test
    fun `app lock is off by default and the switch asks to turn it on`() {
        show()

        compose.onNodeWithText("Asks for your fingerprint, face or screen lock", substring = true).performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("App lock") and isToggleable()).performScrollTo().assertIsOff().performClick()

        assertEquals(listOf("app lock true"), calls)
    }

    @Test
    fun `app lock that is on can be turned off`() {
        show(appLock = AppLockState(ready = true, enabled = true, canAuthenticate = true))

        compose.onNode(hasText("App lock") and isToggleable()).performScrollTo().assertIsOn().performClick()

        assertEquals(listOf("app lock false"), calls)
    }

    @Test
    fun `app lock cannot be turned on without a screen lock and says why`() {
        show(appLock = AppLockState(ready = true, canAuthenticate = false))

        compose.onNodeWithText(
            "Set up a screen lock on this phone to use app lock."
        ).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("App lock") and isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun `app lock that is on can still be turned off after the screen lock was removed`() {
        show(appLock = AppLockState(ready = true, enabled = true, canAuthenticate = false))

        compose.onNode(hasText("App lock") and isToggleable()).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf("app lock false"), calls)
    }

    @Test
    fun `app lock switch waits while the prompt is open`() {
        show(appLock = AppLockState(ready = true, canAuthenticate = true, prompting = true))

        compose.onNode(hasText("App lock") and isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun `billing says it opens the website`() {
        show()

        compose.onNodeWithText("Opens the website", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a missing browser is explained`() {
        show(AccountUiState(message = "No browser app found — open x in a browser."))

        compose.onNodeWithText("No browser app found", substring = true).assertIsDisplayed()
    }

    @Test
    fun `it is a tab, not a screen to go back from`() {
        show()

        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun `logging out disables the button`() {
        show(AccountUiState(loggingOut = true))

        compose.onNodeWithText("Logging out…", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show()

        compose.onNodeWithText("Version 0.1.0").assertIsDisplayed()
    }
}
