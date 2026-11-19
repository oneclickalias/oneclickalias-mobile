package com.oneclickalias.app.navigation

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oneclickalias.app.AppContainer
import com.oneclickalias.app.data.api.Endpoints
import com.oneclickalias.app.data.lock.AuthPurpose
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.platform.DebugScans
import com.oneclickalias.app.testing.CombinedBackend
import com.oneclickalias.app.testing.FakeAmsBackend
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.NUMBER
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRED_NAME
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRING_CODE
import com.oneclickalias.app.testing.FakeDeviceAuthenticator
import com.oneclickalias.app.testing.InMemoryAppLockSetting
import com.oneclickalias.app.testing.InMemoryPendingClaims
import com.oneclickalias.app.testing.InMemoryRecipientMemory
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.auth.Device
import kotlinx.coroutines.cancel
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OcaNavHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val ams = FakeAmsBackend()
    private val store = InMemorySessionStore()
    private val memory = InMemoryRecipientMemory()
    private val pending = InMemoryPendingClaims()
    private val lockSetting = InMemoryAppLockSetting()
    private val prompt = FakeDeviceAuthenticator()
    private var clock = 1_000_000L
    private var hasCamera = false
    private var hasPasswordManager = false
    private var claimDeliveries = 0
    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        server.dispatcher = CombinedBackend(backend, ams)
        server.start()
    }

    @After
    fun tearDown() {
        container.appScope.cancel()
        server.close()
    }

    private fun launch() {
        val origin = server.url("/")
        container = AppContainer(
            ApplicationProvider.getApplicationContext(),
            endpoints = Endpoints(apiBase = origin, authBase = origin, webBase = origin),
            sessionStore = store,
            recipientMemory = memory,
            pendingClaims = pending,
            appLockSetting = lockSetting,
            deviceAuthenticator = prompt,
            elapsedRealtime = { clock },
            hasCamera = hasCamera,
            hasPasswordManager = { hasPasswordManager },
            scheduleClaimDelivery = { claimDeliveries++ }
        )
        compose.setContent {
            nav = rememberNavController()
            OcaTheme { OcaNavHost(container = container, navController = nav) }
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring, ignoreCase = true), 5_000)

    private fun storedSession(): Session {
        val now = System.currentTimeMillis()
        return Session("at", "rt", DEVICE, now + 900_000, now)
    }

    /** Home: the tab bar is there (the "New alias" tab is open). */
    private fun waitForAliases() = compose.waitUntilAtLeastOneExists(tab("Aliases"), 5_000)

    private fun tab(label: String) = hasText(label) and isSelectable()

    private fun openTab(label: String) = compose.onNode(tab(label)).performClick()

    private fun createAccountButton() = compose.onNodeWithText("Create account", ignoreCase = true).performScrollTo()

    /** The login screen, the first one without a session. */
    private fun waitForLogin() = waitForText("Log in")

    private fun systemBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    /** Loads of the "New alias" tab (home). */
    private fun aliasListLoads() = ams.requests.count { it == "GET /api/aliases/ready" }

    private fun windowIsSecure(): Boolean =
        compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun waitForPrompt() = compose.waitUntil(5_000) { prompt.showing }

    /** Signed in with app lock on, started cold: the lock screen with the prompt open. */
    private fun lockedColdStart() {
        store.stored = storedSession()
        lockSetting.value.value = true
        launch()
        waitForText("OneClickAlias is locked", substring = true)
        waitForPrompt()
    }

    @Test
    fun `starts at the login screen without a session, the keyboard stays closed`() {
        launch()

        waitForLogin()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsNotFocused()
        createAccountButton().assertIsDisplayed()
    }

    @Test
    fun `login offers the qr scan only on a phone with a camera`() {
        launch()
        waitForLogin()

        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `the scanner asks for the camera only on a tap`() {
        hasCamera = true
        launch()
        waitForLogin()
        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).performScrollTo().performClick()

        waitForText("Scan the code from your dashboard")
        compose.onNodeWithText("Allow camera", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Use account number", ignoreCase = true).performClick()
        waitForLogin()
    }

    @Test
    fun `a scanned code shows the device name to compare`() {
        hasCamera = true
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        backend.pairingTokenResponses[0] = { FakeAuthBackend.error(400, "pairing_pending") }
        launch()
        waitForLogin()
        compose.onNodeWithText("Scan code from dashboard", ignoreCase = true).performScrollTo().performClick()
        waitForText("Scan the code from your dashboard")

        // The debug-build hook, the same way as a text the camera recognised.
        compose.waitUntil(5_000) { DebugScans.texts.subscriptionCount.value > 0 }
        DebugScans.texts.tryEmit("OCA-PAIR-1-$PAIRING_CODE")

        waitForText(PAIRED_NAME)
        compose.onNodeWithText("Confirm in your browser", ignoreCase = true).assertIsDisplayed()
        assertEquals("POST /auth/pairing/claim", backend.requests.last())
        assertEquals(0, DebugScans.texts.subscriptionCount.value)
    }

    @Test
    fun `starts at home with a stored session`() {
        store.stored = storedSession()
        launch()

        waitForAliases()
    }

    @Test
    fun `app lock hides home at a cold start until the prompt confirms`() {
        lockedColdStart()

        assertTrue(windowIsSecure())
        compose.onNodeWithText("Aliases").assertDoesNotExist()
        assertEquals(0, aliasListLoads())

        prompt.answer(true)

        waitForAliases()
        assertEquals(listOf(AuthPurpose.Unlock), prompt.prompts)
        assertTrue(windowIsSecure())
    }

    @Test
    fun `a cancelled prompt keeps the lock screen, unlock opens it again`() {
        lockedColdStart()

        prompt.answer(false)
        waitForText("Unlock")
        compose.onNodeWithText("Aliases").assertDoesNotExist()
        compose.onNodeWithText("Unlock", ignoreCase = true).performClick()
        waitForPrompt()
        prompt.answer(true)

        waitForAliases()
        assertEquals(2, prompt.prompts.size)
    }

    @Test
    fun `log out from the lock screen lands at the login screen and turns app lock off`() {
        memory.remembered = "r-work"
        lockedColdStart()
        prompt.answer(false)
        waitForText("Unlock")

        compose.onNodeWithText("Log out", ignoreCase = true).performClick()

        waitForText("Create account")
        assertEquals("DELETE /devices/$DEVICE", backend.requests.last())
        assertNull(store.stored)
        assertFalse(lockSetting.value.value)
        assertFalse(windowIsSecure())
    }

    @Test
    fun `over a minute in the background locks again and keeps the open screen`() {
        lockedColdStart()
        prompt.answer(true)
        waitForAliases()
        openTab("Account")
        waitForText("Version", substring = true)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock += 61_000
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitForText("OneClickAlias is locked", substring = true)
        compose.onNodeWithText("Version", substring = true).assertDoesNotExist()
        waitForPrompt()
        prompt.answer(true)
        waitForText("Version", substring = true)
    }

    @Test
    fun `a short switch to another app does not lock`() {
        lockedColdStart()
        prompt.answer(true)
        waitForAliases()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock += 30_000
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitForAliases()
        compose.onNodeWithText("OneClickAlias is locked", substring = true).assertDoesNotExist()
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `turning app lock on from the account screen needs the prompt`() {
        store.stored = storedSession()
        launch()
        waitForAliases()
        openTab("Account")
        waitForText("Version", substring = true)
        assertFalse(windowIsSecure())

        compose.onNode(hasText("App lock") and isToggleable()).performScrollTo().performClick()
        waitForPrompt()
        prompt.answer(true)

        compose.waitUntil(5_000) { lockSetting.value.value }
        compose.waitUntil(5_000) { windowIsSecure() }
        compose.onNode(hasText("App lock") and isToggleable()).assertIsOn()
        assertEquals(listOf(AuthPurpose.Enable), prompt.prompts)
    }

    @Test
    fun `signed out screens are never locked`() {
        lockSetting.value.value = true
        launch()

        waitForText("Create account")
        assertTrue(prompt.prompts.isEmpty())
        assertFalse(windowIsSecure())
    }

    @Test
    fun `create account, confirm and land at home`() {
        launch()
        waitForText("Create account")
        createAccountButton().performClick()

        waitForText("4711 0000 1234 5678")
        compose.onNodeWithText("I saved my account number").performClick()
        compose.onNodeWithText("Continue", ignoreCase = true).performClick()

        waitForAliases()
        assertEquals(FakeAuthBackend.ACCESS, store.stored?.accessToken)
        // Sign-up screens are gone: back must not return to the account number.
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun `create account offers the password manager only when one is set up`() {
        launch()
        waitForText("Create account")
        createAccountButton().performClick()
        waitForText("4711 0000 1234 5678")

        compose.onNodeWithText("Save to password manager", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `create account with a password manager shows the save button`() {
        hasPasswordManager = true
        launch()
        waitForText("Create account")
        createAccountButton().performClick()
        waitForText("4711 0000 1234 5678")

        compose.onNodeWithText("Save to password manager", ignoreCase = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `log in with an account number and land at home`() {
        launch()
        waitForLogin()

        compose.onNode(hasSetTextAction()).performTextInput(NUMBER)
        compose.onNodeWithText("Log in", ignoreCase = true).performClick()

        waitForAliases()
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun `the account tab logs out to the login screen and forgets the recipient choice`() {
        store.stored = storedSession()
        memory.remembered = "r-work"
        launch()
        waitForAliases()

        openTab("Account")
        waitForText("Version", substring = true)
        compose.onNodeWithText("Log out", ignoreCase = true).performClick()

        waitForText("Create account")
        assertNull(store.stored)
        assertNull(memory.remembered)
        assertEquals("DELETE /devices/$DEVICE", backend.requests.last())
    }

    @Test
    fun `the account tab leads to recipients and devices`() {
        store.stored = storedSession()
        backend.devices += Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
        ams.addRecipient("r-home", "home@example.com")
        launch()
        waitForAliases()

        openTab("Account")
        compose.onNodeWithText("Recipients").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Recipient home@example.com", substring = true), 5_000)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Devices").performClick()

        compose.waitUntilAtLeastOneExists(
            hasContentDescription("Device brave otter, this device", substring = true),
            5_000
        )
    }

    @Test
    fun `screens switch at once, without a fade`() {
        store.stored = storedSession()
        backend.devices += Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
        launch()
        waitForAliases()
        openTab("Account")
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Devices").performClick()
        compose.mainClock.advanceTimeBy(100)
        // Only the new screen: the account tab is gone, not fading out beneath it.
        compose.onNodeWithText("Recipients").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Recipients").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `the back gesture does not move the screen, letting go switches at once`() {
        store.stored = storedSession()
        backend.devices += Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
        launch()
        waitForAliases()
        openTab("Account")
        compose.onNodeWithText("Devices").performClick()
        // The back arrow: only the devices screen has one.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Back"), 5_000)
        compose.waitForIdle()
        val before = compose.onNodeWithContentDescription("Back").getBoundsInRoot()

        compose.runOnUiThread {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        val during = compose.onNodeWithContentDescription("Back").getBoundsInRoot()

        // No animation: the screen neither shrinks nor moves while the finger is down.
        assertEquals(before, during)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilDoesNotExist(hasContentDescription("Back"), 5_000)
        assertTrue(nav.currentDestination?.hasRoute<AliasesRoute>() == true)
    }

    @Test
    fun `sign out all devices from the device screen lands at the login screen`() {
        store.stored = storedSession()
        backend.devices += Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
        memory.remembered = "r-work"
        launch()
        waitForAliases()
        openTab("Account")
        compose.onNodeWithText("Devices").performClick()
        waitForText("Lost access to a device?")

        compose.onNodeWithText("Sign out all devices", ignoreCase = true).performScrollTo().performClick()
        waitForText("Enter it below to confirm.")
        compose.onNode(hasSetTextAction()).performTextInput(NUMBER)
        compose.onNode(hasText("Sign out all devices", ignoreCase = true) and hasClickAction()).performClick()

        waitForText("All devices have been signed out", substring = true)
        assertEquals(listOf("POST /auth/devices/revoke-all", "DELETE /devices/$DEVICE"), backend.requests.takeLast(2))
        assertNull(store.stored)
        assertNull(memory.remembered)
    }

    @Test
    fun `delete account lands at the login screen with the deletion notice`() {
        store.stored = storedSession()
        memory.remembered = "r-work"
        launch()
        waitForAliases()
        openTab("Account")

        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        waitForText("Enter your account number to confirm.")
        compose.onNode(hasSetTextAction()).performTextInput(NUMBER)
        compose.onNode(hasText("Delete account", ignoreCase = true) and hasClickAction()).performClick()

        waitForText("Your account will be deleted in 12 hours", substring = true)
        createAccountButton().assertIsDisplayed()
        assertEquals("DELETE /auth/account", backend.requests.last())
        assertNull(store.stored)
        assertNull(memory.remembered)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun `device limit at login signs out all devices and logs in`() {
        var logins = 0
        backend.loginResponse = {
            if (logins++ == 0) {
                FakeAuthBackend.error(409, "device_limit_reached")
            } else {
                FakeAuthBackend.json(
                    """{"accessToken":"${FakeAuthBackend.ACCESS}","refreshToken":"rt","expiresIn":900,"deviceId":"$DEVICE"}"""
                )
            }
        }
        launch()
        waitForLogin()
        compose.onNode(hasSetTextAction()).performTextInput(NUMBER)
        compose.onNodeWithText("Log in", ignoreCase = true).performClick()

        waitForText("Device limit reached")
        compose.onNodeWithText("Sign out all devices & log in", ignoreCase = true).performScrollTo().performClick()

        waitForAliases()
        assertEquals(
            listOf("POST /auth/login", "POST /auth/devices/revoke-all", "POST /auth/login"),
            backend.requests.filter { it.startsWith("POST") }
        )
    }

    @Test
    fun `system back from another tab returns to the first tab`() {
        store.stored = storedSession()
        launch()
        waitForAliases()

        openTab("Account")
        waitForText("Version", substring = true)
        systemBack()

        waitForText("Tap an alias to copy it. A fresh one", substring = true)
        compose.onNodeWithText("Version", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the open tab is still open after a screen behind it`() {
        store.stored = storedSession()
        backend.devices += Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
        launch()
        waitForAliases()
        openTab("Account")

        compose.onNodeWithText("Devices").performClick()
        waitForText("Lost access to a device?")
        compose.onNodeWithContentDescription("Back").performClick()

        waitForText("Version", substring = true)
    }

    @Test
    fun `a new session starts at the first tab again`() {
        store.stored = storedSession()
        launch()
        waitForAliases()
        openTab("Account")
        waitForText("Version", substring = true)
        compose.onNodeWithText("Log out", ignoreCase = true).performClick()
        waitForLogin()

        compose.onNode(hasSetTextAction()).performTextInput(NUMBER)
        compose.onNodeWithText("Log in", ignoreCase = true).performClick()

        waitForText("Tap an alias to copy it. A fresh one", substring = true)
    }

    @Test
    fun `copy on a card puts the alias on the clipboard, activates it and shows the next`() {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        store.stored = storedSession()
        launch()
        waitForAliases()
        waitForText("home@example.com")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Alias ${first.alias}"), 5_000)

        compose.onNode(hasContentDescription("Alias ${first.alias}")).performClick()

        compose.waitUntil(5_000) { ams.aliases.any { it.id == first.id } }
        val next = ams.ready.single()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Alias ${next.alias}"), 5_000)
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
        assertEquals(first.alias, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(1, clipboard.primaryClip?.itemCount)
        assertEquals(0, claimDeliveries)

        // The copied alias is a normal one now: the "Aliases" tab lists it.
        openTab("Aliases")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Alias ${first.alias}", substring = true), 5_000)
    }

    @Test
    fun `copy without a connection keeps the alias waiting and says it needs the phone online`() {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        ams.claimResponse = { FakeAmsBackend.error(503, "auth_unavailable") }
        store.stored = storedSession()
        launch()
        waitForAliases()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Alias ${first.alias}"), 5_000)

        compose.onNode(hasContentDescription("Alias ${first.alias}")).performClick()

        waitForText("Go online to activate this alias", substring = true)
        waitForText("Tap to copy it again", substring = true)
        assertEquals(1, claimDeliveries)
        assertEquals(setOf(first.id), pending.ids.toSet())
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
        assertEquals(first.alias, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `add recipient on the alias screen opens the recipient screen, back decides again`() {
        store.stored = storedSession()
        launch()
        waitForAliases()
        waitForText("Confirm a recipient address first")

        compose.onNodeWithText("Add recipient", ignoreCase = true).performClick()
        waitForText("Enter the inbox", substring = true)
        compose.onNode(hasSetTextAction()).performTextInput("home@example.com")
        compose.onNodeWithText("Send confirmation email", ignoreCase = true).performClick()
        waitForText("Confirmation email sent to home@example.com", substring = true)
        // The link was opened on the website meanwhile.
        ams.recipients[0] = ams.recipients[0].copy(verified = true)
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitUntilAtLeastOneExists(hasContentDescription("forwards to home@example.com"), 5_000)
        compose.onNode(hasContentDescription("forwards to home@example.com")).assertIsDisplayed()
    }

    @Test
    fun `returning to the foreground reloads the list`() {
        store.stored = storedSession()
        launch()
        waitForAliases()
        compose.waitUntil(5_000) { aliasListLoads() == 1 }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntil(5_000) { aliasListLoads() == 2 }
    }

    @Test
    fun `device signed out elsewhere lands at the login screen on the next reload`() {
        store.stored = storedSession()
        launch()
        waitForAliases()
        compose.waitUntil(5_000) { aliasListLoads() == 1 }
        // Removed from another device: introspection and refresh refuse it now.
        ams.validTokens = emptySet()
        backend.refreshResponse = { FakeAuthBackend.error(401, "unauthorized") }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitForText("You have been signed out", substring = true)
        createAccountButton().assertIsDisplayed()
        assertNull(store.stored)
    }

    @Test
    fun `session ended by the server at start lands at the login screen with a notice`() {
        ams.validTokens = emptySet()
        backend.refreshResponse = { FakeAuthBackend.error(401, "unauthorized") }
        store.stored = storedSession()
        launch()

        waitForText("You have been signed out", substring = true)
        createAccountButton().assertIsDisplayed()
        assertNull(store.stored)
    }

    @Test
    fun `notice goes away once the user moves on`() {
        ams.validTokens = emptySet()
        backend.refreshResponse = { FakeAuthBackend.error(401, "unauthorized") }
        store.stored = storedSession()
        launch()
        waitForText("You have been signed out", substring = true)

        createAccountButton().performClick()
        waitForText("4711 0000 1234 5678")
        compose.onNodeWithContentDescription("Back").performClick()

        waitForLogin()
        compose.onNodeWithText("You have been signed out", substring = true).assertDoesNotExist()
    }

    @Test
    fun `back arrow and system back return from create account to the login screen`() {
        launch()
        waitForLogin()

        createAccountButton().performClick()
        waitForText("4711 0000 1234 5678")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForLogin()

        createAccountButton().performClick()
        waitForText("4711 0000 1234 5678")
        systemBack()
        waitForLogin()
        assertNull(nav.previousBackStackEntry)
    }
}
