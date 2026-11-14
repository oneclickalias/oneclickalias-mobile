package com.oneclickalias.app.data.api

import com.oneclickalias.contracts.auth.AccountNumber
import com.oneclickalias.contracts.auth.AccountStatus
import com.oneclickalias.contracts.auth.ConfirmedRegistration
import com.oneclickalias.contracts.auth.Device
import com.oneclickalias.contracts.auth.DeviceList
import com.oneclickalias.contracts.auth.LoginResponse
import com.oneclickalias.contracts.auth.PairingClaim
import com.oneclickalias.contracts.auth.RefreshResponse
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthApiTest {
    private val server = MockWebServer()
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        server.start()
        // Base origin like Endpoints.authBase (no path).
        api = AuthApi(ApiTransport(createHttpClient()), server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun json(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun error(code: Int, error: String) = json(code, """{"error":"$error","message":"m"}""")

    @Test
    fun `create account number posts without credentials`() = runTest {
        server.enqueue(json(200, """{"accountNumber":"1234567890123456","createdAt":"2026-11-10T10:00:00Z"}"""))

        val result = api.createAccountNumber()

        assertEquals(ApiResult.Success(AccountNumber("1234567890123456", "2026-11-10T10:00:00Z")), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/account-number", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `create account number maps rate limit`() = runTest {
        server.enqueue(error(429, "rate_limited"))

        assertEquals(ApiResult.HttpError(429, "rate_limited"), api.createAccountNumber())
    }

    @Test
    fun `confirm registration sends the account number`() = runTest {
        server.enqueue(json(200, """{"confirmedAt":"2026-11-10T10:01:00Z"}"""))

        val result = api.confirmRegistration("1234567890123456")

        assertEquals(ApiResult.Success(ConfirmedRegistration("2026-11-10T10:01:00Z")), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/confirm-registration", request.url.encodedPath)
        assertEquals("""{"accountNumber":"1234567890123456"}""", request.body?.utf8())
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
    }

    @Test
    fun `confirm registration with unknown number is unauthorized`() = runTest {
        server.enqueue(error(401, "unauthorized"))

        assertEquals(ApiResult.HttpError(401, "unauthorized"), api.confirmRegistration("1234567890123456"))
    }

    @Test
    fun `login sends only the account number and returns the token pair`() = runTest {
        server.enqueue(
            json(200, """{"accessToken":"at","refreshToken":"rt","expiresIn":900,"deviceId":"dev-1"}""")
        )

        val result = api.login("1234567890123456")

        assertEquals(ApiResult.Success(LoginResponse("at", "rt", 900, "dev-1")), result)
        val request = server.takeRequest()
        assertEquals("/auth/login", request.url.encodedPath)
        assertEquals("""{"accountNumber":"1234567890123456"}""", request.body?.utf8())
    }

    @Test
    fun `login maps every documented error`() = runTest {
        val cases = listOf(
            400 to "invalid_request",
            401 to "unauthorized",
            403 to "registration_not_confirmed",
            409 to "device_limit_reached",
            429 to "rate_limited",
            500 to "internal_error"
        )
        cases.forEach { (status, code) -> server.enqueue(error(status, code)) }

        cases.forEach { (status, code) ->
            assertEquals(ApiResult.HttpError(status, code), api.login("1234567890123456"))
        }
    }

    @Test
    fun `login with a broken body is unknown`() = runTest {
        server.enqueue(json(200, """{"accessToken":"at"}"""))
        server.enqueue(json(503, "Service Unavailable"))

        assertEquals(ApiResult.HttpError(200, ApiResult.UNKNOWN_ERROR_CODE), api.login("1234567890123456"))
        assertEquals(ApiResult.HttpError(503, ApiResult.UNKNOWN_ERROR_CODE), api.login("1234567890123456"))
    }

    @Test
    fun `refresh sends refresh token and device id`() = runTest {
        server.enqueue(json(200, """{"accessToken":"at2","refreshToken":"rt2","expiresIn":900}"""))

        val result = api.refresh(refreshToken = "rt", deviceId = "dev-1")

        assertEquals(ApiResult.Success(RefreshResponse("at2", "rt2", 900)), result)
        val request = server.takeRequest()
        assertEquals("/auth/refresh", request.url.encodedPath)
        assertEquals("""{"refreshToken":"rt","deviceId":"dev-1"}""", request.body?.utf8())
    }

    @Test
    fun `refresh with a dead token is unauthorized`() = runTest {
        server.enqueue(error(401, "unauthorized"))

        assertEquals(ApiResult.HttpError(401, "unauthorized"), api.refresh("rt", "dev-1"))
    }

    @Test
    fun `delete device uses the bearer token and an encoded path`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val result = api.deleteDevice(deviceId = "9315bf6d-7672-44db-9c09-2dd3212c9b0a", accessToken = "at")

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/devices/9315bf6d-7672-44db-9c09-2dd3212c9b0a", request.url.encodedPath)
        assertEquals("Bearer at", request.headers["Authorization"])
    }

    @Test
    fun `delete device cannot escape the devices path`() = runTest {
        server.enqueue(error(400, "invalid_device_id"))

        api.deleteDevice(deviceId = "../auth/account", accessToken = "at")

        assertEquals("/devices/..%2Fauth%2Faccount", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `delete device maps not found`() = runTest {
        server.enqueue(error(404, "not_found"))

        assertEquals(ApiResult.HttpError(404, "not_found"), api.deleteDevice("dev-1", "at"))
    }

    @Test
    fun `list devices sends a bearer GET and decodes the list`() = runTest {
        server.enqueue(
            json(
                200,
                """{"devices":[{"deviceId":"dev-1","name":"brave otter","createdAt":"2026-11-05T10:00:00Z",""" +
                    """"current":true}]}"""
            )
        )

        val result = api.listDevices(accessToken = "at")

        assertEquals(
            ApiResult.Success(DeviceList(listOf(Device("dev-1", "brave otter", "2026-11-05T10:00:00Z", true)))),
            result
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/devices", request.url.encodedPath)
        assertEquals("Bearer at", request.headers["Authorization"])
    }

    @Test
    fun `revoke all posts the account number without a token`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val result = api.revokeAllDevices("1234567890123456")

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/devices/revoke-all", request.url.encodedPath)
        assertEquals("""{"accountNumber":"1234567890123456"}""", request.body?.utf8())
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `revoke all maps an unknown number`() = runTest {
        server.enqueue(error(401, "unauthorized"))

        assertEquals(ApiResult.HttpError(401, "unauthorized"), api.revokeAllDevices("1234567890123456"))
    }

    @Test
    fun `delete account sends the account number with the bearer token`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        val result = api.deleteAccount(accountNumber = "1234567890123456", accessToken = "at")

        assertEquals(ApiResult.Success(Unit), result)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/auth/account", request.url.encodedPath)
        assertEquals("""{"accountNumber":"1234567890123456"}""", request.body?.utf8())
        assertEquals("Bearer at", request.headers["Authorization"])
    }

    @Test
    fun `delete account maps a number of another account`() = runTest {
        server.enqueue(error(403, "forbidden"))

        assertEquals(ApiResult.HttpError(403, "forbidden"), api.deleteAccount("1234567890123456", "at"))
    }

    @Test
    fun `claim pairing posts only the code without credentials`() = runTest {
        server.enqueue(
            json(
                200,
                """{"pickupSecret":"$PICKUP","deviceName":"Casual Hippo",""" +
                    """"expiresAt":"2026-11-10T10:02:00Z","pollInterval":2}"""
            )
        )

        val result = api.claimPairing(CODE)

        assertEquals(ApiResult.Success(PairingClaim(PICKUP, "Casual Hippo", "2026-11-10T10:02:00Z", 2)), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/pairing/claim", request.url.encodedPath)
        assertEquals("""{"code":"$CODE"}""", request.body?.utf8())
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `claim pairing maps the pairing errors`() = runTest {
        for (code in listOf("pairing_expired", "pairing_used", "pairing_denied", "invalid_request")) {
            server.enqueue(error(400, code))
            assertEquals(ApiResult.HttpError(400, code), api.claimPairing(CODE))
        }
    }

    @Test
    fun `pick up pairing tokens posts the pickup secret and returns the token pair`() = runTest {
        server.enqueue(
            json(200, """{"accessToken":"at","refreshToken":"rt","expiresIn":900,"deviceId":"d-1"}""")
        )

        val result = api.pickUpPairingTokens(PICKUP)

        assertEquals(ApiResult.Success(LoginResponse("at", "rt", 900, "d-1")), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/pairing/token", request.url.encodedPath)
        assertEquals("""{"pickupSecret":"$PICKUP"}""", request.body?.utf8())
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `pick up pairing tokens maps pending`() = runTest {
        server.enqueue(error(400, "pairing_pending"))

        assertEquals(ApiResult.HttpError(400, "pairing_pending"), api.pickUpPairingTokens(PICKUP))
    }

    @Test
    fun `account status sends a bearer GET`() = runTest {
        server.enqueue(json(200, """{"paid":false,"paidUntil":null}"""))

        val result = api.accountStatus("at")

        assertEquals(ApiResult.Success(AccountStatus(paid = false, paidUntil = null)), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/auth/account", request.url.encodedPath)
        assertEquals("Bearer at", request.headers["Authorization"])
    }

    @Test
    fun `account status on an older server is not found`() = runTest {
        server.enqueue(error(404, "not_found"))

        assertEquals(ApiResult.HttpError(404, "not_found"), api.accountStatus("at"))
    }

    private companion object {
        const val CODE = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U"
        const val PICKUP = "pps_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
