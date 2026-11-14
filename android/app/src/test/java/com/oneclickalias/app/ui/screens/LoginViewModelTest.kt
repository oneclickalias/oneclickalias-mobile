package com.oneclickalias.app.ui.screens

import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.NUMBER
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.error
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import kotlinx.coroutines.test.TestScope
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

class LoginViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val store = InMemorySessionStore()
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

    private fun TestScope.viewModel(): LoginViewModel {
        sessions = SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store)
        return LoginViewModel(sessions)
    }

    private fun LoginViewModel.enter(text: String = NUMBER) = onInput(text)

    @Test
    fun `input keeps digits only and at most sixteen`() = runTest {
        val vm = viewModel()

        vm.onInput("4711 0000-1234 5678 99")

        assertEquals(NUMBER, vm.state.value.digits)
    }

    @Test
    fun `submit is only possible with sixteen digits`() = runTest {
        val vm = viewModel()
        vm.onInput("471100001234567")

        assertFalse(vm.state.value.canSubmit)
        vm.submit()

        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `successful login signs in`() = runTest {
        val vm = viewModel()
        vm.enter()

        vm.submit()

        eventually { vm.state.value.signedIn }
        assertEquals(SessionState.SignedIn, sessions.state.value)
        assertEquals(listOf("POST /auth/login"), backend.requests)
    }

    @Test
    fun `wrong number shows its text`() = runTest {
        backend.loginResponse = { error(401, "unauthorized") }
        val vm = viewModel()
        vm.enter()

        vm.submit()

        eventually { vm.state.value.error != null }
        assertEquals(AuthMessages.WRONG_NUMBER, vm.state.value.error)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `editing clears the error`() = runTest {
        backend.loginResponse = { error(401, "unauthorized") }
        val vm = viewModel()
        vm.enter()
        vm.submit()
        eventually { vm.state.value.error != null }

        vm.onInput("471100001234567")

        assertNull(vm.state.value.error)
    }

    @Test
    fun `unconfirmed registration asks for the confirmation, then confirms and logs in`() = runTest {
        backend.loginResponse = { error(403, "registration_not_confirmed") }
        val vm = viewModel()
        vm.enter()
        vm.submit()
        eventually { vm.state.value.needsConfirmation }
        assertEquals(AuthMessages.CONFIRM_TO_CONTINUE, vm.state.value.error)
        assertFalse(vm.state.value.canSubmit)

        backend.loginResponse = FakeAuthBackend().loginResponse
        vm.setConfirmed(true)
        vm.submit()

        eventually { vm.state.value.signedIn }
        assertEquals(
            listOf("POST /auth/login", "POST /auth/confirm-registration", "POST /auth/login"),
            backend.requests
        )
    }

    @Test
    fun `rate limit and server errors get their texts`() = runTest {
        val cases = listOf(
            error(429, "rate_limited") to AuthMessages.RATE_LIMITED,
            error(500, "internal_error") to AuthMessages.SERVER_ERROR,
            error(418, "brand_new_code") to AuthMessages.LOGIN_FAILED,
            FakeAuthBackend.json("garbage", 502) to AuthMessages.SERVER_ERROR
        )
        val vm = viewModel()
        vm.enter()
        for ((response, text) in cases) {
            backend.loginResponse = { response }
            vm.submit()
            eventually { vm.state.value.error == text && !vm.state.value.submitting }
        }
    }

    @Test
    fun `device limit offers sign out all instead of an error`() = runTest {
        backend.loginResponse = { error(409, "device_limit_reached") }
        val vm = viewModel()
        vm.enter()

        vm.submit()

        eventually { vm.state.value.deviceLimitReached && !vm.state.value.submitting }
        assertNull(vm.state.value.error)
    }

    @Test
    fun `sign out all devices with the typed number, then log in`() = runTest {
        backend.loginResponse = { error(409, "device_limit_reached") }
        val vm = viewModel()
        vm.enter()
        vm.submit()
        eventually { vm.state.value.deviceLimitReached && !vm.state.value.submitting }
        backend.loginResponse = {
            FakeAuthBackend.json(
                """{"accessToken":"at","refreshToken":"rt","expiresIn":900,"deviceId":"${FakeAuthBackend.DEVICE}"}"""
            )
        }

        vm.revokeAllAndLogIn()

        eventually { vm.state.value.signedIn }
        assertEquals(listOf("POST /auth/login", "POST /auth/devices/revoke-all", "POST /auth/login"), backend.requests)
        assertEquals("""{"accountNumber":"$NUMBER"}""", backend.bodies[1])
        assertEquals(SessionState.SignedIn, sessions.state.value)
        assertFalse(vm.state.value.deviceLimitReached)
    }

    @Test
    fun `failed sign out all keeps the offer and shows the error`() = runTest {
        backend.loginResponse = { error(409, "device_limit_reached") }
        backend.revokeAllResponse = { error(429, "rate_limited") }
        val vm = viewModel()
        vm.enter()
        vm.submit()
        eventually { vm.state.value.deviceLimitReached && !vm.state.value.submitting }

        vm.revokeAllAndLogIn()

        eventually { vm.state.value.error != null }
        assertEquals(AuthMessages.RATE_LIMITED, vm.state.value.error)
        assertTrue(vm.state.value.deviceLimitReached)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `editing the number withdraws the offer`() = runTest {
        backend.loginResponse = { error(409, "device_limit_reached") }
        val vm = viewModel()
        vm.enter()
        vm.submit()
        eventually { vm.state.value.deviceLimitReached && !vm.state.value.submitting }

        vm.onInput("4711")

        assertFalse(vm.state.value.deviceLimitReached)
    }

    @Test
    fun `no connection is not a server error`() = runTest {
        val vm = viewModel()
        server.close()
        vm.enter()

        vm.submit()

        eventually { vm.state.value.error != null }
        assertEquals(AuthMessages.OFFLINE, vm.state.value.error)
    }

    @Test
    fun `reveal toggles masking`() = runTest {
        val vm = viewModel()

        vm.toggleReveal()
        assertTrue(vm.state.value.revealed)
        vm.toggleReveal()
        assertFalse(vm.state.value.revealed)
    }

    @Test
    fun `state never prints the typed number`() {
        val state = LoginUiState(digits = NUMBER)

        assertFalse(state.toString().contains(NUMBER))
        assertFalse(state.toString().contains("4711"))
    }
}
