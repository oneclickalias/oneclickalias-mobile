package com.oneclickalias.app.ui.pairing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRED_ACCESS
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRED_DEVICE
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRED_NAME
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRED_REFRESH
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PAIRING_CODE
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.PICKUP
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.error
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.app.ui.screens.AuthMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PairingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val httpClient = createHttpClient()
    private val store = InMemorySessionStore()
    private lateinit var sessions: SessionManager
    private val viewModels = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()

    private val qr = "OCA-PAIR-1-$PAIRING_CODE"

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        // A cancelled poll still resumes on Main once, from its sleep on Default or
        // from an OkHttp thread. Wait for that here: after the rule has reset Main it
        // would throw there and fail whichever test runs next.
        runBlocking { withTimeoutOrNull(5_000) { viewModelJobs.joinAll() } }
        val deadline = System.currentTimeMillis() + 5_000
        while (httpClient.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        server.close()
    }

    private suspend fun viewModel(): PairingViewModel {
        val api = AuthApi(ApiTransport(httpClient), server.url("/"))
        sessions = SessionManager(api, store)
        sessions.restore()
        // Real 20 ms instead of the two seconds of the contract.
        val factory = viewModelFactory {
            initializer { PairingViewModel(api, sessions, sleep = { withContext(Dispatchers.Default) { delay(20) } }) }
        }
        return ViewModelProvider.create(viewModels, factory)[PairingViewModel::class]
            .also { viewModelJobs += it.viewModelScope.coroutineContext.job }
    }

    private fun pickUps() = backend.requests.count { it == "POST /auth/pairing/token" }

    private fun pending() = error(400, "pairing_pending")

    @Test
    fun `starts scanning`() = runTest {
        assertEquals(PairingUiState.Scanning(), viewModel().state.value)
    }

    @Test
    fun `text that is not ours keeps scanning with a hint and sends nothing`() = runTest {
        val vm = viewModel()

        vm.onScanned("https://example.com/")

        assertEquals(PairingUiState.Scanning(problem = PairingMessages.NOT_OURS), vm.state.value)
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `another version asks for an update`() = runTest {
        val vm = viewModel()

        vm.onScanned("OCA-PAIR-2-$PAIRING_CODE")

        assertEquals(PairingUiState.Scanning(problem = PairingMessages.UPDATE_APP), vm.state.value)
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `valid code claims, shows the name, and signs in after the approval`() = runTest {
        backend.pairingTokenResponses.add(0) { pending() }
        backend.pairingTokenResponses.add(0) { pending() }
        val vm = viewModel()

        vm.onScanned(qr)

        eventually { vm.state.value == PairingUiState.Waiting(PAIRED_NAME) }
        eventually { sessions.state.value == SessionState.SignedIn }
        assertEquals("POST /auth/pairing/claim", backend.requests.first())
        assertEquals("""{"code":"$PAIRING_CODE"}""", backend.bodies.first())
        assertEquals(3, pickUps())
        assertTrue(backend.bodies.drop(1).all { it == """{"pickupSecret":"$PICKUP"}""" })
        val stored = store.stored!!
        assertEquals(
            Session(
                PAIRED_ACCESS,
                PAIRED_REFRESH,
                PAIRED_DEVICE,
                stored.obtainedAtMillis + 900_000,
                stored.obtainedAtMillis
            ),
            stored
        )
    }

    @Test
    fun `only the first valid scan is claimed`() = runTest {
        backend.pairingTokenResponses[0] = { pending() }
        val vm = viewModel()

        vm.onScanned(qr)
        vm.onScanned(qr)
        vm.onScanned("nonsense")

        eventually { vm.state.value is PairingUiState.Waiting }
        assertEquals(1, backend.requests.count { it == "POST /auth/pairing/claim" })
    }

    @Test
    fun `claim errors end with their texts`() = runTest {
        val cases = mapOf(
            error(400, "pairing_expired") to PairingMessages.EXPIRED,
            error(400, "pairing_used") to PairingMessages.USED,
            error(400, "pairing_denied") to PairingMessages.DENIED,
            error(400, "invalid_request") to PairingMessages.FAILED,
            error(429, "rate_limited") to AuthMessages.RATE_LIMITED,
            error(500, "internal_error") to AuthMessages.SERVER_ERROR,
            MockResponse.Builder().code(200).body("{}").build() to PairingMessages.FAILED
        )
        for ((response, text) in cases) {
            backend.claimResponse = { response }
            val vm = viewModel()
            vm.onScanned(qr)
            eventually("expected: $text") { vm.state.value == PairingUiState.Failed(text) }
            viewModels.clear()
        }
        assertEquals(0, pickUps())
        assertNull(store.stored)
    }

    @Test
    fun `final pickup answers end with their texts and store nothing`() = runTest {
        val cases = mapOf(
            error(400, "pairing_denied") to PairingMessages.DENIED,
            error(400, "pairing_expired") to PairingMessages.EXPIRED,
            error(400, "pairing_used") to PairingMessages.USED,
            error(401, "unauthorized") to PairingMessages.ENDED
        )
        for ((response, text) in cases) {
            backend.pairingTokenResponses[0] = { response }
            val vm = viewModel()
            vm.onScanned(qr)
            eventually("expected: $text") { vm.state.value == PairingUiState.Failed(text) }
            viewModels.clear()
        }
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(), sessions.state.value)
    }

    @Test
    fun `scan again starts over`() = runTest {
        backend.claimResponse = { error(400, "pairing_expired") }
        val vm = viewModel()
        vm.onScanned(qr)
        eventually { vm.state.value is PairingUiState.Failed }

        vm.scanAgain()

        assertEquals(PairingUiState.Scanning(), vm.state.value)
    }

    @Test
    fun `leaving the screen stops polling`() = runTest {
        backend.pairingTokenResponses[0] = { pending() }
        val vm = viewModel()
        vm.onScanned(qr)
        eventually { pickUps() >= 2 }

        viewModels.clear()
        val afterLeaving = pickUps()
        withContext(Dispatchers.Default) { delay(200) }

        assertTrue(pickUps() <= afterLeaving + 1)
        assertFalse(sessions.state.value == SessionState.SignedIn)
    }

    @Test
    fun `states print nothing secret`() {
        for (state in listOf(
            PairingUiState.Scanning(),
            PairingUiState.Claiming,
            PairingUiState.Waiting(PAIRED_NAME),
            PairingUiState.Failed(PairingMessages.USED)
        )) {
            assertFalse(state.toString().contains(PICKUP))
            assertFalse(state.toString().contains(PAIRING_CODE))
        }
    }
}
