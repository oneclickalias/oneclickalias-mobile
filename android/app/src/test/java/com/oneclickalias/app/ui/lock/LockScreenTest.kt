package com.oneclickalias.app.ui.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.data.lock.AppLockState
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: AppLockState) {
        compose.setContent {
            OcaTheme {
                LockScreen(state = state, onUnlock = { calls += "unlock" }, onLogOut = { calls += "log out" })
            }
        }
    }

    private val locked = AppLockState(ready = true, enabled = true, locked = true, canAuthenticate = true)

    @Test
    fun `offers unlock and log out`() {
        show(locked)

        compose.onNodeWithText("OneClickAlias is locked", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Unlock", ignoreCase = true).performClick()
        compose.onNodeWithText("Log out", ignoreCase = true).performClick()

        assertEquals(listOf("unlock", "log out"), calls)
    }

    @Test
    fun `unlock waits while the prompt is open`() {
        show(locked.copy(prompting = true))

        compose.onNodeWithText("Unlock", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    fun `without a screen lock only log out is left, with the reason`() {
        show(locked.copy(canAuthenticate = false))

        compose.onNodeWithText("no longer has a screen lock", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Unlock", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Log out", ignoreCase = true).performClick()

        assertEquals(listOf("log out"), calls)
    }

    @Test
    fun `logging out disables both buttons`() {
        show(locked.copy(loggingOut = true))

        compose.onNodeWithText("Logging out…", ignoreCase = true).assertIsNotEnabled()
        compose.onNodeWithText("Unlock", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show(locked)

        compose.onNodeWithText("Unlock", ignoreCase = true).assertIsDisplayed()
    }
}
