package com.oneclickalias.app.data.lock

import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.testing.FakeDeviceAuthenticator
import com.oneclickalias.app.testing.InMemoryAppLockSetting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockTest {
    private val setting = InMemoryAppLockSetting()
    private val prompt = FakeDeviceAuthenticator()
    private val session = MutableStateFlow<SessionState>(SessionState.Loading)
    private var clock = 5_000_000L
    private var logouts = 0
    private var logoutDone: CompletableDeferred<Unit>? = null

    private fun TestScope.appLock(): AppLock = AppLock(
        setting = setting,
        authenticator = prompt,
        sessionState = session,
        scope = backgroundScope,
        logout = {
            logouts++
            logoutDone?.await()
            session.value = SessionState.SignedOut()
            setting.clear()
        },
        clock = { clock }
    ).also { runCurrent() }

    /** Cold start of a signed-in app with app lock on, activity in the foreground. */
    private fun TestScope.lockedColdStart(): AppLock {
        setting.value.value = true
        session.value = SessionState.SignedIn
        return appLock().also {
            it.onForeground()
            runCurrent()
        }
    }

    /** Signed in, app lock on and unlocked. */
    private fun TestScope.unlockedApp(): AppLock = lockedColdStart().also {
        prompt.answer(true)
        runCurrent()
    }

    private fun TestScope.background(lock: AppLock, millis: Long) {
        lock.onBackground()
        clock += millis
        lock.onForeground()
        runCurrent()
    }

    @Test
    fun `nothing is shown until the setting and the session are known`() = runTest {
        val lock = appLock()

        assertFalse(lock.state.value.ready)

        session.value = SessionState.SignedIn
        runCurrent()

        assertTrue(lock.state.value.ready)
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `off by default, a cold start stays open`() = runTest {
        session.value = SessionState.SignedIn
        val lock = appLock()
        lock.onForeground()
        runCurrent()

        assertEquals(
            AppLockState(ready = true, enabled = false, locked = false, canAuthenticate = true),
            lock.state.value
        )
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun `cold start with app lock on is locked and prompts once by itself`() = runTest {
        val lock = lockedColdStart()

        assertTrue(lock.state.value.locked)
        assertTrue(lock.state.value.prompting)
        assertEquals(listOf(AuthPurpose.Unlock), prompt.prompts)
    }

    @Test
    fun `no prompt before the activity is in the foreground`() = runTest {
        setting.value.value = true
        session.value = SessionState.SignedIn
        val lock = appLock()

        assertTrue(lock.state.value.locked)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun `successful prompt unlocks`() = runTest {
        val lock = lockedColdStart()

        prompt.answer(true)
        runCurrent()

        assertFalse(lock.state.value.locked)
        assertFalse(lock.state.value.prompting)
        assertTrue(lock.state.value.enabled)
    }

    @Test
    fun `cancelled prompt keeps the lock screen and does not reopen by itself`() = runTest {
        val lock = lockedColdStart()

        prompt.answer(false)
        runCurrent()
        background(lock, 1_000)

        assertTrue(lock.state.value.locked)
        assertFalse(lock.state.value.prompting)
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `unlock button starts the prompt again`() = runTest {
        val lock = lockedColdStart()
        prompt.answer(false)
        runCurrent()

        lock.unlock()
        runCurrent()
        assertEquals(2, prompt.prompts.size)
        prompt.answer(true)
        runCurrent()

        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `unlock while the prompt is open does not open a second one`() = runTest {
        val lock = lockedColdStart()

        lock.unlock()
        runCurrent()

        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `59 seconds in the background do not lock`() = runTest {
        val lock = unlockedApp()

        background(lock, 59_000)

        assertFalse(lock.state.value.locked)
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `61 seconds in the background lock and prompt again`() = runTest {
        val lock = unlockedApp()

        background(lock, 61_000)

        assertTrue(lock.state.value.locked)
        assertTrue(lock.state.value.prompting)
        assertEquals(2, prompt.prompts.size)
    }

    @Test
    fun `time spent in the prompt does not count as background`() = runTest {
        // Before Android 10 the screen-lock check is its own activity: this one stops meanwhile.
        val lock = lockedColdStart()

        lock.onBackground()
        clock += 120_000
        lock.onForeground()
        prompt.answer(true)
        runCurrent()

        assertFalse(lock.state.value.locked)
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `without app lock a long background stays open`() = runTest {
        session.value = SessionState.SignedIn
        val lock = appLock()
        lock.onForeground()

        background(lock, 600_000)

        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `signed out screens are never locked`() = runTest {
        setting.value.value = true
        session.value = SessionState.SignedOut()
        val lock = appLock()
        lock.onForeground()
        runCurrent()

        background(lock, 600_000)

        assertTrue(lock.state.value.ready)
        assertFalse(lock.state.value.locked)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun `phone without a screen lock stays locked and only offers log out`() = runTest {
        prompt.available = false
        val lock = lockedColdStart()

        assertTrue(lock.state.value.locked)
        assertFalse(lock.state.value.canAuthenticate)
        assertTrue(prompt.prompts.isEmpty())

        lock.unlock()
        runCurrent()
        assertTrue(lock.state.value.locked)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun `screen lock removed while the app was away is noticed on return`() = runTest {
        val lock = unlockedApp()

        lock.onBackground()
        prompt.available = false
        clock += 61_000
        lock.onForeground()
        runCurrent()

        assertTrue(lock.state.value.locked)
        assertFalse(lock.state.value.canAuthenticate)
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun `log out from the lock screen ends the session and opens the app signed out`() = runTest {
        val lock = lockedColdStart()
        prompt.answer(false)
        runCurrent()
        logoutDone = CompletableDeferred()

        lock.logOut()
        lock.logOut()
        runCurrent()
        assertTrue(lock.state.value.loggingOut)
        logoutDone?.complete(Unit)
        runCurrent()

        assertEquals(1, logouts)
        assertFalse(lock.state.value.locked)
        assertFalse(lock.state.value.enabled)
        assertFalse(lock.state.value.loggingOut)
    }

    @Test
    fun `turning it on needs a successful prompt`() = runTest {
        session.value = SessionState.SignedIn
        val lock = appLock()
        lock.onForeground()

        lock.setEnabled(true)
        runCurrent()
        assertEquals(listOf(AuthPurpose.Enable), prompt.prompts)
        assertTrue(lock.state.value.prompting)
        assertFalse(lock.state.value.enabled)

        prompt.answer(true)
        runCurrent()

        assertTrue(setting.value.value)
        assertTrue(lock.state.value.enabled)
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `cancelled prompt leaves it off`() = runTest {
        session.value = SessionState.SignedIn
        val lock = appLock()
        lock.onForeground()

        lock.setEnabled(true)
        runCurrent()
        prompt.answer(false)
        runCurrent()

        assertFalse(setting.value.value)
        assertFalse(lock.state.value.enabled)
        assertFalse(lock.state.value.prompting)
    }

    @Test
    fun `cannot be turned on without a screen lock`() = runTest {
        prompt.available = false
        session.value = SessionState.SignedIn
        val lock = appLock()
        lock.onForeground()

        lock.setEnabled(true)
        runCurrent()

        assertTrue(prompt.prompts.isEmpty())
        assertFalse(setting.value.value)
        assertFalse(lock.state.value.canAuthenticate)
    }

    @Test
    fun `turning it off needs no prompt`() = runTest {
        val lock = unlockedApp()

        lock.setEnabled(false)
        runCurrent()

        assertFalse(setting.value.value)
        assertFalse(lock.state.value.enabled)
        assertEquals(1, prompt.prompts.size)
        background(lock, 600_000)
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `session end resets the setting, a new login starts unlocked and off`() = runTest {
        val lock = unlockedApp()

        // SessionManager's clearAccountData hook clears the setting when the session ends.
        session.value = SessionState.SignedOut()
        setting.clear()
        runCurrent()
        session.value = SessionState.SignedIn
        runCurrent()
        background(lock, 600_000)

        assertFalse(lock.state.value.enabled)
        assertFalse(lock.state.value.locked)
    }
}
