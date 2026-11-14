package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private var tab by mutableStateOf(HomeTab.NewAlias)

    private fun show() = compose.setContent {
        OcaTheme {
            HomeFrame(tab = tab, onSelectTab = { tab = it }) {
                Box(Modifier.fillMaxSize().testTag("content")) { Text("Content of ${tab.name}") }
            }
        }
    }

    private fun item(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun `the three tabs are in a bar at the bottom of the screen`() {
        show()

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("content").getUnclippedBoundsInRoot()
        listOf("New alias", "Aliases", "Account").forEach { label ->
            val bounds = item(label).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$label is below the content", bounds.top >= content.bottom)
            assertTrue("$label reaches the bottom", root.bottom - bounds.bottom < 1.dp)
            assertTrue("$label is tall enough to hit", bounds.bottom - bounds.top >= 48.dp)
        }
    }

    @Test
    fun `the open tab is marked selected, a tap opens another`() {
        show()
        item("New alias").assertIsSelected()
        item("Aliases").assertIsNotSelected()

        item("Aliases").performClick()

        assertEquals(HomeTab.Aliases, tab)
        item("Aliases").assertIsSelected()
        compose.onNodeWithText("Content of Aliases").assertIsDisplayed()
    }
}
