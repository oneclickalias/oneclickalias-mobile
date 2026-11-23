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
import com.oneclickalias.app.testing.FakeWebLinks
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.contracts.auth.AccountStatus
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val backend = FakeAuthBackend()
    private val links = FakeWebLinks()
    private val store = InMemorySessionStore(
        Session("at", "rt", DEVICE, System.currentTimeMillis() + 900_000, System.currentTimeMillis())
    )

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `logout removes the device and the local session`() = runTest {
        val sessions =
            SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store)
        sessions.restore()
        val vm = AccountViewModel(sessions, links, server.url("/"))

        vm.logout()

        assertTrue(vm.state.value.loggingOut)
        eventually { sessions.state.value is SessionState.SignedOut }
        assertEquals(listOf("DELETE /devices/$DEVICE"), backend.requests)
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(), sessions.state.value)
    }

    @Test
    fun `offline logout still signs out locally with a notice`() = runTest {
        val url = server.url("/")
        server.close()
        val sessions = SessionManager(AuthApi(ApiTransport(createHttpClient()), url), store)
        sessions.restore()

        AccountViewModel(sessions, links, url).logout()

        eventually { sessions.state.value is SessionState.SignedOut }
        assertEquals(SessionState.SignedOut(SignOutNotice.SignedOutLocally), sessions.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `billing opens the website's billing page in the browser`() = runTest {
        val sessions = SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store)
        val vm = AccountViewModel(sessions, links, server.url("/"))

        vm.openBilling()

        assertEquals(listOf(server.url("/dashboard/billing").toString()), links.opened)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `no browser installed says where to go instead`() = runTest {
        links.browserInstalled = false
        val sessions = SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store)
        val vm = AccountViewModel(sessions, links, server.url("/"))

        vm.openBilling()

        val url = server.url("/dashboard/billing").toString()
        assertEquals("No browser app found — open $url in a browser.", vm.state.value.message)
    }

    private suspend fun statusViewModel(): AccountViewModel {
        val sessions = SessionManager(AuthApi(ApiTransport(createHttpClient()), server.url("/")), store)
        sessions.restore()
        return AccountViewModel(sessions, links, server.url("/")) { sessions.accountStatus() }
    }

    @Test
    fun `load shows until when the account is paid`() = runTest {
        val vm = statusViewModel()

        vm.load()

        eventually { vm.state.value.paidState != null }
        assertTrue(vm.state.value.paidState is PaidState.Active)
    }

    @Test
    fun `an unreadable status shows no status at all`() = runTest {
        backend.accountStatusResponse = { FakeAuthBackend.error(500, "internal_error") }
        val vm = statusViewModel()

        vm.load()

        eventually { vm.state.value.statusLoaded }
        assertNull(vm.state.value.paidState)
    }

    @Test
    fun `paid, expired and never paid read like the website`() {
        val utc = ZoneId.of("UTC")

        assertEquals(PaidState.Active("Dec 10, 2026"), paidState(AccountStatus(true, "2026-12-10T10:00:00Z"), utc))
        assertEquals(PaidState.Expired("Nov 1, 2026"), paidState(AccountStatus(false, "2026-11-01T10:00:00Z"), utc))
        assertEquals(PaidState.NotActive, paidState(AccountStatus(false, null), utc))
    }
}
