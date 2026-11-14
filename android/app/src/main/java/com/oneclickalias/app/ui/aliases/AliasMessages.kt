package com.oneclickalias.app.ui.aliases

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.ams.ErrorCode

/** What the user tried when an AMS call failed; picks the fallback text. */
enum class AliasAction { Load, Create, Trackers, Delete }

/**
 * User-facing texts for AMS errors, worded like the website
 * (OCA-frontend/src/lib/api.ts, ALIAS_ERROR_MESSAGES). Only `error` and the
 * HTTP status decide; the server's `message` is never shown. Table:
 * docs/flows.md ("Alias errors").
 */
object AliasMessages {
    const val RATE_LIMITED =
        "Too many changes in a short time — up to 30 per minute are possible. Please wait a moment and try again."
    const val NOT_ACTIVE = "Your account is not active yet."
    const val RECIPIENT_NOT_CONFIRMED =
        "This recipient address is not confirmed — confirm it from the email we sent, or choose another one."
    const val RECIPIENT_GONE = "This recipient address no longer exists — choose another one."
    const val ALIAS_GONE = "This alias no longer exists."
    const val GENERATION_FAILED = "No free alias name could be generated — please try again."
    const val SESSION_ENDED = "Your session has ended — please log in again."
    const val LOAD_FAILED = "Your aliases could not be loaded — please try again."
    const val CREATE_FAILED = "The alias could not be created — please try again."
    const val TRACKERS_FAILED = "Tracker blocking could not be changed — please try again."
    const val DELETE_FAILED = "The alias could not be deleted — please try again."

    /** No app can open web links. */
    fun noBrowser(url: String): String = "No browser app found — open $url in a browser."

    fun error(failure: ApiResult<*>, action: AliasAction): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> AuthMessages.TIMEOUT
            NetworkErrorKind.Unreachable -> AuthMessages.OFFLINE
        }

        is ApiResult.HttpError ->
            byCode(failure.amsErrorCode(), action) ?: byStatus(failure.status) ?: fallback(action)

        is ApiResult.Success -> fallback(action)
    }

    private fun byCode(code: ErrorCode?, action: AliasAction): String? = when (code) {
        ErrorCode.RATE_LIMITED -> RATE_LIMITED

        ErrorCode.PAYMENT_REQUIRED -> NOT_ACTIVE

        ErrorCode.ACCOUNT_BLOCKED -> AuthMessages.ACCOUNT_BLOCKED

        ErrorCode.RECIPIENT_NOT_VERIFIED -> RECIPIENT_NOT_CONFIRMED

        ErrorCode.INVALID_RECIPIENT_ID -> RECIPIENT_GONE

        // Create looks up the recipient; the other writes look up the alias.
        ErrorCode.NOT_FOUND -> if (action == AliasAction.Create) RECIPIENT_GONE else ALIAS_GONE

        ErrorCode.INVALID_ALIAS_ID -> ALIAS_GONE

        ErrorCode.ALIAS_GENERATION_FAILED -> GENERATION_FAILED

        ErrorCode.AUTH_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE

        ErrorCode.INTERNAL_ERROR -> AuthMessages.SERVER_ERROR

        // SessionManager ends the session first; this text shows only if a fresh token is refused too.
        ErrorCode.UNAUTHORIZED -> SESSION_ENDED

        // invalid_request is a bug in this app; the rest are not returned by these endpoints.
        ErrorCode.INVALID_REQUEST,
        ErrorCode.INVALID_DEST_EMAIL,
        ErrorCode.INVALID_ACCOUNT_ID,
        ErrorCode.INVALID_TICKET_ID,
        ErrorCode.INVALID_TOKEN,
        ErrorCode.NOT_SHARED,
        ErrorCode.ALREADY_EXISTS,
        ErrorCode.ALREADY_VERIFIED,
        ErrorCode.RECIPIENT_IN_USE,
        ErrorCode.TICKET_LIMIT_REACHED,
        ErrorCode.TICKET_CLOSED,
        null -> null
    }

    private fun byStatus(status: Int): String? = when {
        status == HTTP_TOO_MANY_REQUESTS -> RATE_LIMITED
        status == HTTP_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE
        status >= HTTP_SERVER_ERROR -> AuthMessages.SERVER_ERROR
        else -> null
    }

    private fun fallback(action: AliasAction): String = when (action) {
        AliasAction.Load -> LOAD_FAILED
        AliasAction.Create -> CREATE_FAILED
        AliasAction.Trackers -> TRACKERS_FAILED
        AliasAction.Delete -> DELETE_FAILED
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
