package com.oneclickalias.app.data.api

import com.oneclickalias.contracts.auth.AccountNumber
import com.oneclickalias.contracts.auth.AccountNumberRequest
import com.oneclickalias.contracts.auth.AccountStatus
import com.oneclickalias.contracts.auth.ConfirmedRegistration
import com.oneclickalias.contracts.auth.DeviceList
import com.oneclickalias.contracts.auth.LoginRequest
import com.oneclickalias.contracts.auth.LoginRequestClaim
import com.oneclickalias.contracts.auth.LoginResponse
import com.oneclickalias.contracts.auth.Pairing
import com.oneclickalias.contracts.auth.PairingClaim
import com.oneclickalias.contracts.auth.PairingClaimRequest
import com.oneclickalias.contracts.auth.PairingTokenRequest
import com.oneclickalias.contracts.auth.RefreshRequest
import com.oneclickalias.contracts.auth.RefreshResponse
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Authentication service user API.
 * Stateless: tokens are passed in and returned; storing and refreshing them is
 * the job of `SessionManager`.
 *
 * @param base origin of the service, e.g. [Endpoints.authBase].
 */
class AuthApi(private val transport: ApiTransport, private val base: HttpUrl) {

    /** `POST /auth/account-number`: new, still unconfirmed account. */
    suspend fun createAccountNumber(): ApiResult<AccountNumber> =
        transport.call(post("auth/account-number", ByteArray(0).toRequestBody()), AccountNumber.serializer())

    /** `POST /auth/confirm-registration`: unlocks login for the account (idempotent). */
    suspend fun confirmRegistration(accountNumber: String): ApiResult<ConfirmedRegistration> = transport.call(
        post(
            "auth/confirm-registration",
            transport.jsonBody(AccountNumberRequest.serializer(), AccountNumberRequest(accountNumber))
        ),
        ConfirmedRegistration.serializer()
    )

    /** `POST /auth/login`: registers this device and returns its first token pair. */
    suspend fun login(accountNumber: String): ApiResult<LoginResponse> = transport.call(
        post("auth/login", transport.jsonBody(LoginRequest.serializer(), LoginRequest(accountNumber))),
        LoginResponse.serializer()
    )

    /** `POST /auth/refresh`: rotates the token pair; the old refresh token is spent. */
    suspend fun refresh(refreshToken: String, deviceId: String): ApiResult<RefreshResponse> = transport.call(
        post("auth/refresh", transport.jsonBody(RefreshRequest.serializer(), RefreshRequest(refreshToken, deviceId))),
        RefreshResponse.serializer()
    )

    /** `DELETE /devices/{deviceId}`: signs a device of the account out. */
    suspend fun deleteDevice(deviceId: String, accessToken: String): ApiResult<Unit> {
        val url = base.newBuilder().addPathSegment("devices").addPathSegment(deviceId).build()
        val request = Request.Builder().url(url).delete().bearer(accessToken).build()
        return transport.callNoContent(request)
    }

    /** `GET /devices`: the devices signed in to the account, `current` marks this one. */
    suspend fun listDevices(accessToken: String): ApiResult<DeviceList> {
        val url = base.newBuilder().addPathSegment("devices").build()
        return transport.call(Request.Builder().url(url).get().bearer(accessToken).build(), DeviceList.serializer())
    }

    /**
     * `POST /auth/devices/revoke-all`: signs out every device of the account,
     * this one included. No token: the account number is the credential.
     */
    suspend fun revokeAllDevices(accountNumber: String): ApiResult<Unit> = transport.callNoContent(
        post(
            "auth/devices/revoke-all",
            transport.jsonBody(AccountNumberRequest.serializer(), AccountNumberRequest(accountNumber))
        )
    )

    /**
     * `DELETE /auth/account`: requests the deletion of the account. The account
     * number is the re-authentication; all devices are signed out at once, and a
     * login within the retention period cancels the deletion.
     */
    suspend fun deleteAccount(accountNumber: String, accessToken: String): ApiResult<Unit> {
        val body = transport.jsonBody(AccountNumberRequest.serializer(), AccountNumberRequest(accountNumber))
        val url = base.newBuilder().addPathSegments("auth/account").build()
        return transport.callNoContent(Request.Builder().url(url).delete(body).bearer(accessToken).build())
    }

    /** `GET /auth/account`: whether the account is paid (the AMS's paid gate), nothing else. */
    suspend fun accountStatus(accessToken: String): ApiResult<AccountStatus> {
        val url = base.newBuilder().addPathSegments("auth/account").build()
        return transport.call(Request.Builder().url(url).get().bearer(accessToken).build(), AccountStatus.serializer())
    }

    /**
     * `POST /auth/pairing/claim`: redeems the code of a QR login (no token: the
     * one-time code is the credential). Returns the device name to show and the
     * pickup secret for [pickUpPairingTokens], which stays in memory only.
     */
    suspend fun claimPairing(code: String): ApiResult<PairingClaim> = transport.call(
        post("auth/pairing/claim", transport.jsonBody(PairingClaimRequest.serializer(), PairingClaimRequest(code))),
        PairingClaim.serializer()
    )

    /**
     * `POST /auth/pairing/token`: after the approval in the dashboard, the first
     * call returns this device's token pair (like [login]); before it,
     * `400 pairing_pending`.
     */
    suspend fun pickUpPairingTokens(pickupSecret: String): ApiResult<LoginResponse> = transport.call(
        post(
            "auth/pairing/token",
            transport.jsonBody(PairingTokenRequest.serializer(), PairingTokenRequest(pickupSecret))
        ),
        LoginResponse.serializer()
    )

    /**
     * `POST /auth/pairing/login-request/claim`: redeems the code a signed-out
     * browser shows (docs/flows.md, "Sign in a browser"). Returns
     * the browser's device name to compare; [approvePairing] signs it in.
     */
    suspend fun claimLoginRequest(code: String, accessToken: String): ApiResult<LoginRequestClaim> = transport.call(
        authorizedPost(
            "auth/pairing/login-request/claim",
            transport.jsonBody(PairingClaimRequest.serializer(), PairingClaimRequest(code)),
            accessToken
        ),
        LoginRequestClaim.serializer()
    )

    /** `POST /auth/pairing/{pairingId}/approve`: signs the browser in (device limit checked here). */
    suspend fun approvePairing(pairingId: String, accessToken: String): ApiResult<Pairing> = transport.call(
        authorizedPost("auth/pairing/$pairingId/approve", ByteArray(0).toRequestBody(), accessToken),
        Pairing.serializer()
    )

    /** `POST /auth/pairing/{pairingId}/deny`: the browser is not signed in. */
    suspend fun denyPairing(pairingId: String, accessToken: String): ApiResult<Pairing> = transport.call(
        authorizedPost("auth/pairing/$pairingId/deny", ByteArray(0).toRequestBody(), accessToken),
        Pairing.serializer()
    )

    private fun authorizedPost(path: String, body: okhttp3.RequestBody, accessToken: String): Request =
        Request.Builder().url(base.newBuilder().addPathSegments(path).build()).post(body).bearer(accessToken).build()

    private fun post(path: String, body: okhttp3.RequestBody): Request =
        Request.Builder().url(base.newBuilder().addPathSegments(path).build()).post(body).build()
}

/** Adds the `Authorization: Bearer` header. Shared by every authenticated client. */
fun Request.Builder.bearer(accessToken: String): Request.Builder = header("Authorization", "Bearer $accessToken")
