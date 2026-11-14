package com.oneclickalias.app.ui.aliases

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.data.aliases.RecipientDecision
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AliasesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Recipient("r-home", "home@example.com", true, "2026-11-01T10:00:00Z")
    private val work = Recipient("r-work", "work@example.com", true, "2026-11-02T10:00:00Z")
    private val alias = Alias(
        id = "a1",
        alias = "quiet-otter-1234@alias.test",
        destEmail = "home@example.com",
        recipientId = "r-home",
        blockTrackers = true,
        createdAt = "2026-11-10T10:00:00Z",
        updatedAt = "2026-11-10T10:00:00Z"
    )
    private val ready = AliasesUiState(
        aliases = listOf(alias),
        list = ListStatus.Ready
    )
    private val calls = mutableListOf<String>()

    private val callbacks = AliasesCallbacks(
        onRefresh = { calls += "refresh" },
        onRetry = { calls += "retry" },
        onCopy = { calls += "copy ${it.alias}" },
        onOpenActions = { calls += "actions $it" },
        onDismissActions = { calls += "dismiss actions" },
        onBlockTrackers = { calls += "trackers $it" },
        onRequestDelete = { calls += "request delete" },
        onCancelDelete = { calls += "cancel delete" },
        onConfirmDelete = { calls += "confirm delete" },
        onOpenBilling = { calls += "billing" },
        onDismissMessage = { calls += "dismiss message" }
    )

    private fun show(state: AliasesUiState) {
        compose.setContent { OcaTheme { AliasesScreen(state = state, callbacks = callbacks) } }
    }

    @Test
    fun `list shows address and destination, a tap copies`() {
        show(ready)

        compose.onNodeWithText("Aliases").assertIsDisplayed()
        compose.onNode(hasContentDescription("Alias quiet-otter-1234@alias.test, forwards to home@example.com"))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("copy quiet-otter-1234@alias.test"), calls)
    }

    @Test
    fun `screen readers hear what a tap on a row does`() {
        show(ready)

        val row = compose.onNode(hasContentDescription("Alias quiet-otter-1234@alias.test", substring = true))
        row.assert(
            SemanticsMatcher("click label is copy") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "copy"
            }
        )
        row.assert(
            SemanticsMatcher("long press opens the actions") {
                it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "show actions"
            }
        )
    }

    @Test
    fun `the more button opens the actions`() {
        show(ready)

        compose.onNodeWithContentDescription("Actions for quiet-otter-1234@alias.test").performClick()

        assertEquals(listOf("actions a1"), calls)
    }

    @Test
    fun `empty list says how to start`() {
        show(ready.copy(aliases = emptyList()))

        compose.onNodeWithText("No aliases yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `first load shows a loading line`() {
        show(AliasesUiState(loading = true))

        compose.onNodeWithText("Loading your aliases…").assertIsDisplayed()
    }

    @Test
    fun `failed first load offers retry`() {
        show(AliasesUiState(list = ListStatus.Failed, listError = "No connection — check your internet connection."))

        compose.onNodeWithText("No connection", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `failed reload keeps the list and offers retry`() {
        show(ready.copy(listError = "Server error — please try again later."))

        compose.onNodeWithText("Server error", substring = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Alias quiet-otter-1234@alias.test", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `payment notice opens billing`() {
        show(ready.copy(paymentRequired = true))

        compose.onNodeWithText("Your account is not active yet").assertIsDisplayed()
        compose.onNodeWithText("Open billing", ignoreCase = true).performClick()

        assertEquals(listOf("billing"), calls)
    }

    @Test
    fun `messages above the button`() {
        show(ready.copy(message = AliasesMessage.Error(AliasMessages.RATE_LIMITED)))
        compose.onNodeWithText(AliasMessages.RATE_LIMITED).assertIsDisplayed()
    }

    @Test
    fun `copied and deleted confirmations`() {
        var state by mutableStateOf(
            ready.copy(message = AliasesMessage.Copied(alias.alias))
        )
        compose.setContent { OcaTheme { AliasesScreen(state = state, callbacks = callbacks) } }
        compose.onNodeWithText("Copied to clipboard").assertIsDisplayed()

        state = ready.copy(message = AliasesMessage.Deleted(alias.alias))
        compose.onNodeWithText("Alias deleted").assertIsDisplayed()
    }

    @Test
    fun `aliases are grouped under the address they forward to`() {
        val toWork = alias.copy(id = "a2", alias = "brave-heron-5678@alias.test", destEmail = "work@example.com")
        val older = alias.copy(id = "a3", alias = "calm-lynx-9012@alias.test")
        show(ready.copy(aliases = listOf(toWork, alias, older)))

        fun top(description: String) =
            compose.onNode(hasContentDescription(description, substring = true)).fetchSemanticsNode().positionInRoot.y

        val work = top("Forwards to work@example.com, 1 alias")
        val workAlias = top("Alias brave-heron-5678@alias.test")
        val home = top("Forwards to home@example.com, 2 aliases")
        val homeAlias = top("Alias quiet-otter-1234@alias.test")
        val homeOlder = top("Alias calm-lynx-9012@alias.test")
        assertEquals(
            listOf(work, workAlias, home, homeAlias, homeOlder).sorted(),
            listOf(work, workAlias, home, homeAlias, homeOlder)
        )
        assertTrue(work < workAlias && home < homeAlias)
    }

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `a new account sees the payment notice in the middle, right under a plain head`() {
        show(AliasesUiState(list = ListStatus.Ready, paymentRequired = true))

        val head = compose.onNodeWithText("Aliases").getUnclippedBoundsInRoot()
        val intro = compose.onNodeWithText("No aliases yet", substring = true).getUnclippedBoundsInRoot()
        // The line under the heading follows it directly, as on the "New alias" tab.
        assertTrue(intro.top - head.bottom < 8.dp)
        val notice = compose.onNodeWithText("Your account is not active yet").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("notice is in the lower half", notice.top > root.bottom / 3)
        assertTrue("notice is not at the bottom", root.bottom - notice.bottom > 100.dp)
        compose.onNodeWithText("Open billing", ignoreCase = true).performClick()
        assertEquals(listOf("billing"), calls)
    }

    @Test
    fun `the head says what a tap does once there are aliases`() {
        show(ready)

        compose.onNodeWithText("Tap an alias to copy it.").assertIsDisplayed()
    }

    @Test
    fun `actions sheet switches trackers and starts the delete`() {
        show(ready.copy(actions = AliasActions(alias)))

        compose.onNodeWithText("Block trackers").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Delete alias", ignoreCase = true).performScrollTo().performClick()

        assertEquals(listOf("trackers false", "request delete"), calls)
    }

    @Test
    fun `tracker switch shows the saved value`() {
        show(ready.copy(actions = AliasActions(alias.copy(blockTrackers = false), saving = true)))

        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun `delete needs a confirmation with the website's warning`() {
        show(ready.copy(actions = AliasActions(alias, confirmingDelete = true)))

        compose.onNodeWithText("Delete alias?").assertIsDisplayed()
        compose.onNodeWithText("This can't be undone, and the name won't be reused.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Cancel", ignoreCase = true).performClick()
        compose.onNodeWithText("Delete", ignoreCase = true).performClick()

        assertEquals(listOf("cancel delete", "confirm delete"), calls)
    }

    @Test
    fun `sheet errors are shown in the sheet`() {
        show(ready.copy(actions = AliasActions(alias, error = AliasMessages.RATE_LIMITED)))

        compose.onNodeWithText(AliasMessages.RATE_LIMITED).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "night")
    fun `renders in dark mode`() {
        show(ready.copy(paymentRequired = true))

        compose.onNode(hasContentDescription("Alias quiet-otter-1234@alias.test", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Your account is not active yet").assertIsDisplayed()
    }
}
