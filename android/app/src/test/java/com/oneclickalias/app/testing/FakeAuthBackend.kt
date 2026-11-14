package com.oneclickalias.app.testing

import com.oneclickalias.app.data.api.ApiJson
import com.oneclickalias.contracts.auth.Device
import com.oneclickalias.contracts.auth.DeviceList
import java.util.Collections
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * MockWebServer dispatcher that answers the Auth endpoints of the sign-up,
 * login, device and account flows. Responses can be swapped per test.
 */
class FakeAuthBackend : Dispatcher() {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())

    var createResponse: () -> MockResponse = {
        json("""{"accountNumber":"$NUMBER","createdAt":"2026-11-10T10:00:00Z"}""")
    }
    var confirmResponse: () -> MockResponse = { json("""{"confirmedAt":"2026-11-10T10:01:00Z"}""") }
    var loginResponse: () -> MockResponse = {
        json("""{"accessToken":"$ACCESS","refreshToken":"$REFRESH","expiresIn":900,"deviceId":"$DEVICE"}""")
    }
    var deleteResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
    var refreshResponse: () -> MockResponse = { error(401, "unauthorized") }
    var revokeAllResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
    var deleteAccountResponse: () -> MockResponse = { MockResponse.Builder().code(204).build() }
    var accountStatusResponse: () -> MockResponse = { json("""{"paid":true,"paidUntil":"2026-12-10T10:00:00Z"}""") }

    /** QR login: `claim` answers with [PICKUP] and [PAIRED_NAME], due two minutes from now. */
    var claimResponse: () -> MockResponse = {
        json(
            """{"pickupSecret":"$PICKUP","deviceName":"$PAIRED_NAME",""" +
                """"expiresAt":"${java.time.Instant.now().plusSeconds(120)}","pollInterval":2}"""
        )
    }

    /** Answers of `POST /auth/pairing/token` in order; the last one repeats. */
    val pairingTokenResponses: MutableList<() -> MockResponse> = Collections.synchronizedList(
        mutableListOf({
            json(
                """{"accessToken":"$PAIRED_ACCESS","refreshToken":"$PAIRED_REFRESH","expiresIn":900,""" +
                    """"deviceId":"$PAIRED_DEVICE"}"""
            )
        })
    )

    /** Reverse direction: `login-request/claim` names the browser [BROWSER_NAME]. */
    var loginRequestClaimResponse: () -> MockResponse = {
        json(
            """{"pairingId":"$BROWSER_PAIRING","deviceName":"$BROWSER_NAME",""" +
                """"expiresAt":"${java.time.Instant.now().plusSeconds(120)}"}"""
        )
    }
    var approveResponse: () -> MockResponse = { pairing("approved") }
    var denyResponse: () -> MockResponse = { pairing("denied") }

    private fun pairing(status: String) = json(
        """{"pairingId":"$BROWSER_PAIRING","status":"$status","deviceName":"$BROWSER_NAME","expiresAt":null}"""
    )

    /** The account's devices for `GET /devices`; `DELETE /devices/{id}` removes from here. */
    val devices: MutableList<Device> = Collections.synchronizedList(mutableListOf())
    var listDevicesResponse: (() -> MockResponse)? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        requests += "${request.method} $path"
        bodies += request.body?.utf8().orEmpty()
        return when {
            path == "/auth/account-number" -> createResponse()

            path == "/auth/confirm-registration" -> confirmResponse()

            path == "/auth/login" -> loginResponse()

            path == "/auth/refresh" -> refreshResponse()

            path == "/auth/devices/revoke-all" -> revokeAllResponse()

            request.method == "GET" && path == "/auth/account" -> accountStatusResponse()

            path == "/auth/account" -> deleteAccountResponse()

            path == "/auth/pairing/claim" -> claimResponse()

            path == "/auth/pairing/login-request/claim" -> loginRequestClaimResponse()

            path == "/auth/pairing/$BROWSER_PAIRING/approve" -> approveResponse()

            path == "/auth/pairing/$BROWSER_PAIRING/deny" -> denyResponse()

            path == "/auth/pairing/token" -> synchronized(pairingTokenResponses) {
                (if (pairingTokenResponses.size > 1) pairingTokenResponses.removeAt(0) else pairingTokenResponses[0])()
            }

            path == "/devices" -> listDevicesResponse?.invoke()
                ?: json(ApiJson.encodeToString(DeviceList.serializer(), DeviceList(devices.toList())))

            path.startsWith("/devices/") -> {
                devices.removeIf { it.deviceId == path.removePrefix("/devices/") }
                deleteResponse()
            }

            else -> error(404, "not_found")
        }
    }

    companion object {
        const val BROWSER_PAIRING = "c9f0a2e1-0000-4000-8000-000000000050"
        const val BROWSER_NAME = "Casual Hippo"
        const val NUMBER = "4711000012345678"
        const val ACCESS = "access-token-value"
        const val REFRESH = "refresh-token-value"
        const val DEVICE = "9315bf6d-7672-44db-9c09-2dd3212c9b0a"
        const val PAIRING_CODE = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U"
        const val PICKUP = "pps_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val PAIRED_NAME = "Casual Hippo"
        const val PAIRED_ACCESS = "paired-access-token"
        const val PAIRED_REFRESH = "paired-refresh-token"
        const val PAIRED_DEVICE = "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"

        fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

        fun error(code: Int, error: String): MockResponse = json("""{"error":"$error","message":"m"}""", code)
    }
}
