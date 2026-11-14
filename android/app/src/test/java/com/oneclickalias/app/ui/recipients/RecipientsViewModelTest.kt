package com.oneclickalias.app.ui.recipients

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.testing.CombinedBackend
import com.oneclickalias.app.testing.FakeAmsBackend
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.FakeWebLinks
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.aliases.ListStatus
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

class RecipientsViewModelTest {
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
    private val links = FakeWebLinks()
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        server.dispatcher = CombinedBackend(auth, ams)
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

    private suspend fun viewModel(startWithAdd: Boolean = false): RecipientsViewModel {
        val origin = server.url("/")
        val sessions = SessionManager(AuthApi(transport, origin), store)
        sessions.restore()
        val factory = viewModelFactory {
            initializer {
                RecipientsViewModel(AmsApi(transport, origin, sessions), links, origin, startWithAdd) {
                    sessions.accountStatus()
                }
            }
        }
        return ViewModelProvider.create(viewModels, factory)[RecipientsViewModel::class]
    }

    private suspend fun loaded(): RecipientsViewModel = viewModel().apply {
        load()
        eventually { state.value.list == ListStatus.Ready && !state.value.loading }
    }

    @Test
    fun `load shows confirmed and waiting addresses in server order`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val work = ams.addRecipient("r-work", "work@example.com", verified = false)

        val vm = loaded()

        assertEquals(listOf(home, work), vm.state.value.recipients)
        assertNull(vm.state.value.listError)
    }

    @Test
    fun `failed first load shows the error, a failed reload keeps the list`() = runTest {
        ams.listRecipientsResponse = { FakeAmsBackend.error(500, "internal_error") }
        val vm = viewModel()
        vm.load()
        eventually { vm.state.value.list == ListStatus.Failed }
        assertEquals(AuthMessages.SERVER_ERROR, vm.state.value.listError)

        ams.listRecipientsResponse = null
        ams.addRecipient("r-home", "home@example.com")
        vm.load()
        eventually { vm.state.value.list == ListStatus.Ready && !vm.state.value.loading }

        ams.listRecipientsResponse = { FakeAmsBackend.error(503, "auth_service_unavailable") }
        vm.load()
        eventually { vm.state.value.listError != null }
        assertEquals(ListStatus.Ready, vm.state.value.list)
        assertEquals(1, vm.state.value.recipients.size)
        assertEquals(AuthMessages.UNAVAILABLE, vm.state.value.listError)
    }

    @Test
    fun `pull to refresh shows the indicator until the load is done`() = runTest {
        val vm = loaded()

        vm.load(userRefresh = true)

        assertTrue(vm.state.value.refreshing)
        eventually { !vm.state.value.refreshing }
    }

    @Test
    fun `opened from the alias screen starts with the add sheet`() = runTest {
        val vm = viewModel(startWithAdd = true)

        assertEquals(AddRecipient(), vm.state.value.adding)
    }

    @Test
    fun `add sends the address, lists it as waiting and says a confirmation email is on its way`() = runTest {
        val vm = loaded()
        vm.openAdd()
        vm.setEmail("  new@example.com ")

        vm.submitAdd()

        eventually { vm.state.value.adding == null }
        assertEquals("""{"email":"new@example.com"}""", ams.bodies.last())
        val added = vm.state.value.recipients.single()
        assertEquals("new@example.com", added.email)
        assertFalse(added.verified)
        assertEquals(RecipientsMessage.ConfirmationSent("new@example.com"), vm.state.value.message)
    }

    @Test
    fun `add without an address does nothing`() = runTest {
        val vm = loaded()
        vm.openAdd()
        vm.setEmail("   ")

        vm.submitAdd()

        assertFalse(vm.state.value.adding!!.canSubmit)
        assertEquals(emptyList<String>(), ams.writes)
    }

    @Test
    fun `add errors stay in the sheet`() = runTest {
        ams.addRecipient("r-home", "home@example.com")
        val vm = loaded()
        vm.openAdd()
        vm.setEmail("home@example.com")

        vm.submitAdd()
        eventually { vm.state.value.adding?.error != null }
        assertEquals(RecipientMessages.ALREADY_ADDED, vm.state.value.adding?.error)
        assertFalse(vm.state.value.adding!!.submitting)

        vm.setEmail("no-at-sign")
        assertNull(vm.state.value.adding?.error)
        vm.submitAdd()
        eventually { vm.state.value.adding?.error != null }
        assertEquals(RecipientMessages.INVALID_EMAIL, vm.state.value.adding?.error)
    }

    @Test
    fun `unpaid account shows the notice right after loading, without a write`() = runTest {
        auth.accountStatusResponse = { FakeAuthBackend.json("""{"paid":false,"paidUntil":null}""") }

        val vm = loaded()

        eventually { vm.state.value.paymentRequired }
        assertTrue(ams.writes.isEmpty())
    }

    @Test
    fun `without an account status the notice waits for a refused write`() = runTest {
        auth.accountStatusResponse = { FakeAuthBackend.error(404, "not_found") }

        val vm = loaded()
        eventually { auth.requests.contains("GET /auth/account") }

        assertFalse(vm.state.value.paymentRequired)
    }

    @Test
    fun `payment required closes the sheet and shows the notice until the next load`() = runTest {
        ams.createRecipientResponse = { FakeAmsBackend.error(403, "payment_required") }
        val vm = loaded()
        vm.openAdd()
        vm.setEmail("new@example.com")

        vm.submitAdd()

        eventually { vm.state.value.paymentRequired }
        assertNull(vm.state.value.adding)

        vm.load()
        eventually { !vm.state.value.paymentRequired }
    }

    @Test
    fun `open billing opens the website's billing page`() = runTest {
        val vm = loaded()

        vm.openBilling()

        assertEquals(listOf(server.url("/dashboard/billing").toString()), links.opened)
    }

    @Test
    fun `resend asks for a new link and says so`() = runTest {
        ams.addRecipient("r-new", "new@example.com", verified = false)
        val vm = loaded()
        vm.openActions("r-new")

        vm.resend()

        eventually { vm.state.value.actions == null }
        assertEquals("POST /api/recipients/r-new/resend", ams.writes.single())
        assertEquals(RecipientsMessage.ConfirmationSent("new@example.com"), vm.state.value.message)
    }

    @Test
    fun `resend of an address confirmed meanwhile reloads the list`() = runTest {
        ams.addRecipient("r-new", "new@example.com", verified = false)
        val vm = loaded()
        ams.recipients[0] = ams.recipients[0].copy(verified = true)
        vm.openActions("r-new")

        vm.resend()

        eventually { vm.state.value.recipients.single().verified }
        assertNull(vm.state.value.actions)
        assertEquals(RecipientsMessage.Error(RecipientMessages.ALREADY_CONFIRMED), vm.state.value.message)
    }

    @Test
    fun `resend refused for payment shows the notice`() = runTest {
        ams.addRecipient("r-new", "new@example.com", verified = false)
        ams.resendResponse = { FakeAmsBackend.error(403, "payment_required") }
        val vm = loaded()
        vm.openActions("r-new")

        vm.resend()

        eventually { vm.state.value.paymentRequired }
        assertNull(vm.state.value.actions)
    }

    @Test
    fun `delete asks first, then removes the address`() = runTest {
        ams.addRecipient("r-old", "old@example.com")
        val vm = loaded()
        vm.openActions("r-old")

        vm.requestDelete()
        assertTrue(vm.state.value.actions!!.confirmingDelete)
        assertEquals(emptyList<String>(), ams.writes)

        vm.confirmDelete()

        eventually { vm.state.value.actions == null }
        assertEquals("DELETE /api/recipients/r-old", ams.writes.single())
        assertEquals(emptyList<Any>(), vm.state.value.recipients)
        assertEquals(RecipientsMessage.Deleted("old@example.com"), vm.state.value.message)
    }

    @Test
    fun `delete of an address with active aliases explains why in the sheet`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        ams.addAlias("quiet-otter-1234", home)
        val vm = loaded()
        vm.openActions("r-home")
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.actions?.error != null }
        assertEquals(RecipientMessages.IN_USE, vm.state.value.actions?.error)
        assertFalse(vm.state.value.actions!!.working)
        assertEquals(1, vm.state.value.recipients.size)
    }

    @Test
    fun `delete of an address already gone counts as deleted`() = runTest {
        ams.addRecipient("r-old", "old@example.com")
        val vm = loaded()
        ams.recipients.clear()
        vm.openActions("r-old")
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.actions == null }
        assertEquals(emptyList<Any>(), vm.state.value.recipients)
    }

    @Test
    fun `delete refused for payment shows the notice`() = runTest {
        ams.addRecipient("r-old", "old@example.com")
        ams.deleteRecipientResponse = { FakeAmsBackend.error(403, "payment_required") }
        val vm = loaded()
        vm.openActions("r-old")
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.paymentRequired }
        assertNull(vm.state.value.actions)
    }

    @Test
    fun `rate limit while writing shows the write limit text`() = runTest {
        ams.addRecipient("r-old", "old@example.com")
        ams.deleteRecipientResponse = { FakeAmsBackend.error(429, "rate_limited") }
        val vm = loaded()
        vm.openActions("r-old")
        vm.requestDelete()

        vm.confirmDelete()

        eventually { vm.state.value.actions?.error != null }
        assertEquals(AliasMessages.RATE_LIMITED, vm.state.value.actions?.error)
    }

    @Test
    fun `no connection while adding says so`() = runTest {
        val vm = loaded()
        vm.openAdd()
        vm.setEmail("new@example.com")
        server.close()

        vm.submitAdd()

        eventually { vm.state.value.adding?.error != null }
        assertEquals(AuthMessages.OFFLINE, vm.state.value.adding?.error)
    }
}
