package com.oneclickalias.app.data.session

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.ApiTransport
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.bearer
import com.oneclickalias.app.data.api.createHttpClient
import com.oneclickalias.contracts.auth.AccountStatus
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {
    private val server = MockWebServer()
    private val auth = FakeAuthServer()
    private val transport = ApiTransport(createHttpClient())
    private val store = InMemorySessionStore()
    private var clock = 1_000_000_000L

    @Before
    fun setUp() {
        server.dispatcher = auth
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun CoroutineScope.manager(api: AuthApi = AuthApi(transport, server.url("/"))) =
        SessionManager(api, store, now = { clock })

    private fun session(accessToken: String = "at-0", expiresInMillis: Long = 900_000, obtainedAgoMillis: Long = 0) =
        Session(accessToken, "rt-0", DEVICE, clock + expiresInMillis, clock - obtainedAgoMillis)

    /** A protected request the way the AMS client will make them. */
    private suspend fun ping(token: String): ApiResult<Unit> =
        transport.callNoContent(Request.Builder().url(server.url("/api/ping")).bearer(token).build())

    private fun TestScope.signedInManager(session: Session): SessionManager {
        store.stored = session
        auth.validAccessTokens += session.accessToken
        return backgroundScope.manager()
    }

    @Test
    fun `restore without stored session is signed out`() = runTest {
        val manager = backgroundScope.manager()

        manager.restore()

        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `restore with stored session is signed in`() = runTest {
        val manager = signedInManager(session())

        manager.restore()

        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `sign in stores the token pair and signs in`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.signIn(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(SessionState.SignedIn, manager.state.value)
        assertEquals(Session("login-at", "login-rt", DEVICE, clock + 900_000, clock), store.stored)
    }

    @Test
    fun `failed sign in stays signed out and stores nothing`() = runTest {
        auth.loginResponse = { error(409, "device_limit_reached") }
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.signIn(NUMBER)

        assertEquals(ApiResult.HttpError(409, "device_limit_reached"), result)
        assertEquals(SessionState.SignedOut(), manager.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `confirm and sign in confirms first`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        assertEquals(ApiResult.Success(Unit), manager.confirmAndSignIn(NUMBER))

        assertEquals(listOf("/auth/confirm-registration", "/auth/login"), auth.paths)
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `failed confirmation does not log in`() = runTest {
        auth.confirmResponse = { error(401, "unauthorized") }
        val manager = backgroundScope.manager()
        manager.restore()

        assertEquals(ApiResult.HttpError(401, "unauthorized"), manager.confirmAndSignIn(NUMBER))

        assertEquals(listOf("/auth/confirm-registration"), auth.paths)
        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `authorized call without session is unauthorized and sends nothing`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.authorized { ping(it) }

        assertEquals(ApiResult.HttpError(401, "unauthorized"), result)
        assertTrue(auth.paths.isEmpty())
    }

    @Test
    fun `valid token is used without refresh`() = runTest {
        val manager = signedInManager(session())
        manager.restore()

        assertEquals(ApiResult.Success(Unit), manager.authorized { ping(it) })

        assertEquals(0, auth.refreshes.get())
    }

    @Test
    fun `token close to expiry is refreshed before the call`() = runTest {
        val manager = signedInManager(session(expiresInMillis = 30_000))
        manager.restore()

        assertEquals(ApiResult.Success(Unit), manager.authorized { ping(it) })

        assertEquals(listOf("/auth/refresh", "/api/ping"), auth.paths)
        assertEquals("Bearer at-1", auth.bearers.last())
        assertEquals(Session("at-1", "rt-1", DEVICE, clock + 900_000, clock), store.stored)
    }

    @Test
    fun `401 triggers one refresh and one retry`() = runTest {
        val manager = signedInManager(session())
        auth.validAccessTokens.clear() // server-side the token died early
        manager.restore()

        assertEquals(ApiResult.Success(Unit), manager.authorized { ping(it) })

        assertEquals(listOf("/api/ping", "/auth/refresh", "/api/ping"), auth.paths)
    }

    @Test
    fun `parallel calls with an expired token share one refresh`() = runTest {
        auth.refreshDelayMillis = 300
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()

        val results = (1..5).map { async { manager.authorized { ping(it) } } }.awaitAll()

        assertEquals(List(5) { ApiResult.Success(Unit) }, results)
        assertEquals(1, auth.refreshes.get())
    }

    @Test
    fun `parallel 401s share one refresh`() = runTest {
        auth.refreshDelayMillis = 300
        val manager = signedInManager(session())
        auth.validAccessTokens.clear()
        manager.restore()

        val results = (1..5).map { async { manager.authorized { ping(it) } } }.awaitAll()

        assertEquals(List(5) { ApiResult.Success(Unit) }, results)
        assertEquals(1, auth.refreshes.get())
    }

    @Test
    fun `waiting calls only continue once the new pair is stored`() = runTest {
        auth.refreshDelayMillis = 200
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()

        (1..3).map {
            async {
                manager.authorized { token ->
                    assertEquals(token, store.stored?.accessToken)
                    ping(token)
                }
            }
        }.awaitAll()

        assertEquals(1, store.writes)
    }

    @Test
    fun `rejected refresh ends the session`() = runTest {
        auth.refreshResponse = { error(401, "unauthorized") } // expired, reused or device removed
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()

        val result = manager.authorized { ping(it) }

        assertEquals(ApiResult.HttpError(401, "unauthorized"), result)
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(SignOutNotice.SessionEnded), manager.state.value)
    }

    @Test
    fun `network error during refresh keeps the session`() = runTest {
        store.stored = session(expiresInMillis = -1)
        val url = server.url("/")
        server.close()
        val manager = backgroundScope.manager(AuthApi(transport, url))
        manager.restore()

        val result = manager.authorized { ping(it) }

        assertEquals(ApiResult.NetworkError(NetworkErrorKind.Unreachable), result)
        assertEquals(session(expiresInMillis = -1), store.stored)
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `server error during refresh keeps the session`() = runTest {
        auth.refreshResponse = { error(500, "internal_error") }
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()

        assertEquals(ApiResult.HttpError(500, "internal_error"), manager.authorized { ping(it) })

        assertEquals(SessionState.SignedIn, manager.state.value)
        assertEquals("at-0", store.stored?.accessToken)
    }

    @Test
    fun `logout deletes this device on the server and locally`() = runTest {
        val manager = signedInManager(session())
        manager.restore()

        manager.logout()

        assertEquals(listOf("DELETE /devices/$DEVICE"), auth.requests.filter { it.startsWith("DELETE") })
        assertEquals("Bearer at-0", auth.bearers.last())
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `logout of an already removed device is a normal logout`() = runTest {
        auth.deleteResponse = { error(404, "not_found") }
        val manager = signedInManager(session())
        manager.restore()

        manager.logout()

        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `logout without server still clears local data and says so`() = runTest {
        store.stored = session()
        val url = server.url("/")
        server.close()
        val manager = backgroundScope.manager(AuthApi(transport, url))
        manager.restore()

        manager.logout()

        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(SignOutNotice.SignedOutLocally), manager.state.value)
    }

    @Test
    fun `restore never refreshes, even an older session`() = runTest {
        // A device signed out elsewhere is noticed by the next API call (AMS
        // introspection checks the device), not by a refresh at every start.
        val manager = signedInManager(session(obtainedAgoMillis = 120_000))

        manager.restore()
        testScheduler.advanceUntilIdle()
        // A refresh would run on OkHttp threads: give it real time to show up.
        withContext(Dispatchers.Default) { delay(500) }

        assertEquals(SessionState.SignedIn, manager.state.value)
        assertEquals(0, auth.refreshes.get())
        assertTrue(auth.paths.isEmpty())
    }

    @Test
    fun `failed write after a rotation deletes the stored blob`() = runTest {
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()
        store.failWrites = true

        assertEquals(ApiResult.Success(Unit), manager.authorized { ping(it) })

        // The old blob holds a spent refresh token: better signed out after a restart.
        assertNull(store.stored)
        // This run keeps the rotated pair in memory.
        assertEquals("Bearer at-1", auth.bearers.last())
        assertEquals(ApiResult.Success(Unit), manager.authorized { ping(it) })
        assertEquals(1, auth.refreshes.get())
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `logout clears account data`() = runTest {
        var cleared = 0
        store.stored = session()
        val manager = SessionManager(
            AuthApi(transport, server.url("/")),
            store,
            now = { clock },
            clearAccountData = { cleared++ }
        )
        manager.restore()

        manager.logout()

        assertEquals(1, cleared)
    }

    @Test
    fun `logout first runs the hook while the session still works`() = runTest {
        val seen = mutableListOf<String>()
        store.stored = session()
        lateinit var manager: SessionManager
        manager = SessionManager(
            AuthApi(transport, server.url("/")),
            store,
            now = { clock },
            clearAccountData = { seen += "cleared" },
            beforeLogout = { seen += "before, signed in: ${manager.state.value == SessionState.SignedIn}" }
        )
        manager.restore()

        manager.logout()

        assertEquals(listOf("before, signed in: true", "cleared"), seen)
    }

    @Test
    fun `session ended by the server clears account data`() = runTest {
        var cleared = 0
        auth.refreshResponse = { error(401, "unauthorized") }
        store.stored = session(expiresInMillis = -1)
        val manager = SessionManager(
            AuthApi(transport, server.url("/")),
            store,
            now = { clock },
            clearAccountData = { cleared++ }
        )
        manager.restore()

        manager.authorized { ping(it) }

        assertEquals(1, cleared)
        assertEquals(SessionState.SignedOut(SignOutNotice.SessionEnded), manager.state.value)
    }

    @Test
    fun `notice can be dismissed`() = runTest {
        auth.refreshResponse = { error(401, "unauthorized") }
        val manager = signedInManager(session(expiresInMillis = -1))
        manager.restore()
        manager.authorized { ping(it) }

        manager.clearNotice()

        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `revoke all while signed in ends this session too`() = runTest {
        var cleared = 0
        store.stored = session()
        val manager = SessionManager(
            AuthApi(transport, server.url("/")),
            store,
            now = { clock },
            clearAccountData = { cleared++ }
        )
        manager.restore()

        val result = manager.revokeAllDevices(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        // The number may belong to another account: this device signs itself out as well.
        assertEquals(listOf("POST /auth/devices/revoke-all", "DELETE /devices/$DEVICE"), auth.requests)
        assertEquals("Bearer at-0", auth.bearers.last())
        assertNull(store.stored)
        assertEquals(1, cleared)
        assertEquals(SessionState.SignedOut(SignOutNotice.AllDevicesSignedOut), manager.state.value)
    }

    @Test
    fun `revoke all ignores a refused own device logout and never refreshes`() = runTest {
        // Own number: the server removed this device already, so its token is refused.
        auth.deleteResponse = { error(401, "unauthorized") }
        val manager = signedInManager(session())
        manager.restore()

        val result = manager.revokeAllDevices(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(listOf("POST /auth/devices/revoke-all", "DELETE /devices/$DEVICE"), auth.requests)
        assertEquals(0, auth.refreshes.get())
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(SignOutNotice.AllDevicesSignedOut), manager.state.value)
    }

    @Test
    fun `revoke all ends the session even when the own device logout fails on the server`() = runTest {
        auth.deleteResponse = { error(503, "unavailable") }
        val manager = signedInManager(session())
        manager.restore()

        val result = manager.revokeAllDevices(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut(SignOutNotice.AllDevicesSignedOut), manager.state.value)
    }

    @Test
    fun `failed revoke all keeps the session`() = runTest {
        auth.revokeAllResponse = { error(401, "unauthorized") }
        val manager = signedInManager(session())
        manager.restore()

        val result = manager.revokeAllDevices(NUMBER)

        assertEquals(ApiResult.HttpError(401, "unauthorized"), result)
        assertEquals("at-0", store.stored?.accessToken)
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `revoke all and sign in frees the device slots, then logs in`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.revokeAllAndSignIn(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(listOf("POST /auth/devices/revoke-all", "POST /auth/login"), auth.requests)
        assertEquals("login-at", store.stored?.accessToken)
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `revoke all and sign in stops when revoking fails`() = runTest {
        auth.revokeAllResponse = { error(429, "rate_limited") }
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.revokeAllAndSignIn(NUMBER)

        assertEquals(ApiResult.HttpError(429, "rate_limited"), result)
        assertEquals(listOf("POST /auth/devices/revoke-all"), auth.requests)
        assertEquals(SessionState.SignedOut(), manager.state.value)
    }

    @Test
    fun `delete account sends the number with the token and ends the session`() = runTest {
        var cleared = 0
        store.stored = session()
        val manager = SessionManager(
            AuthApi(transport, server.url("/")),
            store,
            now = { clock },
            clearAccountData = { cleared++ }
        )
        manager.restore()

        val result = manager.deleteAccount(NUMBER)

        assertEquals(ApiResult.Success(Unit), result)
        // The server signs out every device itself; no separate logout call.
        assertEquals(listOf("DELETE /auth/account"), auth.requests)
        assertEquals("Bearer at-0", auth.bearers.last())
        assertEquals("""{"accountNumber":"$NUMBER"}""", auth.bodies.last())
        assertNull(store.stored)
        assertEquals(1, cleared)
        assertEquals(SessionState.SignedOut(SignOutNotice.AccountDeletionRequested), manager.state.value)
    }

    @Test
    fun `delete account with the wrong number keeps the session`() = runTest {
        auth.deleteAccountResponse = { error(403, "forbidden") }
        val manager = signedInManager(session())
        manager.restore()

        val result = manager.deleteAccount(NUMBER)

        assertEquals(ApiResult.HttpError(403, "forbidden"), result)
        assertEquals("at-0", store.stored?.accessToken)
        assertEquals(SessionState.SignedIn, manager.state.value)
    }

    @Test
    fun `pairing pickup stores the token pair like a login and signs in`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.signInWithPairing(PICKUP)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(SessionState.SignedIn, manager.state.value)
        assertEquals(Session("pair-at", "pair-rt", "d-paired", clock + 900_000, clock), store.stored)
        assertEquals(listOf("POST /auth/pairing/token"), auth.requests)
        assertEquals("""{"pickupSecret":"$PICKUP"}""", auth.bodies.single())
    }

    @Test
    fun `pending pairing pickup stays signed out and stores nothing`() = runTest {
        auth.pairingTokenResponse = { error(400, "pairing_pending") }
        val manager = backgroundScope.manager()
        manager.restore()

        val result = manager.signInWithPairing(PICKUP)

        assertEquals(ApiResult.HttpError(400, "pairing_pending"), result)
        assertEquals(SessionState.SignedOut(), manager.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `account status goes through the token rules`() = runTest {
        val manager = signedInManager(session(expiresInMillis = 30_000))
        manager.restore()

        val result = manager.accountStatus()

        assertEquals(ApiResult.Success(AccountStatus(paid = false, paidUntil = null)), result)
        assertEquals(listOf("POST /auth/refresh", "GET /auth/account"), auth.requests)
        assertEquals("Bearer at-1", auth.bearers.last())
    }

    @Test
    fun `account status without session sends nothing`() = runTest {
        val manager = backgroundScope.manager()
        manager.restore()

        assertEquals(ApiResult.HttpError(401, "unauthorized"), manager.accountStatus())
        assertTrue(auth.requests.isEmpty())
    }

    @Test
    fun `session state never prints secrets`() {
        assertTrue(SessionState.SignedIn.toString().none { it.isDigit() })
        assertEquals("Session(<redacted>)", session().toString())
    }

    /** Auth service plus one protected endpoint, answering by path. */
    private class FakeAuthServer : Dispatcher() {
        val paths: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val bearers: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val refreshes = AtomicInteger()
        var refreshDelayMillis = 0L
        var loginResponse: () -> MockResponse = {
            json("""{"accessToken":"login-at","refreshToken":"login-rt","expiresIn":900,"deviceId":"$DEVICE"}""")
        }
        var confirmResponse: () -> MockResponse = { json("""{"confirmedAt":"2026-11-10T10:00:00Z"}""") }
        var deleteResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
        var revokeAllResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
        var deleteAccountResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
        var pairingTokenResponse: () -> MockResponse = {
            json("""{"accessToken":"pair-at","refreshToken":"pair-rt","expiresIn":900,"deviceId":"d-paired"}""")
        }
        var refreshResponse: () -> MockResponse = {
            val n = refreshes.get()
            validAccessTokens += "at-$n"
            json("""{"accessToken":"at-$n","refreshToken":"rt-$n","expiresIn":900}""")
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url.encodedPath
            paths += path
            requests += "${request.method} $path"
            request.headers["Authorization"]?.let { bearers += it }
            bodies += request.body?.utf8().orEmpty()
            return when {
                path == "/auth/login" -> loginResponse()

                path == "/auth/confirm-registration" -> confirmResponse()

                path == "/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    refreshResponse().newBuilder().headersDelay(refreshDelayMillis, TimeUnit.MILLISECONDS).build()
                }

                path.startsWith("/devices/") -> deleteResponse()

                path == "/auth/devices/revoke-all" -> revokeAllResponse()

                request.method == "GET" && path == "/auth/account" -> json("""{"paid":false,"paidUntil":null}""")

                path == "/auth/account" -> deleteAccountResponse()

                path == "/auth/pairing/token" -> pairingTokenResponse()

                path == "/api/ping" -> {
                    val token = request.headers["Authorization"]?.removePrefix("Bearer ")
                    if (token in
                        validAccessTokens
                    ) {
                        MockResponse.Builder().code(204).build()
                    } else {
                        error(401, "unauthorized")
                    }
                }

                else -> error(404, "not_found")
            }
        }
    }

    private companion object {
        const val NUMBER = "1234567890123456"
        const val DEVICE = "9315bf6d-7672-44db-9c09-2dd3212c9b0a"
        const val PICKUP = "pps_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        fun json(body: String, code: Int = 200) = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

        fun error(code: Int, error: String) = json("""{"error":"$error","message":"m"}""", code)
    }
}
