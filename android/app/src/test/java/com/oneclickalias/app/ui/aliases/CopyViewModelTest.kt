package com.oneclickalias.app.ui.aliases

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oneclickalias.app.data.aliases.ClaimDelivery
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.testing.CombinedBackend
import com.oneclickalias.app.testing.FakeAliasClipboard
import com.oneclickalias.app.testing.FakeAmsBackend
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.FakeWebLinks
import com.oneclickalias.app.testing.InMemoryPendingClaims
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CopyViewModelTest {
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
    private val pending = InMemoryPendingClaims()
    private val scheduled = AtomicInteger()
    private val clipboard = FakeAliasClipboard()
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

    private suspend fun loaded(): CopyViewModel {
        val origin = server.url("/")
        val sessions = SessionManager(AuthApi(transport, origin), store)
        sessions.restore()
        val api = AmsApi(transport, origin, sessions)
        val delivery = ClaimDelivery(api, pending) { scheduled.incrementAndGet() }
        val factory = viewModelFactory {
            initializer {
                CopyViewModel(api, delivery, pending::add, clipboard, links, origin) { sessions.accountStatus() }
            }
        }
        return ViewModelProvider.create(viewModels, factory)[CopyViewModel::class].apply {
            load()
            eventually {
                state.value.list != ListStatus.Loading && !state.value.loading &&
                    state.value.cards.none { it.working }
            }
        }
    }

    @Test
    fun `one card per confirmed recipient, missing ready aliases are fetched`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        ams.addRecipient("r-work", "work@example.com")
        ams.addRecipient("r-new", "new@example.com", verified = false)
        val ready = ams.addReady(home)

        val vm = loaded()

        val cards = vm.state.value.cards
        assertEquals(listOf("r-home", "r-work"), cards.map { it.recipient.id })
        assertEquals(ready, cards[0].alias)
        assertEquals(ams.ready.single { it.recipientId == "r-work" }, cards[1].alias)
        assertEquals(1, ams.requests.toList().count { it == "POST /api/aliases/ready" })
        assertTrue(ams.aliases.isEmpty())
    }

    @Test
    fun `copy puts the alias on the clipboard, claims it and shows the next one`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        val vm = loaded()

        vm.copy("r-home")

        eventually { vm.state.value.cards.single().alias?.id?.let { it != first.id } == true }
        assertEquals(listOf(first.alias), clipboard.copies)
        assertEquals(listOf(first), ams.aliases)
        assertEquals(ams.ready.single(), vm.state.value.cards.single().alias)
        assertNull(vm.state.value.cards.single().copied)
        assertTrue(pending.ids.isEmpty())
        assertEquals(0, scheduled.get())
    }

    @Test
    fun `copy without a connection keeps the alias waiting and schedules the delivery`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        val vm = loaded()
        ams.claimResponse = { FakeAmsBackend.error(503, "auth_unavailable") }

        vm.copy("r-home")

        eventually { vm.state.value.cards.single().let { it.copied != null && !it.working } }
        assertEquals(listOf(first.alias), clipboard.copies)
        assertEquals(setOf(first.id), pending.ids.toSet())
        assertEquals(1, scheduled.get())
        assertNull(vm.state.value.cards.single().alias)

        // Copy again copies the same alias and sends no second claim.
        val claims = ams.requests.toList().count { it.endsWith("/claim") }
        vm.copy("r-home")
        assertEquals(listOf(first.alias, first.alias), clipboard.copies)
        assertEquals(claims, ams.requests.toList().count { it.endsWith("/claim") })
    }

    @Test
    fun `the next load delivers a waiting copy first and shows the next alias`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        pending.ids += first.id

        val vm = loaded()

        assertEquals(listOf(first), ams.aliases)
        assertTrue(pending.ids.isEmpty())
        assertEquals(ams.ready.single(), vm.state.value.cards.single().alias)
    }

    @Test
    fun `a waiting copy that still cannot be delivered shows as copied after a restart`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        val first = ams.addReady(home)
        pending.ids += first.id
        ams.claimResponse = { FakeAmsBackend.error(503, "auth_unavailable") }

        val vm = loaded()

        assertEquals(first, vm.state.value.cards.single().copied)
        assertNull(vm.state.value.cards.single().alias)
    }

    @Test
    fun `a copied alias the server removed meanwhile says so and shows a new one`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        ams.addReady(home)
        val vm = loaded()
        ams.claimResponse = { FakeAmsBackend.error(404, "not_found") }

        vm.copy("r-home")

        eventually { vm.state.value.message == CopyMessage.Removed }
        assertTrue(pending.ids.isEmpty())
        assertNull(vm.state.value.cards.single().copied)
    }

    @Test
    fun `before android 13 the copy gets a short confirmation`() = runTest {
        clipboard.systemConfirms = false
        val home = ams.addRecipient("r-home", "home@example.com")
        ams.addReady(home)
        val vm = loaded()

        vm.copy("r-home")

        eventually { vm.state.value.message == CopyMessage.Copied }
    }

    @Test
    fun `unpaid account shows the billing notice and no cards`() = runTest {
        val home = ams.addRecipient("r-home", "home@example.com")
        ams.addReady(home)
        auth.accountStatusResponse = { FakeAuthBackend.json("""{"paid":false,"paidUntil":null}""") }
        ams.listReadyResponse = { FakeAmsBackend.error(403, "payment_required") }

        val vm = loaded()

        assertTrue(vm.state.value.paymentRequired)
        assertTrue(vm.state.value.cards.isEmpty())
        assertEquals(0, ams.requests.toList().count { it == "POST /api/aliases/ready" })
    }

    @Test
    fun `without a confirmed recipient the notice says whether one waits for confirmation`() = runTest {
        val vm = loaded()
        assertEquals(false, vm.state.value.noRecipient)

        ams.addRecipient("r-new", "new@example.com", verified = false)
        vm.load()
        eventually { vm.state.value.noRecipient == true }
    }

    @Test
    fun `recipients waiting for confirmation are counted next to the cards`() = runTest {
        ams.addRecipient("r-home", "home@example.com")
        ams.addRecipient("r-new", "new@example.com", verified = false)

        val vm = loaded()

        assertEquals(1, vm.state.value.cards.size)
        assertEquals(1, vm.state.value.pendingRecipients)
    }

    @Test
    fun `a failed ready alias shows the error on its card`() = runTest {
        ams.addRecipient("r-home", "home@example.com")
        ams.ensureReadyResponse = { FakeAmsBackend.error(429, "rate_limited") }

        val vm = loaded()

        assertEquals(AliasMessages.RATE_LIMITED, vm.state.value.cards.single().error)
    }
}
