package com.oneclickalias.app.ui.aliases

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone like the Galaxy A22: room for three cards.
@Config(qualifiers = "w384dp-h856dp-xxhdpi")
class CopyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callbacks = CopyCallbacks(
        onRetry = { calls += "retry" },
        onCopy = { calls += "copy $it" },
        onOpenBilling = { calls += "billing" },
        onOpenRecipients = { calls += "recipients $it" }
    )

    private fun card(n: Int) = Recipient("r$n", "inbox$n@example.com", true, "2026-11-01T10:00:00Z").let {
        CopyCard(it, alias = Alias("a$n", "quiet-otter-000$n@alias.test", it.email, it.id, true, "", ""))
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun show(state: CopyUiState) {
        compose.setContent { OcaTheme { CopyScreen(state = state, callbacks = callbacks) } }
    }

    private fun showCards(count: Int) = show(CopyUiState(cards = (1..count).map(::card), list = ListStatus.Ready))

    private fun top(n: Int): Dp = entry(n).getUnclippedBoundsInRoot().top

    /** The whole card is one button; screen readers hear the alias and where it forwards to. */
    private fun entry(n: Int) = compose.onNode(hasContentDescription("Alias quiet-otter-000$n@alias.test"))

    private fun head() = compose.onNodeWithText("Tap an alias to copy it", substring = true).getUnclippedBoundsInRoot()

    /**
     * How far the middle of cards [first]..[last] together is from the middle of
     * the space under the head (which ends 16dp below its text).
     */
    private fun groupOffCentre(first: Int, last: Int): Dp {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val groupMiddle = (top(first) + entry(last).getUnclippedBoundsInRoot().bottom) / 2
        return (groupMiddle - (head().bottom + 16.dp + root.bottom) / 2).let { if (it < 0.dp) -it else it }
    }

    @Test
    fun `a single card sits in the middle of the screen`() {
        showCards(1)

        assertTrue("off by ${groupOffCentre(1, 1)}", groupOffCentre(1, 1) < 2.dp)
    }

    @Test
    fun `two cards sit in the middle together, in server order`() {
        showCards(2)

        assertTrue("off by ${groupOffCentre(1, 2)}", groupOffCentre(1, 2) < 2.dp)
        assertTrue(top(2) > top(1))
    }

    @Test
    fun `three cards sit in the middle together, in server order`() {
        showCards(3)

        assertTrue("off by ${groupOffCentre(1, 3)}", groupOffCentre(1, 3) < 2.dp)
        assertEquals(listOf(1, 2, 3), (1..3).sortedBy { top(it).value })
    }

    @Test
    @Config(qualifiers = "w384dp-h560dp-xxhdpi")
    fun `more cards than fit start right under the head and scroll`() {
        showCards(5)

        assertTrue(top(1) - head().bottom < 40.dp)
        assertEquals(listOf(1, 2, 3, 4, 5), (1..5).sortedBy { top(it).value })
        entry(5).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a tap on the card copies for its recipient`() {
        showCards(2)

        entry(2).assert(clickLabel("copy")).performClick()

        assertEquals(listOf("copy r2"), calls)
    }

    @Test
    fun `a card shows the whole address in one line and who it forwards to`() {
        showCards(1)

        compose.onNodeWithText("quiet-otter-0001@alias.test").assertIsDisplayed()
        compose.onNodeWithText("inbox1@example.com").assertIsDisplayed()
        entry(1).assert(hasContentDescription("forwards to inbox1@example.com"))
        val lines = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("quiet-otter-0001@alias.test").fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(lines)
        assertEquals(1, lines.single().lineCount)
    }

    @Test
    fun `the longest alias still fits in one line`() {
        val long = card(1).let {
            it.copy(alias = it.alias!!.copy(alias = "ninechars-ninechars-0000@rmails.net"))
        }
        show(CopyUiState(cards = listOf(long), list = ListStatus.Ready))

        val lines = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("ninechars-ninechars-0000@rmails.net").fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(lines)
        assertEquals(1, lines.single().lineCount)
        assertFalse(lines.single().hasVisualOverflow)
    }

    @Test
    fun `a card without its alias yet cannot be copied`() {
        show(CopyUiState(cards = listOf(card(1).copy(alias = null, working = true)), list = ListStatus.Ready))

        compose.onNodeWithText("Getting your alias…").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("inbox1@example.com")).assertIsNotEnabled()
    }

    @Test
    fun `a copy made offline says so and is copied again with a tap`() {
        val waiting = card(1).let { it.copy(alias = null, copied = it.alias) }
        show(CopyUiState(cards = listOf(waiting), list = ListStatus.Ready))

        compose.onNodeWithText("Go online to activate this alias", substring = true).assertIsDisplayed()
        entry(1).performClick()

        assertEquals(listOf("copy r1"), calls)
    }

    @Test
    fun `a card that got no alias retries with a tap`() {
        show(CopyUiState(cards = listOf(card(1).copy(alias = null, error = "Server error")), list = ListStatus.Ready))

        compose.onNodeWithText("Server error").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("inbox1@example.com")).assert(clickLabel("retry")).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `a failed reload keeps the cards and offers retry above the tab bar`() {
        show(CopyUiState(cards = listOf(card(1)), list = ListStatus.Ready, loadError = "No connection"))

        entry(1).assertIsDisplayed()
        compose.onNodeWithText("No connection").assertIsDisplayed()
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `without a recipient the notice leads to adding one`() {
        show(CopyUiState(list = ListStatus.Ready, noRecipient = false))

        compose.onNodeWithText("Add recipient", ignoreCase = true).performClick()

        assertEquals(listOf("recipients true"), calls)
    }

    @Test
    fun `an unpaid account sees the payment notice instead of cards`() {
        show(CopyUiState(list = ListStatus.Ready, paymentRequired = true))

        compose.onNodeWithText("Open billing", ignoreCase = true).performClick()

        assertEquals(listOf("billing"), calls)
    }

    @Test
    @Config(qualifiers = "w384dp-h856dp-night-xxhdpi")
    fun `renders in dark mode`() {
        showCards(3)

        entry(1).assertIsDisplayed()
    }
}
