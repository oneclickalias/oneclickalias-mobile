package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h856dp-xxhdpi")
class AliasAddressTest {
    @get:Rule
    val compose = createComposeRule()

    /** Text width of an alias row on a 384dp phone: 16dp gutters, 1dp card lines, 14dp padding, 48dp "⋯". */
    private val rowTextWidth = (384 - 2 * 16 - 2 * 1 - 14 - 48).dp

    private fun layout(alias: String): Pair<String, TextLayoutResult> {
        compose.setContent {
            OcaTheme {
                Box(Modifier.width(rowTextWidth)) { AliasAddress(alias = alias, modifier = Modifier.testTag("a")) }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("a").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val result = results.single()
        return result.layoutInput.text.text to result
    }

    @Test
    fun `display text only adds invisible characters`() {
        val shown = aliasDisplayText("ninechars-ninechars-0000@rmails.net")

        assertEquals("ninechars-ninechars-0000@rmails.net", shown.filter { it != '​' && it != '⁠' })
        assertTrue(shown.contains("​@"))
    }

    @Test
    fun `text without an at sign stays as it is`() {
        assertEquals("no-at-sign", aliasDisplayText("no-at-sign"))
    }

    // EFF long-list words have at most 9 letters: word-word-NNNN is the longest local part.

    @Test
    fun `longest diceware alias on the production domain fits in one line`() {
        assertEquals(1, layout("ninechars-ninechars-0000@rmails.net").second.lineCount)
    }

    @Test
    fun `longest diceware alias on the local domain fits in one line`() {
        assertEquals(1, layout("ninechars-ninechars-0000@alias.local").second.lineCount)
    }

    @Test
    fun `a too long alias breaks before the at sign, not in the domain`() {
        val (text, result) = layout("ninechars-ninechars-0000@forwarding.example-mail.net")

        assertEquals(2, result.lineCount)
        val secondLine = text.substring(result.getLineStart(1)).filter { it != '⁠' }
        assertEquals("@forwarding.example-mail.net", secondLine)
    }
}
