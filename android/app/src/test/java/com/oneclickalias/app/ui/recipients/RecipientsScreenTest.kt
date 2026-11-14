package com.oneclickalias.app.ui.recipients

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Recipient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RecipientsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Recipient("r-home", "home@example.com", true, "2026-11-01T10:00:00Z")
    private val work = Recipient("r-work", "work@example.com", false, "2026-11-02T10:00:00Z")
    private val ready = RecipientsUiState(recipients = listOf(home, work), list = ListStatus.Ready)
    private val calls = mutableListOf<String>()

    private val callbacks = RecipientsCallbacks(
        onBack = { calls += "back" },
        onRefresh = { calls += "refresh" },
        onRetry = { calls += "retry" },
        onOpenAdd = { calls += "open add" },
        onDismissAdd = { calls += "dismiss add" },
        onEmailChange = { calls += "email $it" },
        onSubmitAdd = { calls += "submit add" },
        onOpenActions = { calls += "actions $it" },
        onDismissActions = { calls += "dismiss actions" },
        onResend = { calls += "resend" },
        onRequestDelete = { calls += "request delete" },
        onCancelDelete = { calls += "cancel delete" },
        onConfirmDelete = { calls += "confirm delete" },
        onOpenBilling = { calls += "billing" },
        onDismissMessage = { calls += "dismiss message" }
    )

    private fun show(state: RecipientsUiState) {
        compose.setContent { OcaTheme { RecipientsScreen(state = state, callbacks = callbacks) } }
    }

    @Test
    fun `rows show the address and its status, a tap opens the actions`() {
        show(ready)

        compose.onNodeWithContentDescription("Recipient home@example.com, confirmed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recipient work@example.com, waiting for confirmation")
            .performClick()

        assertEquals(listOf("actions r-work"), calls)
    }

    @Test
    fun `add recipient opens the add sheet, back goes back`() {
        show(ready)

        compose.onNodeWithText("Add recipient", ignoreCase = true).performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("open add", "back"), calls)
    }

    @Test
    fun `add sheet sends the typed address and shows its error`() {
        show(ready.copy(adding = AddRecipient(email = "new@example.com", error = RecipientMessages.ALREADY_ADDED)))

        compose.onNodeWithText(RecipientMessages.ALREADY_ADDED).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("other@example.com")
        compose.onNodeWithText("Send confirmation email", ignoreCase = true).performClick()

        assertEquals(listOf("email other@example.com", "submit add"), calls.filterNot { it == "dismiss add" })
    }

    @Test
    fun `adding disables the button`() {
        show(ready.copy(adding = AddRecipient(email = "new@example.com", submitting = true)))

        compose.onNodeWithText("Sending…", ignoreCase = true).assertIsNotEnabled()
    }

    @Test
    fun `waiting address can get a new link and be deleted`() {
        show(ready.copy(actions = RecipientActions(work)))

        compose.onNodeWithText("Resend confirmation email", ignoreCase = true).performClick()
        compose.onNodeWithText("Delete recipient", ignoreCase = true).performClick()

        assertEquals(listOf("resend", "request delete"), calls)
    }

    @Test
    fun `confirmed address offers no resend`() {
        show(ready.copy(actions = RecipientActions(home)))

        compose.onNodeWithText("Resend confirmation email", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Delete recipient", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `delete asks first and shows the in-use error in the sheet`() {
        show(ready.copy(actions = RecipientActions(home, confirmingDelete = true, error = RecipientMessages.IN_USE)))

        compose.onNodeWithText("Delete recipient?").assertIsDisplayed()
        compose.onNodeWithText(RecipientMessages.IN_USE).assertIsDisplayed()
        compose.onNodeWithText("Delete", ignoreCase = true).performClick()
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()

        assertEquals(listOf("confirm delete", "cancel delete"), calls)
    }

    @Test
    fun `confirmation sent says where to open the link`() {
        show(ready.copy(message = RecipientsMessage.ConfirmationSent("new@example.com")))

        compose.onNodeWithText("Confirmation email sent to new@example.com", substring = true).assertIsDisplayed()
    }

    @Test
    fun `payment notice opens billing`() {
        show(ready.copy(paymentRequired = true))

        compose.onNodeWithText("Your account is not active yet").assertIsDisplayed()
        compose.onNodeWithText("Open billing", ignoreCase = true).performClick()

        assertEquals(listOf("billing"), calls)
    }

    @Test
    fun `failed first load offers retry, empty list says what to do`() {
        show(RecipientsUiState(list = ListStatus.Failed, listError = RecipientMessages.LOAD_FAILED))

        compose.onNodeWithText(RecipientMessages.LOAD_FAILED).assertIsDisplayed()
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `empty list says what to do`() {
        show(RecipientsUiState(list = ListStatus.Ready))

        compose.onNodeWithText("No recipient addresses yet", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show(ready)

        compose.onNodeWithText("Recipients").assertIsDisplayed()
    }
}
