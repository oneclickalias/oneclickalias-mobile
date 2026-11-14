package com.oneclickalias.app.ui.devices

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.screens.AuthMessages
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMessagesTest {
    private fun text(status: Int, code: String, action: AccountAction) =
        AccountMessages.error(ApiResult.HttpError(status, code), action)

    @Test
    fun `revoke all with an unknown or malformed number`() {
        assertEquals(AuthMessages.WRONG_NUMBER, text(401, "unauthorized", AccountAction.RevokeAll))
        assertEquals(AuthMessages.INVALID_NUMBER, text(400, "invalid_request", AccountAction.RevokeAll))
    }

    @Test
    fun `delete account with the number of another account`() {
        assertEquals(AccountMessages.WRONG_ACCOUNT_NUMBER, text(403, "forbidden", AccountAction.DeleteAccount))
        assertEquals(AuthMessages.INVALID_NUMBER, text(400, "invalid_request", AccountAction.DeleteAccount))
    }

    @Test
    fun `a device signed out meanwhile`() {
        assertEquals(AccountMessages.DEVICE_GONE, text(404, "not_found", AccountAction.SignOutDevice))
        assertEquals(AccountMessages.DEVICE_GONE, text(400, "invalid_device_id", AccountAction.SignOutDevice))
    }

    @Test
    fun `a refused token means the session ended`() {
        assertEquals(AliasMessages.SESSION_ENDED, text(401, "unauthorized", AccountAction.LoadDevices))
        assertEquals(AliasMessages.SESSION_ENDED, text(401, "unauthorized", AccountAction.DeleteAccount))
    }

    @Test
    fun `shared codes and statuses`() {
        assertEquals(AuthMessages.RATE_LIMITED, text(429, "rate_limited", AccountAction.RevokeAll))
        assertEquals(AuthMessages.RATE_LIMITED, text(429, "unknown", AccountAction.DeleteAccount))
        assertEquals(AuthMessages.ACCOUNT_BLOCKED, text(403, "account_blocked", AccountAction.DeleteAccount))
        assertEquals(AuthMessages.UNAVAILABLE, text(503, "unknown", AccountAction.LoadDevices))
        assertEquals(AuthMessages.SERVER_ERROR, text(500, "internal_error", AccountAction.SignOutDevice))
        assertEquals(
            AuthMessages.OFFLINE,
            AccountMessages.error(ApiResult.NetworkError(NetworkErrorKind.Unreachable), AccountAction.RevokeAll)
        )
        assertEquals(
            AuthMessages.TIMEOUT,
            AccountMessages.error(ApiResult.NetworkError(NetworkErrorKind.Timeout), AccountAction.DeleteAccount)
        )
    }

    @Test
    fun `anything else falls back per action`() {
        assertEquals(AccountMessages.LOAD_DEVICES_FAILED, text(418, "x", AccountAction.LoadDevices))
        assertEquals(AccountMessages.SIGN_OUT_FAILED, text(418, "x", AccountAction.SignOutDevice))
        assertEquals(AccountMessages.REVOKE_ALL_FAILED, text(418, "x", AccountAction.RevokeAll))
        assertEquals(AccountMessages.DELETE_FAILED, text(418, "x", AccountAction.DeleteAccount))
        assertEquals(AccountMessages.REVOKE_ALL_FAILED, text(403, "forbidden", AccountAction.RevokeAll))
    }
}
