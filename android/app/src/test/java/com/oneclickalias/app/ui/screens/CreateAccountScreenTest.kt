package com.oneclickalias.app.ui.screens

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<CreateAccountUiState>(CreateAccountUiState.Creating)
    private val calls = mutableListOf<String>()
    private var passwordManager by mutableStateOf(false)

    private fun show() = compose.setContent {
        OcaTheme {
            CreateAccountScreen(
                state = state,
                onBack = { calls += "back" },
                onRetry = { calls += "retry" },
                onCopy = { calls += "copy" },
                onSavedChange = { calls += "saved=$it" },
                onSubmit = { calls += "submit" },
                onLogIn = { calls += "login" },
                onStartOver = { calls += "startOver" },
                onSaveToPasswordManager = if (passwordManager) ({ calls += "passwordManager" }) else null
            )
        }
    }

    private fun isSecure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `shows progress while the number is created`() {
        show()

        compose.onNodeWithContentDescription("Creating your account").assertIsDisplayed()
    }

    @Test
    fun `shows the number grouped, with warning and screen-reader text`() {
        state = CreateAccountUiState.Ready("4711000012345678")
        show()

        compose.onNodeWithText("4711 0000 1234 5678").assertIsDisplayed()
        compose.onNodeWithContentDescription("Account number: 4 7 1 1, 0 0 0 0, 1 2 3 4, 5 6 7 8").assertIsDisplayed()
        compose.onNodeWithText("only way to log in", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `window is secure while the number is created and shown`() {
        // Already while creating: the flag takes a frame to apply, the number must
        // never be drawn into a recording before it does.
        show()
        assertTrue(isSecure())

        state = CreateAccountUiState.Ready("4711000012345678")
        compose.waitForIdle()
        assertTrue(isSecure())

        state = CreateAccountUiState.Interrupted
        compose.waitForIdle()
        assertFalse(isSecure())

        state = CreateAccountUiState.CreateFailed("Could not create an account.")
        compose.waitForIdle()
        assertFalse(isSecure())
    }

    @Test
    fun `continue is enabled only after confirming`() {
        state = CreateAccountUiState.Ready("4711000012345678")
        show()
        compose.onNodeWithText("Continue", ignoreCase = true).assertIsNotEnabled()

        compose.onNodeWithText("I saved my account number").performClick()
        assertEquals(listOf("saved=true"), calls)

        state = CreateAccountUiState.Ready("4711000012345678", saved = true)
        compose.onNodeWithText("Continue", ignoreCase = true).assertIsEnabled().performClick()
        assertEquals("submit", calls.last())
    }

    @Test
    fun `copy reports and shows feedback`() {
        state = CreateAccountUiState.Ready("4711000012345678")
        show()

        compose.onNodeWithContentDescription("Copy account number").performClick()
        assertEquals(listOf("copy"), calls)

        state = CreateAccountUiState.Ready("4711000012345678", copied = true)
        compose.onNodeWithContentDescription("Account number copied")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        // No separate copy button any more: the icon in the number's frame is the way to copy.
        compose.onNodeWithText("Copy", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `offers saving to the password manager when one is set up`() {
        passwordManager = true
        state = CreateAccountUiState.Ready("4711000012345678")
        show()

        compose.onNodeWithText("Save to password manager", ignoreCase = true).performScrollTo().performClick()

        assertEquals(listOf("passwordManager"), calls)
    }

    @Test
    fun `no password manager, no save button`() {
        state = CreateAccountUiState.Ready("4711000012345678")
        show()

        compose.onNodeWithText("Save to password manager", ignoreCase = true).assertDoesNotExist()
        // The other ways stay: copy and the confirmation.
        compose.onNodeWithContentDescription("Copy account number").assertIsDisplayed()
        compose.onNodeWithText("I saved my account number").assertIsDisplayed()
    }

    @Test
    fun `save to password manager is disabled while logging in`() {
        passwordManager = true
        state = CreateAccountUiState.Ready("4711000012345678", saved = true, submitting = true)
        show()

        compose.onNodeWithText("Save to password manager", ignoreCase = true).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `submitting and errors are shown`() {
        state = CreateAccountUiState.Ready("4711000012345678", saved = true, submitting = true)
        show()
        compose.onNodeWithText("Logging in…", ignoreCase = true).assertIsNotEnabled()

        state = CreateAccountUiState.Ready("4711000012345678", saved = true, error = AuthMessages.DEVICE_LIMIT)
        compose.onNodeWithText(AuthMessages.DEVICE_LIMIT).assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `creation failure offers a retry`() {
        state = CreateAccountUiState.CreateFailed(AuthMessages.OFFLINE)
        show()

        compose.onNodeWithText(AuthMessages.OFFLINE).assertIsDisplayed()
        compose.onNodeWithText("Try again", ignoreCase = true).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `interrupted state explains and offers both ways`() {
        state = CreateAccountUiState.Interrupted
        show()

        compose.onNodeWithText("never stored on this device", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Log in", ignoreCase = true).performClick()
        compose.onNodeWithText("Create a new account", ignoreCase = true).performClick()

        assertEquals(listOf("login", "startOver"), calls)
    }

    @Test
    fun `back arrow navigates back`() {
        show()

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        state = CreateAccountUiState.Ready("4711000012345678")
        show()

        compose.onNodeWithText("4711 0000 1234 5678").assertIsDisplayed()
    }
}
