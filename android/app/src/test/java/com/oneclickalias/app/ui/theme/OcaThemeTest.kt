package com.oneclickalias.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class OcaThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun textColorOf(text: String) = mutableListOf<TextLayoutResult>().also { results ->
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
    }.single().layoutInput.style.color

    @Test
    fun `text without explicit color uses the dark foreground`() {
        compose.setContent { OcaTheme { Text("plain") } }

        assertEquals(DarkTokens.foreground, textColorOf("plain"))
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `stays dark when the system is in light mode`() {
        var background = Color.Unspecified
        compose.setContent { OcaTheme { background = MaterialTheme.colorScheme.background } }

        assertEquals(DarkTokens.background, background)
    }
}
