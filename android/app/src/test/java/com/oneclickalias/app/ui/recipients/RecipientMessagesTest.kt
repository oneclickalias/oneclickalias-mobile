package com.oneclickalias.app.ui.recipients

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.screens.AuthMessages
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientMessagesTest {
    private fun text(status: Int, code: String, action: RecipientAction = RecipientAction.Add) =
        RecipientMessages.error(ApiResult.HttpError(status, code), action)

    @Test
    fun `add errors explain what is wrong with the address`() {
        assertEquals(RecipientMessages.INVALID_EMAIL, text(400, "invalid_dest_email"))
        assertEquals(RecipientMessages.INVALID_EMAIL, text(400, "invalid_request"))
        assertEquals(RecipientMessages.ALREADY_ADDED, text(409, "already_exists"))
    }

    @Test
    fun `delete of an address in use says why`() {
        assertEquals(RecipientMessages.IN_USE, text(409, "recipient_in_use", RecipientAction.Delete))
    }

    @Test
    fun `resend of a confirmed address and gone addresses`() {
        assertEquals(RecipientMessages.ALREADY_CONFIRMED, text(409, "already_verified", RecipientAction.Resend))
        assertEquals(RecipientMessages.GONE, text(404, "not_found", RecipientAction.Resend))
        assertEquals(RecipientMessages.GONE, text(400, "invalid_recipient_id", RecipientAction.Delete))
    }

    @Test
    fun `shared codes use the alias and auth texts`() {
        assertEquals(AliasMessages.RATE_LIMITED, text(429, "rate_limited"))
        assertEquals(AliasMessages.RATE_LIMITED, text(429, "unknown"))
        assertEquals(AliasMessages.NOT_ACTIVE, text(403, "payment_required"))
        assertEquals(AuthMessages.ACCOUNT_BLOCKED, text(403, "account_blocked"))
        assertEquals(AuthMessages.UNAVAILABLE, text(503, "auth_service_unavailable"))
        assertEquals(AuthMessages.UNAVAILABLE, text(503, "unknown"))
        assertEquals(AuthMessages.SERVER_ERROR, text(500, "internal_error"))
        assertEquals(AliasMessages.SESSION_ENDED, text(401, "unauthorized"))
    }

    @Test
    fun `network errors`() {
        assertEquals(
            AuthMessages.OFFLINE,
            RecipientMessages.error(ApiResult.NetworkError(NetworkErrorKind.Unreachable), RecipientAction.Load)
        )
        assertEquals(
            AuthMessages.TIMEOUT,
            RecipientMessages.error(ApiResult.NetworkError(NetworkErrorKind.Timeout), RecipientAction.Delete)
        )
    }

    @Test
    fun `anything else falls back per action`() {
        assertEquals(RecipientMessages.LOAD_FAILED, text(418, "brand_new_code", RecipientAction.Load))
        assertEquals(RecipientMessages.ADD_FAILED, text(418, "brand_new_code", RecipientAction.Add))
        assertEquals(RecipientMessages.RESEND_FAILED, text(418, "brand_new_code", RecipientAction.Resend))
        assertEquals(RecipientMessages.DELETE_FAILED, text(418, "brand_new_code", RecipientAction.Delete))
    }
}
