package com.oneclickalias.app.ui.screens

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateAccountViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val store = InMemorySessionStore()
    private val copied = mutableListOf<Pair<String, String>>()
    private lateinit var api: AuthApi
    private var sessionManager: SessionManager? = null

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
        api = AuthApi(ApiTransport(createHttpClient()), server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        CreateAccountViewModel(api, sessions(), { label, secret -> copied += label to secret }, saved)

    private fun kotlinx.coroutines.test.TestScope.sessions(): SessionManager =
        sessionManager ?: SessionManager(api, store).also { sessionManager = it }

    private fun CreateAccountViewModel.ready() = state.value as CreateAccountUiState.Ready

    @Test
    fun `creates an account number on start`() = runTest {
        val vm = viewModel()

        eventually { vm.state.value is CreateAccountUiState.Ready }

        assertEquals(NUMBER, vm.ready().accountNumber)
        assertEquals(listOf("POST /auth/account-number"), backend.requests)
    }

    @Test
    fun `creation failure can be retried`() = runTest {
        backend.createResponse = { error(429, "rate_limited") }
        val vm = viewModel()
        eventually { vm.state.value is CreateAccountUiState.CreateFailed }
        assertEquals(CreateAccountUiState.CreateFailed(AuthMessages.RATE_LIMITED), vm.state.value)

        backend.createResponse = FakeAuthBackend().createResponse
        vm.retry()

        eventually { vm.state.value is CreateAccountUiState.Ready }
    }

    @Test
    fun `continue needs the saved confirmation`() = runTest {
        val vm = viewModel()
        eventually { vm.state.value is CreateAccountUiState.Ready }

        vm.submit()

        assertFalse(vm.ready().submitting)
        assertEquals(listOf("POST /auth/account-number"), backend.requests)
    }

    @Test
    fun `confirming signs in with the shown number`() = runTest {
        val vm = viewModel()
        eventually { vm.state.value is CreateAccountUiState.Ready }

        vm.setSaved(true)
        vm.submit()

        eventually { sessions().state.value == SessionState.SignedIn }
        assertEquals(
            listOf("POST /auth/account-number", "POST /auth/confirm-registration", "POST /auth/login"),
            backend.requests
        )
        assertEquals("""{"accountNumber":"$NUMBER"}""", backend.bodies[1])
        assertEquals(FakeAuthBackend.ACCESS, store.stored?.accessToken)
    }

    @Test
    fun `login failure after confirmation shows the error and allows a retry`() = runTest {
        backend.loginResponse = { error(409, "device_limit_reached") }
        val vm = viewModel()
        eventually { vm.state.value is CreateAccountUiState.Ready }
        vm.setSaved(true)

        vm.submit()

        eventually { vm.ready().error != null }
        assertEquals(AuthMessages.DEVICE_LIMIT, vm.ready().error)
        assertFalse(vm.ready().submitting)
        assertEquals(NUMBER, vm.ready().accountNumber)
    }

    @Test
    fun `copy hands the number to the secret clipboard`() = runTest {
        val vm = viewModel()
        eventually { vm.state.value is CreateAccountUiState.Ready }

        vm.copy()

        assertEquals(listOf("Account number" to NUMBER), copied)
        assertTrue(vm.ready().copied)
    }

    @Test
    fun `number never goes into saved state`() = runTest {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        eventually { vm.state.value is CreateAccountUiState.Ready }

        saved.keys().forEach { key -> assertFalse(saved.get<Any>(key).toString().contains(NUMBER)) }
        assertEquals(true, saved.get<Boolean>(CreateAccountViewModel.KEY_NUMBER_ISSUED))
    }

    @Test
    fun `restored after process death without the number it does not create a new one`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf(CreateAccountViewModel.KEY_NUMBER_ISSUED to true)))

        assertEquals(CreateAccountUiState.Interrupted, vm.state.value)
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `start over after an interruption creates a new number`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf(CreateAccountViewModel.KEY_NUMBER_ISSUED to true)))

        vm.startOver()

        eventually { vm.state.value is CreateAccountUiState.Ready }
    }

    @Test
    fun `ready state never prints the number`() {
        val state = CreateAccountUiState.Ready(accountNumber = NUMBER)

        assertFalse(state.toString().contains(NUMBER))
        assertFalse(state.toString().contains("4711"))
    }
}
