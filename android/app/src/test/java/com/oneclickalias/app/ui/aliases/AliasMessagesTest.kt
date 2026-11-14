package com.oneclickalias.app.ui.aliases

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.ui.screens.AuthMessages
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasMessagesTest {
    private fun http(status: Int, code: String) = ApiResult.HttpError(status, code)

    @Test
    fun `write rate limit explains the limit`() {
        assertEquals(AliasMessages.RATE_LIMITED, AliasMessages.error(http(429, "rate_limited"), AliasAction.Create))
        assertEquals(AliasMessages.RATE_LIMITED, AliasMessages.error(http(429, "unknown"), AliasAction.Delete))
    }

    @Test
    fun `payment required says the account is not active`() {
        assertEquals(
            AliasMessages.NOT_ACTIVE,
            AliasMessages.error(http(403, "payment_required"), AliasAction.Trackers)
        )
    }

    @Test
    fun `blocked account uses the suspended text`() {
        assertEquals(
            AuthMessages.ACCOUNT_BLOCKED,
            AliasMessages.error(http(403, "account_blocked"), AliasAction.Create)
        )
    }

    @Test
    fun `recipient problems on create have their own texts`() {
        assertEquals(
            AliasMessages.RECIPIENT_NOT_CONFIRMED,
            AliasMessages.error(http(400, "recipient_not_verified"), AliasAction.Create)
        )
        assertEquals(AliasMessages.RECIPIENT_GONE, AliasMessages.error(http(404, "not_found"), AliasAction.Create))
        assertEquals(
            AliasMessages.RECIPIENT_GONE,
            AliasMessages.error(http(400, "invalid_recipient_id"), AliasAction.Create)
        )
    }

    @Test
    fun `missing alias on a change says it is gone`() {
        assertEquals(AliasMessages.ALIAS_GONE, AliasMessages.error(http(404, "not_found"), AliasAction.Trackers))
        assertEquals(AliasMessages.ALIAS_GONE, AliasMessages.error(http(400, "invalid_alias_id"), AliasAction.Delete))
    }

    @Test
    fun `generation failure asks to try again`() {
        assertEquals(
            AliasMessages.GENERATION_FAILED,
            AliasMessages.error(http(500, "alias_generation_failed"), AliasAction.Create)
        )
    }

    @Test
    fun `network errors say what happened`() {
        assertEquals(
            AuthMessages.OFFLINE,
            AliasMessages.error(ApiResult.NetworkError(NetworkErrorKind.Unreachable), AliasAction.Load)
        )
        assertEquals(
            AuthMessages.TIMEOUT,
            AliasMessages.error(ApiResult.NetworkError(NetworkErrorKind.Timeout), AliasAction.Create)
        )
    }

    @Test
    fun `server errors by code and status`() {
        assertEquals(AuthMessages.SERVER_ERROR, AliasMessages.error(http(500, "internal_error"), AliasAction.Load))
        assertEquals(AuthMessages.SERVER_ERROR, AliasMessages.error(http(502, "unknown"), AliasAction.Load))
        assertEquals(
            AuthMessages.UNAVAILABLE,
            AliasMessages.error(http(503, "auth_service_unavailable"), AliasAction.Load)
        )
        assertEquals(AuthMessages.UNAVAILABLE, AliasMessages.error(http(503, "unknown"), AliasAction.Create))
    }

    @Test
    fun `unknown codes fall back per action`() {
        assertEquals(AliasMessages.LOAD_FAILED, AliasMessages.error(http(418, "brand_new"), AliasAction.Load))
        assertEquals(AliasMessages.CREATE_FAILED, AliasMessages.error(http(400, "invalid_request"), AliasAction.Create))
        assertEquals(AliasMessages.TRACKERS_FAILED, AliasMessages.error(http(409, "brand_new"), AliasAction.Trackers))
        assertEquals(AliasMessages.DELETE_FAILED, AliasMessages.error(http(409, "brand_new"), AliasAction.Delete))
    }

    @Test
    fun `every text is different`() {
        val texts = listOf(
            AliasMessages.RATE_LIMITED,
            AliasMessages.NOT_ACTIVE,
            AliasMessages.RECIPIENT_NOT_CONFIRMED,
            AliasMessages.RECIPIENT_GONE,
            AliasMessages.ALIAS_GONE,
            AliasMessages.GENERATION_FAILED,
            AliasMessages.SESSION_ENDED,
            AliasMessages.LOAD_FAILED,
            AliasMessages.CREATE_FAILED,
            AliasMessages.TRACKERS_FAILED,
            AliasMessages.DELETE_FAILED,
            AuthMessages.OFFLINE,
            AuthMessages.TIMEOUT,
            AuthMessages.SERVER_ERROR,
            AuthMessages.UNAVAILABLE,
            AuthMessages.ACCOUNT_BLOCKED
        )
        assertEquals(texts.size, texts.toSet().size)
    }
}
