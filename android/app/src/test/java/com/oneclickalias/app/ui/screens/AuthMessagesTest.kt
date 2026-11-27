package com.oneclickalias.app.ui.screens

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMessagesTest {
    private fun login(status: Int, code: String) = AuthMessages.login(ApiResult.HttpError(status, code))

    @Test
    fun `login errors get their own texts`() {
        assertEquals(AuthMessages.WRONG_NUMBER, login(401, "unauthorized"))
        assertEquals(AuthMessages.INVALID_NUMBER, login(400, "invalid_request"))
        assertEquals(AuthMessages.NOT_CONFIRMED, login(403, "registration_not_confirmed"))
        assertEquals(AuthMessages.DEVICE_LIMIT, login(409, "device_limit_reached"))
        assertEquals(AuthMessages.ACCOUNT_BLOCKED, login(403, "account_blocked"))
        assertEquals(AuthMessages.RATE_LIMITED, login(429, "rate_limited"))
        assertEquals(AuthMessages.SERVER_ERROR, login(500, "internal_error"))
        assertEquals(AuthMessages.UNAVAILABLE, login(503, ApiResult.UNKNOWN_ERROR_CODE))
    }

    @Test
    fun `the suspension text says where support is`() {
        // The app has no support screen; tickets are opened on the website.
        assertTrue(AuthMessages.ACCOUNT_BLOCKED.contains("oneclickalias.com"))
    }

    @Test
    fun `network failures are told apart from server errors`() {
        assertEquals(AuthMessages.OFFLINE, AuthMessages.login(ApiResult.NetworkError(NetworkErrorKind.Unreachable)))
        assertEquals(AuthMessages.TIMEOUT, AuthMessages.login(ApiResult.NetworkError(NetworkErrorKind.Timeout)))
        assertNotEquals(AuthMessages.OFFLINE, AuthMessages.TIMEOUT)
    }

    @Test
    fun `status decides when the code is unknown`() {
        assertEquals(AuthMessages.RATE_LIMITED, login(429, ApiResult.UNKNOWN_ERROR_CODE))
        assertEquals(AuthMessages.SERVER_ERROR, login(502, "something_new"))
        assertEquals(AuthMessages.LOGIN_FAILED, login(418, "something_new"))
        assertEquals(AuthMessages.LOGIN_FAILED, login(200, ApiResult.UNKNOWN_ERROR_CODE))
    }

    @Test
    fun `account creation has its own fallback`() {
        assertEquals(AuthMessages.CREATE_FAILED, AuthMessages.createAccount(ApiResult.HttpError(400, "x")))
        assertEquals(AuthMessages.RATE_LIMITED, AuthMessages.createAccount(ApiResult.HttpError(429, "rate_limited")))
        assertEquals(
            AuthMessages.OFFLINE,
            AuthMessages.createAccount(ApiResult.NetworkError(NetworkErrorKind.Unreachable))
        )
    }
}
