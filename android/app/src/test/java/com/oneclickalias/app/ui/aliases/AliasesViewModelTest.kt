package com.oneclickalias.app.ui.aliases

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickalias.app.data.aliases.RecipientDecision
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.data.session.SignOutNotice
import com.oneclickalias.app.testing.CombinedBackend
import com.oneclickalias.app.testing.FakeAliasClipboard
import com.oneclickalias.app.testing.FakeAmsBackend
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.FakeWebLinks
import com.oneclickalias.app.testing.InMemoryRecipientMemory
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.app.ui.screens.AuthMessages
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

class AliasesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val auth = FakeAuthBackend()
    private val ams = FakeAmsBackend()
    private val httpClient = createHttpClient()
    private val transport = ApiTransport(httpClient)
    private val store = InMemorySessionStore(
        Session("at", "rt", DEVICE, System.currentTimeMillis() + 900_000, System.currentTimeMillis())
    )
    private val clipboard = FakeAliasClipboard()
    private val links = FakeWebLinks()
    private lateinit var sessions: SessionManager

    /** Cleared after each test, so no request outlives it (and the test Main dispatcher). */
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        server.dispatcher = CombinedBackend(auth, ams)
        server.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        // Cancelled calls finish on OkHttp threads and resume on Main: let them
        // do that before the rule resets the Main dispatcher.
        val deadline = System.currentTimeMillis() + 5_000
        while (httpClient.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        server.close()
    }

    private suspend fun viewModel(): AliasesViewModel {
        val origin = server.url("/")
        sessions = SessionManager(AuthApi(transport, origin), store)
        sessions.restore()
        val factory = viewModelFactory {
            initializer {
                AliasesViewModel(AmsApi(transport, origin, sessions), clipboard, links, origin) {
                    sessions.accountStatus()
                }
            }
        }
        return ViewModelProvider.create(viewModels, factory)[AliasesViewModel::class]
    }

    private suspend fun loaded(): AliasesViewModel = viewModel().apply {
        load()
        eventually { state.value.list == ListStatus.Ready && !state.value.loading }
    }

    private val home get() = ams.recipients.first { it.id == "r-home" }

    // Loading

    @Test
    fun `load shows the aliases newest first`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val old = ams.addAlias("old-one-1111", home, createdAt = "2026-11-01T10:00:00Z")
        val new = ams.addAlias("new-one-2222", home, createdAt = "2026-11-09T10:00:00Z")

        val vm = loaded()

        assertEquals(listOf(new, old), vm.state.value.aliases)
        assertNull(vm.state.value.listError)
    }

    @Test
    fun `empty account is ready with an empty list`() = runTest {
        val vm = loaded()

        assertEquals(emptyList<Any>(), vm.state.value.aliases)
    }

    @Test
    fun `failed first load shows the error, retry loads`() = runTest {
        ams.listAliasesResponse = { FakeAmsBackend.error(500, "internal_error") }
        val vm = viewModel()

        vm.load()
        eventually { vm.state.value.list == ListStatus.Failed && !vm.state.value.loading }
        assertEquals(AuthMessages.SERVER_ERROR, vm.state.value.listError)

        ams.listAliasesResponse = null
        ams.addAlias("back-again-1234", ams.addRecipient("r-home", "home@example.com"))
        vm.load()

        eventually { vm.state.value.list == ListStatus.Ready }
        assertEquals(1, vm.state.value.aliases.size)
        assertNull(vm.state.value.listError)
    }

    @Test
    fun `failed reload keeps the list and reports the error`() = runTest {
        ams.addAlias("kept-alias-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        server.close()

        vm.load()

        eventually { vm.state.value.listError != null }
        assertEquals(AuthMessages.OFFLINE, vm.state.value.listError)
        assertEquals(ListStatus.Ready, vm.state.value.list)
        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `pull to refresh shows the indicator until the reload is done`() = runTest {
        ams.addRecipient("r-home", "home@example.com")
        val vm = loaded()
        ams.addAlias("fresh-alias-1234", home)

        vm.load(userRefresh = true)

        assertTrue(vm.state.value.refreshing)
        eventually { !vm.state.value.refreshing }
        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `reload of a device signed out elsewhere ends the session with a notice`() = runTest {
        // The AMS introspection no longer knows the device; refresh is refused too.
        ams.validTokens = emptySet()
        auth.refreshResponse = { FakeAuthBackend.error(401, "unauthorized") }
        val vm = viewModel()

        vm.load()

        eventually { sessions.state.value is SessionState.SignedOut }
        assertEquals(SessionState.SignedOut(SignOutNotice.SessionEnded), sessions.state.value)
        assertNull(store.stored)
    }

    // Creating and copying

    @Test
    fun `unpaid account shows the billing notice right after loading, without a write`() = runTest {
        auth.accountStatusResponse = { FakeAuthBackend.json("""{"paid":false,"paidUntil":null}""") }

        val vm = loaded()

        eventually { vm.state.value.paymentRequired }
        assertTrue(ams.writes.isEmpty())
    }

    @Test
    fun `every load asks for the account status, a paid account shows no notice`() = runTest {
        val vm = loaded()
        vm.load()
        eventually { auth.requests.toList().count { it == "GET /auth/account" } == 2 }

        assertFalse(vm.state.value.paymentRequired)
    }

    @Test
    fun `an account paid meanwhile loses the notice on the next load`() = runTest {
        auth.accountStatusResponse = { FakeAuthBackend.json("""{"paid":false,"paidUntil":"2026-10-01T00:00:00Z"}""") }
        val vm = loaded()
        eventually { vm.state.value.paymentRequired }

        auth.accountStatusResponse = { FakeAuthBackend.json("""{"paid":true,"paidUntil":"2026-12-01T00:00:00Z"}""") }
        vm.load()

        eventually { !vm.state.value.paymentRequired }
    }

    @Test
    fun `tapping an alias copies it`() = runTest {
        val alias = ams.addAlias("tap-me-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()

        vm.copy(alias)

        assertEquals(listOf(alias.alias), clipboard.copies)
    }

    // Actions per alias

    @Test
    fun `tracker switch patches the alias and shows the saved value`() = runTest {
        val alias = ams.addAlias("track-me-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        vm.openActions(alias.id)

        vm.setBlockTrackers(false)

        eventually { vm.state.value.actions?.saving == false && vm.state.value.actions?.alias?.blockTrackers == false }
        assertEquals("""{"block_trackers":false}""", ams.bodies.last { it.contains("block_trackers") })
        assertFalse(vm.state.value.aliases.single().blockTrackers)
        assertFalse(ams.aliases.single().blockTrackers)
    }

    @Test
    fun `tracker switch without payment closes the sheet and shows the billing notice`() = runTest {
        val alias = ams.addAlias("track-me-1234", ams.addRecipient("r-home", "home@example.com"))
        ams.patchResponse = { FakeAmsBackend.error(403, "payment_required") }
        val vm = loaded()
        vm.openActions(alias.id)

        vm.setBlockTrackers(false)

        eventually { vm.state.value.paymentRequired }
        assertNull(vm.state.value.actions)
        assertTrue(vm.state.value.aliases.single().blockTrackers)
    }

    @Test
    fun `tracker switch rate limited shows the error in the sheet`() = runTest {
        val alias = ams.addAlias("track-me-1234", ams.addRecipient("r-home", "home@example.com"))
        ams.patchResponse = { FakeAmsBackend.error(429, "rate_limited") }
        val vm = loaded()
        vm.openActions(alias.id)

        vm.setBlockTrackers(false)

        eventually { vm.state.value.actions?.error != null }
        assertEquals(AliasMessages.RATE_LIMITED, vm.state.value.actions?.error)
        assertTrue(vm.state.value.actions!!.alias.blockTrackers)
    }

    @Test
    fun `delete asks first, then removes the alias`() = runTest {
        val alias = ams.addAlias("delete-me-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        vm.openActions(alias.id)

        vm.requestDelete()
        assertTrue(vm.state.value.actions!!.confirmingDelete)
        assertTrue(ams.writes.isEmpty())

        vm.confirmDelete()

        eventually { vm.state.value.actions == null }
        assertEquals(listOf("DELETE /api/aliases/${alias.id}"), ams.writes)
        assertTrue(vm.state.value.aliases.isEmpty())
        assertEquals(AliasesMessage.Deleted(alias.alias), vm.state.value.message)
    }

    @Test
    fun `cancel keeps the alias`() = runTest {
        val alias = ams.addAlias("keep-me-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        vm.openActions(alias.id)
        vm.requestDelete()

        vm.cancelDelete()

        assertFalse(vm.state.value.actions!!.confirmingDelete)
        assertTrue(ams.writes.isEmpty())
    }

    @Test
    fun `deleting an alias that is already gone removes it too`() = runTest {
        val alias = ams.addAlias("gone-already-1234", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        ams.aliases.clear()
        vm.openActions(alias.id)
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.actions == null }
        assertTrue(vm.state.value.aliases.isEmpty())
    }

    @Test
    fun `failed delete keeps the alias and shows the error in the sheet`() = runTest {
        val alias = ams.addAlias("stay-1234", ams.addRecipient("r-home", "home@example.com"))
        ams.deleteResponse = { FakeAmsBackend.error(503, "auth_service_unavailable") }
        val vm = loaded()
        vm.openActions(alias.id)
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.actions?.error != null }
        assertEquals(AuthMessages.UNAVAILABLE, vm.state.value.actions?.error)
        assertFalse(vm.state.value.actions!!.deleting)
        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `delete without payment shows the billing notice`() = runTest {
        val alias = ams.addAlias("stay-1234", ams.addRecipient("r-home", "home@example.com"))
        ams.deleteResponse = { FakeAmsBackend.error(403, "payment_required") }
        val vm = loaded()
        vm.openActions(alias.id)
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.paymentRequired }
        assertNull(vm.state.value.actions)
    }

    // Website links

    @Test
    fun `billing opens the website's billing page in the browser`() = runTest {
        val vm = viewModel()

        vm.openBilling()

        assertEquals(listOf(server.url("/dashboard/billing").toString()), links.opened)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `no browser installed says where to go instead`() = runTest {
        links.browserInstalled = false
        val vm = viewModel()

        vm.openBilling()

        val url = server.url("/dashboard/billing").toString()
        assertEquals(AliasesMessage.Error(AliasMessages.noBrowser(url)), vm.state.value.message)
    }

    // Groups

    @Test
    fun `groups start collapsed and a toggle opens and closes one`() = runTest {
        ams.addAlias("home-alias-1111", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()

        assertEquals(emptySet<String>(), vm.state.value.expandedGroups)
        vm.toggleGroup("home@example.com")
        assertEquals(setOf("home@example.com"), vm.state.value.expandedGroups)
        vm.toggleGroup("home@example.com")
        assertEquals(emptySet<String>(), vm.state.value.expandedGroups)
    }

    @Test
    fun `an open group stays open across a reload`() = runTest {
        ams.addAlias("home-alias-1111", ams.addRecipient("r-home", "home@example.com"))
        val vm = loaded()
        vm.toggleGroup("home@example.com")

        vm.load()
        eventually { !vm.state.value.loading }

        assertEquals(setOf("home@example.com"), vm.state.value.expandedGroups)
    }
}
