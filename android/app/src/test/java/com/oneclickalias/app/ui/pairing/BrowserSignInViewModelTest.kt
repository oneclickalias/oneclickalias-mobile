package com.oneclickalias.app.ui.pairing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.BROWSER_NAME
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.BROWSER_PAIRING
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BrowserSignInViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val httpClient = createHttpClient()
    private val store = InMemorySessionStore(
        Session("at", "rt", DEVICE, System.currentTimeMillis() + 900_000, System.currentTimeMillis())
    )
    private val viewModels = ViewModelStore()
    private var identityConfirmed = true
    private var prompts = 0
    private val leaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val code = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        val deadline = System.currentTimeMillis() + 5_000
        while (httpClient.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        server.close()
    }

    private suspend fun viewModel(): BrowserSignInViewModel {
        val origin = server.url("/")
        val auth = AuthApi(ApiTransport(httpClient), origin)
        val sessions = SessionManager(auth, store)
        sessions.restore()
        val factory = viewModelFactory {
            initializer {
                BrowserSignInViewModel(
                    auth,
                    sessions,
                    confirmIdentity = {
                        prompts++
                        identityConfirmed
                    },
                    leaveScope = leaveScope
                )
            }
        }
        return ViewModelProvider.create(viewModels, factory)[BrowserSignInViewModel::class]
    }

    private suspend fun scanned(): BrowserSignInViewModel = viewModel().apply {
        onScanned("OCA-LOGIN-1-$code")
        eventually { state.value is BrowserSignInUiState.Confirm }
    }

    @Test
    fun `a browser code is claimed and shows the browser's name`() = runTest {
        val vm = scanned()

        assertEquals(BrowserSignInUiState.Confirm(BROWSER_PAIRING, BROWSER_NAME), vm.state.value)
        assertEquals(
            """{"code":"$code"}""",
            backend.bodies[backend.requests.indexOf("POST /auth/pairing/login-request/claim")]
        )
    }

    @Test
    fun `approve signs the browser in`() = runTest {
        val vm = scanned()

        vm.approve()

        eventually { vm.state.value == BrowserSignInUiState.Done(BROWSER_NAME) }
        assertTrue("POST /auth/pairing/$BROWSER_PAIRING/approve" in backend.requests)
        assertEquals(1, prompts)
    }

    @Test
    fun `a cancelled prompt approves nothing`() = runTest {
        identityConfirmed = false
        val vm = scanned()

        vm.approve()

        eventually { (vm.state.value as? BrowserSignInUiState.Confirm)?.working == false && prompts == 1 }
        assertTrue(backend.requests.none { it.endsWith("/approve") })
    }

    @Test
    fun `deny ends the request`() = runTest {
        val vm = scanned()

        vm.deny()

        eventually { vm.state.value == BrowserSignInUiState.Denied }
        assertTrue("POST /auth/pairing/$BROWSER_PAIRING/deny" in backend.requests)
    }

    @Test
    fun `leaving before deciding denies the request`() = runTest {
        scanned()

        viewModels.clear()

        eventually { "POST /auth/pairing/$BROWSER_PAIRING/deny" in backend.requests }
    }

    @Test
    fun `codes that are not a browser code are explained and claim nothing`() = runTest {
        val vm = viewModel()

        vm.onScanned("OCA-PAIR-1-$code")
        assertEquals(BrowserSignInUiState.Scanning(BrowserSignInMessages.APP_CODE), vm.state.value)
        vm.onScanned("OCA-LOGIN-2-$code")
        assertEquals(BrowserSignInUiState.Scanning(BrowserSignInMessages.UPDATE_APP), vm.state.value)
        vm.onScanned("https://example.com")
        assertEquals(BrowserSignInUiState.Scanning(BrowserSignInMessages.NOT_OURS), vm.state.value)
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `an expired code and a full device list have their own texts`() = runTest {
        backend.loginRequestClaimResponse = { FakeAuthBackend.error(400, "pairing_expired") }
        val vm = viewModel()
        vm.onScanned("OCA-LOGIN-1-$code")
        eventually { vm.state.value == BrowserSignInUiState.Failed(BrowserSignInMessages.EXPIRED) }

        backend.loginRequestClaimResponse = FakeAuthBackend().loginRequestClaimResponse
        backend.approveResponse = { FakeAuthBackend.error(409, "device_limit_reached") }
        vm.scanAgain()
        vm.onScanned("OCA-LOGIN-1-$code")
        eventually { vm.state.value is BrowserSignInUiState.Confirm }
        vm.approve()
        eventually { vm.state.value == BrowserSignInUiState.Failed(BrowserSignInMessages.DEVICE_LIMIT) }
    }
}
