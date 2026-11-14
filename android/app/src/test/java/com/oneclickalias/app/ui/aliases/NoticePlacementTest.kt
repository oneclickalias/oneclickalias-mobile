package com.oneclickalias.app.ui.aliases

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.ui.theme.OcaTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Switching between the two alias tabs must not move a notice both of them show. */
@RunWith(AndroidJUnit4::class)
class NoticePlacementTest {
    @get:Rule
    val compose = createComposeRule()

    private var tab by mutableStateOf(HomeTab.NewAlias)

    private fun show() = compose.setContent {
        OcaTheme {
            HomeFrame(tab = tab, onSelectTab = { tab = it }) {
                when (tab) {
                    HomeTab.NewAlias -> CopyScreen(
                        state = CopyUiState(list = ListStatus.Ready, paymentRequired = true),
                        callbacks = CopyCallbacks()
                    )

                    else -> AliasesScreen(
                        state = AliasesUiState(list = ListStatus.Ready, paymentRequired = true),
                        callbacks = AliasesCallbacks()
                    )
                }
            }
        }
    }

    private fun notice(): DpRect = compose.onNodeWithText("Your account is not active yet").getUnclippedBoundsInRoot()

    private fun assertSamePlace(qualifiers: String) {
        show()
        val onNewAlias = notice()
        tab = HomeTab.Aliases
        compose.waitForIdle()
        val onAliases = notice()

        // Not even a pixel (a third of a dp here): any jump shows when switching tabs.
        val moved = abs((onNewAlias.top - onAliases.top).value)
        assertTrue("the notice moves by ${moved}dp on $qualifiers", moved < 0.1f)
        assertTrue(abs((onNewAlias.left - onAliases.left).value) < 0.1f)
        assertTrue(abs((onNewAlias.right - onAliases.right).value) < 0.1f)
    }

    @Test
    @Config(qualifiers = "w384dp-h856dp-xxhdpi")
    fun `the payment notice stays in place between the tabs`() = assertSamePlace("a 384dp phone")

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `the payment notice stays in place on a narrower phone`() = assertSamePlace("a 360dp phone")
}
