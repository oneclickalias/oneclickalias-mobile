package com.oneclickalias.app.ui.screens

import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.data.session.SignOutNotice
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.NUMBER
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.app.ui.devices.AccountMessages
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NumberConfirmViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val store = InMemorySessionStore(
        Session("at", "rt", DEVICE, System.currentTimeMillis() + 900_000, System.currentTimeMillis())
    )
    private lateinit var sessions: SessionManager

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun signedIn(): SessionManager =
        SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store).also {
            it.restore()
            sessions = it
        }

    @Test
    fun `button needs all 16 digits, input keeps digits only`() = runTest {
        val vm = NumberConfirmViewModel.revokeAll(signedIn())

        vm.onInput("4711 0000 1234")
        assertEquals("471100001234", vm.state.value.digits)
        assertFalse(vm.state.value.canSubmit)

        vm.onInput("4711-0000-1234-5678-99")
        assertEquals(NUMBER, vm.state.value.digits)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `sign out all devices ends this session too`() = runTest {
        val vm = NumberConfirmViewModel.revokeAll(signedIn())
        vm.onInput(NUMBER)

        vm.submit()

        eventually { sessions.state.value is SessionState.SignedOut }
        // Then this device signs itself out too, in case the number was another account's.
        assertEquals(listOf("POST /auth/devices/revoke-all", "DELETE /devices/$DEVICE"), backend.requests)
        assertEquals("""{"accountNumber":"$NUMBER"}""", backend.bodies.first())
        assertEquals(SessionState.SignedOut(SignOutNotice.AllDevicesSignedOut), sessions.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `an unknown number keeps the session and says so`() = runTest {
        backend.revokeAllResponse = { FakeAuthBackend.error(401, "unauthorized") }
        val vm = NumberConfirmViewModel.revokeAll(signedIn())
        vm.onInput(NUMBER)

        vm.submit()

        eventually { vm.state.value.error != null }
        assertEquals(AuthMessages.WRONG_NUMBER, vm.state.value.error)
        assertFalse(vm.state.value.submitting)
        assertEquals(SessionState.SignedIn, sessions.state.value)
    }

    @Test
    fun `delete account sends the number and lands signed out with the deletion notice`() = runTest {
        val vm = NumberConfirmViewModel.deleteAccount(signedIn())
        vm.onInput(NUMBER)

        vm.submit()

        eventually { sessions.state.value is SessionState.SignedOut }
        assertEquals(listOf("DELETE /auth/account"), backend.requests)
        assertEquals(SessionState.SignedOut(SignOutNotice.AccountDeletionRequested), sessions.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `delete account with another account's number says it does not match`() = runTest {
        backend.deleteAccountResponse = { FakeAuthBackend.error(403, "forbidden") }
        val vm = NumberConfirmViewModel.deleteAccount(signedIn())
        vm.onInput(NUMBER)

        vm.submit()

        eventually { vm.state.value.error != null }
        assertEquals(AccountMessages.WRONG_ACCOUNT_NUMBER, vm.state.value.error)
        assertEquals("at", store.stored?.accessToken)
    }

    @Test
    fun `typing clears the error`() = runTest {
        backend.deleteAccountResponse = { FakeAuthBackend.error(403, "forbidden") }
        val vm = NumberConfirmViewModel.deleteAccount(signedIn())
        vm.onInput(NUMBER)
        vm.submit()
        eventually { vm.state.value.error != null }

        vm.onInput("4711")

        assertNull(vm.state.value.error)
    }

    @Test
    fun `state never prints the number`() = runTest {
        val vm = NumberConfirmViewModel.deleteAccount(signedIn())
        vm.onInput(NUMBER)
        vm.toggleReveal()

        assertFalse(vm.state.value.toString().contains(NUMBER.takeLast(8)))
    }
}
