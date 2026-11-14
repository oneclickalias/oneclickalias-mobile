package com.oneclickalias.app.ui.screens

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberConfirmScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(NumberConfirmUiState())
    private val calls = mutableListOf<String>()

    private fun show(texts: NumberConfirmTexts = NumberConfirmTexts.DeleteAccount) = compose.setContent {
        OcaTheme {
            NumberConfirmScreen(
                texts = texts,
                state = state,
                onInput = { calls += "input" },
                onToggleReveal = { calls += "reveal" },
                onSubmit = { calls += "submit" },
                onBack = { calls += "back" }
            )
        }
    }

    private fun button(text: String) = compose.onNode(hasText(text, ignoreCase = true) and hasClickAction())

    private fun isSecure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `delete account warns about the 12 hours and needs the full number`() {
        state = NumberConfirmUiState(digits = "47110000")
        show()

        compose.onNodeWithText("permanently deleted in 12 hours", substring = true).assertExists()
        compose.onNodeWithText("deletion is cancelled", substring = true).assertExists()
        button("Delete account").assertIsNotEnabled()

        state = NumberConfirmUiState(digits = "4711000012345678")
        button("Delete account").assertIsEnabled().performClick()

        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `sign out all says this phone is included`() {
        show(NumberConfirmTexts.RevokeAll)

        compose.onNodeWithText("including this phone", substring = true).assertExists()
    }

    @Test
    fun `revealed number makes the window secure`() {
        state = NumberConfirmUiState(digits = "4711000012345678")
        show()
        compose.waitForIdle()
        assertFalse(isSecure())

        state = state.copy(revealed = true)
        compose.waitForIdle()

        assertTrue(isSecure())
    }

    @Test
    fun `errors and the busy state`() {
        state =
            NumberConfirmUiState(digits = "4711000012345678", error = "This doesn't match your current account number.")
        show()
        compose.onNodeWithText("This doesn't match", substring = true).assertExists()

        state = state.copy(submitting = true, error = null)

        button("Deleting…").assertIsNotEnabled()
    }
}
