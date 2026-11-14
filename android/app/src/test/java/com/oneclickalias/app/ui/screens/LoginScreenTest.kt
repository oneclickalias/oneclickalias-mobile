package com.oneclickalias.app.ui.screens

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.FillableData
import androidx.compose.ui.autofill.createFromText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.account.AccountNumberFormat
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(LoginUiState())
    private val calls = mutableListOf<String>()

    private var scanAvailable by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)

    private fun show() = compose.setContent {
        OcaTheme {
            LoginScreen(
                onScanCode = if (scanAvailable) ({ calls += "scan" }) else null,
                state = state,
                onInput = { calls += "input=$it" },
                onToggleReveal = { calls += "reveal" },
                onConfirmedChange = { calls += "confirmed=$it" },
                onSubmit = { calls += "submit" },
                onCreateAccount = { calls += "create" },
                notice = notice,
                onRevokeAllAndLogIn = { calls += "revoke all" }
            )
        }
    }

    private fun field() = compose.onNode(hasSetTextAction())

    private fun isSecure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `field is a password for autofill and masked in groups`() {
        state = LoginUiState(digits = "47110000")
        show()

        field()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsSensitiveData, true))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    androidx.compose.ui.text.AnnotatedString("•••• ••••")
                )
            )
        assertFalse(isSecure())
    }

    @Test
    fun `a password manager can fill the field, formatted or not`() {
        show()

        // Compose autofill fills through this action; the ViewModel keeps the digits only.
        field().performSemanticsAction(SemanticsActions.OnFillData) {
            it(FillableData.createFromText("4711 0000 1234 5678")!!)
        }

        assertEquals(listOf("input=4711 0000 1234 5678"), calls)
        assertEquals("4711000012345678", AccountNumberFormat.sanitize("4711 0000 1234 5678"))
    }

    @Test
    fun `typing reports the raw text`() {
        show()

        field().performTextInput("4711")

        assertEquals(listOf("input=4711"), calls)
    }

    @Test
    fun `revealed number is grouped and makes the window secure`() {
        state = LoginUiState(digits = "47110000", revealed = true)
        show()

        field().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("4711 0000")
            )
        )
        assertTrue(isSecure())
        compose.onNodeWithText("Hide", ignoreCase = true).performClick()
        assertEquals(listOf("reveal"), calls)

        state = LoginUiState(digits = "47110000", revealed = false)
        compose.waitForIdle()
        assertFalse(isSecure())
    }

    @Test
    fun `log in is enabled with sixteen digits`() {
        state = LoginUiState(digits = "471100001234567")
        show()
        compose.onNodeWithText("Log in", ignoreCase = true).assertIsNotEnabled()

        state = LoginUiState(digits = "4711000012345678")
        compose.onNodeWithText("Log in", ignoreCase = true).assertIsEnabled().performClick()

        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `error text is announced`() {
        state = LoginUiState(digits = "4711000012345678", error = AuthMessages.WRONG_NUMBER)
        show()

        compose.onNodeWithText(AuthMessages.WRONG_NUMBER)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    @Config(qualifiers = "w320dp-h400dp")
    fun `long error stays visible next to the button without scrolling`() {
        // Small window, like a phone with the keyboard open: the error must not
        // end up clipped inside the shrunken scroll area.
        state = LoginUiState(digits = "4711000012345678", error = AuthMessages.DEVICE_LIMIT)
        show()

        compose.onNodeWithText(AuthMessages.DEVICE_LIMIT).assertIsDisplayed()
        compose.onNodeWithText("Log in", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `device limit offers to sign out all devices and log in`() {
        state = LoginUiState(digits = "4711000012345678", deviceLimitReached = true)
        show()

        compose.onNodeWithText("Device limit reached").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sign out all devices & log in", ignoreCase = true).performScrollTo().performClick()

        assertEquals(listOf("revoke all"), calls)
    }

    @Test
    fun `signing out all devices disables both buttons`() {
        state = LoginUiState(digits = "4711000012345678", deviceLimitReached = true, submitting = true)
        show()

        compose.onNodeWithText("Signing out all devices…", ignoreCase = true).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `unconfirmed registration shows the confirmation`() {
        state =
            LoginUiState(
                digits = "4711000012345678",
                needsConfirmation = true,
                error = AuthMessages.CONFIRM_TO_CONTINUE
            )
        show()

        compose.onNodeWithText("Confirm and log in", ignoreCase = true).assertIsNotEnabled()
        compose.onNodeWithText("I saved my account number").performScrollTo().performClick()

        assertEquals(listOf("confirmed=true"), calls)
    }

    @Test
    fun `submitting disables the button`() {
        state = LoginUiState(digits = "4711000012345678", submitting = true)
        show()

        compose.onNodeWithText("Logging in…", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    fun `no hint line under the field`() {
        show()

        compose.onNodeWithText("16 digits").assertDoesNotExist()
    }

    @Test
    fun `the field does not take the focus, so the keyboard stays closed`() {
        scanAvailable = true
        show()

        field().assertIsNotFocused()
        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the keyboard's done key logs in`() {
        state = LoginUiState(digits = "4711000012345678")
        show()

        field().performImeAction()

        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `the done key does nothing while the number is incomplete`() {
        state = LoginUiState(digits = "4711")
        show()

        field().performImeAction()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `create account is the last thing on the page`() {
        scanAvailable = true
        show()

        val create = compose.onNodeWithText("Create account", ignoreCase = true).performScrollTo()
        val createTop = create.fetchSemanticsNode().positionInRoot.y
        val scanTop = compose.onNodeWithText("Scan code from dashboard", ignoreCase = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(createTop > scanTop)
        create.performClick()

        assertEquals(listOf("create"), calls)
    }

    @Test
    fun `the reason for being signed out is announced above the field`() {
        notice = "You have been signed out. Log in again with your account number."
        show()

        compose.onNodeWithText(notice!!)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show()

        compose.onNodeWithText("OneClickAlias").assertIsDisplayed()
        compose.onNodeWithText("Log in", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `scan code opens the scanner`() {
        scanAvailable = true
        show()

        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).performScrollTo().performClick()

        assertEquals(listOf("scan"), calls)
    }

    @Test
    fun `without a camera there is no scan entry`() {
        show()

        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).assertDoesNotExist()
    }
}
