package com.oneclickalias.app.ui.devices

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
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.data.session.SignOutNotice
import com.oneclickalias.app.testing.FakeAuthBackend
import com.oneclickalias.app.testing.FakeAuthBackend.Companion.DEVICE
import com.oneclickalias.app.testing.MainDispatcherRule
import com.oneclickalias.app.testing.eventually
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.auth.Device
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

class DevicesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = MockWebServer()
    private val auth = FakeAuthBackend()
    private val httpClient = createHttpClient()
    private val transport = ApiTransport(httpClient)
    private val store = InMemorySessionStore(
        Session("at", "rt", DEVICE, System.currentTimeMillis() + 900_000, System.currentTimeMillis())
    )
    private val viewModels = ViewModelStore()
    private lateinit var sessions: SessionManager

    private val thisPhone = Device(DEVICE, "brave otter", "2026-11-05T10:00:00Z", current = true)
    private val laptop = Device("dev-laptop", "quiet heron", "2026-11-06T10:00:00Z", current = false)

    @Before
    fun setUp() {
        server.dispatcher = auth
        server.start()
        auth.devices += listOf(thisPhone, laptop)
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

    private suspend fun loaded(): DevicesViewModel {
        val api = AuthApi(transport, server.url("/"))
        sessions = SessionManager(api, store)
        sessions.restore()
        val factory = viewModelFactory { initializer { DevicesViewModel(sessions, api) } }
        return ViewModelProvider.create(viewModels, factory)[DevicesViewModel::class].apply {
            load()
            eventually { state.value.list == ListStatus.Ready && !state.value.loading }
        }
    }

    @Test
    fun `load lists the devices with this one first`() = runTest {
        auth.devices.add(0, auth.devices.removeAt(1)) // server order: laptop, this phone

        val vm = loaded()

        assertEquals(listOf(thisPhone, laptop), vm.state.value.devices)
        assertEquals(listOf("GET /devices"), auth.requests)
    }

    @Test
    fun `a list with this device keeps the session`() = runTest {
        val vm = loaded()

        assertEquals(SessionState.SignedIn, sessions.state.value)
        assertEquals(2, vm.state.value.devices.size)
    }

    @Test
    fun `a list without this device ends the session like a 401`() = runTest {
        // Removed in the dashboard: GET /devices still answers 200 to the access
        // token (valid up to 15 min), but the list no longer marks a current device.
        auth.devices.remove(thisPhone)
        val api = AuthApi(transport, server.url("/"))
        sessions = SessionManager(api, store)
        sessions.restore()
        val vm = ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { DevicesViewModel(sessions, api) } }
        )[DevicesViewModel::class]

        vm.load()

        eventually { sessions.state.value is SessionState.SignedOut }
        assertEquals(SessionState.SignedOut(SignOutNotice.SessionEnded), sessions.state.value)
        assertNull(store.stored)
        // Local clean-up only: the device is gone, a logout call would fail anyway.
        assertEquals(listOf("GET /devices"), auth.requests)
    }

    @Test
    fun `failed load shows the error, pull to refresh retries`() = runTest {
        auth.listDevicesResponse = { FakeAuthBackend.error(500, "internal_error") }
        val api = AuthApi(transport, server.url("/"))
        sessions = SessionManager(api, store)
        sessions.restore()
        val vm = ViewModelProvider.create(
            viewModels,
            viewModelFactory { initializer { DevicesViewModel(sessions, api) } }
        )[DevicesViewModel::class]

        vm.load()
        eventually { vm.state.value.list == ListStatus.Failed }
        assertEquals(AuthMessages.SERVER_ERROR, vm.state.value.listError)

        auth.listDevicesResponse = null
        vm.load(userRefresh = true)
        assertTrue(vm.state.value.refreshing)
        eventually { vm.state.value.list == ListStatus.Ready && !vm.state.value.refreshing }
        assertEquals(2, vm.state.value.devices.size)
    }

    @Test
    fun `sign out another device asks first, then removes it`() = runTest {
        val vm = loaded()

        vm.requestSignOut(laptop.deviceId)
        assertEquals(laptop, vm.state.value.signingOut?.device)
        assertFalse(auth.requests.any { it.startsWith("DELETE") })

        vm.confirmSignOut()

        eventually { vm.state.value.signingOut == null }
        assertEquals("DELETE /devices/dev-laptop", auth.requests.last())
        assertEquals(listOf(thisPhone), vm.state.value.devices)
        assertEquals(DevicesMessage.SignedOut("quiet heron"), vm.state.value.message)
        assertEquals(SessionState.SignedIn, sessions.state.value)
    }

    @Test
    fun `a device already gone counts as signed out`() = runTest {
        auth.deleteResponse = { FakeAuthBackend.error(404, "not_found") }
        val vm = loaded()
        vm.requestSignOut(laptop.deviceId)

        vm.confirmSignOut()

        eventually { vm.state.value.signingOut == null }
        assertEquals(listOf(thisPhone), vm.state.value.devices)
    }

    @Test
    fun `failed sign out keeps the sheet with the error`() = runTest {
        auth.deleteResponse = { FakeAuthBackend.error(500, "internal_error") }
        val vm = loaded()
        vm.requestSignOut(laptop.deviceId)

        vm.confirmSignOut()

        eventually { vm.state.value.signingOut?.error != null }
        assertEquals(AuthMessages.SERVER_ERROR, vm.state.value.signingOut?.error)
        assertFalse(vm.state.value.signingOut!!.working)
        assertEquals(2, vm.state.value.devices.size)
    }

    @Test
    fun `signing out this device is a logout`() = runTest {
        val vm = loaded()
        vm.requestSignOut(DEVICE)

        vm.confirmSignOut()

        eventually { sessions.state.value == SessionState.SignedOut() }
        assertEquals("DELETE /devices/$DEVICE", auth.requests.last())
        assertNull(store.stored)
    }

    @Test
    fun `cancel closes the sheet without a request`() = runTest {
        val vm = loaded()
        vm.requestSignOut(laptop.deviceId)

        vm.dismissSignOut()

        assertNull(vm.state.value.signingOut)
        assertFalse(auth.requests.any { it.startsWith("DELETE") })
    }

    @Test
    fun `state prints no device ids`() = runTest {
        val vm = loaded()
        vm.requestSignOut(laptop.deviceId)

        val text = vm.state.value.toString()

        assertFalse(text, text.contains(DEVICE) || text.contains("dev-laptop"))
    }
}
