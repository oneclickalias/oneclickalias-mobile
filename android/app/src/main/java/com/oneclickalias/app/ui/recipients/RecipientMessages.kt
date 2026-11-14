package com.oneclickalias.app.ui.recipients

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.ams.ErrorCode

/** What the user tried when a recipient call failed; picks the fallback text. */
enum class RecipientAction { Load, Add, Resend, Delete }

/**
 * User-facing texts for recipient errors, worded like the website
 * (OCA-frontend/src/lib/api.ts, RECIPIENT_ERROR_MESSAGES). Only `error` and
 * the HTTP status decide; the server's `message` is never shown. Table:
 * docs/flows.md ("Recipient errors").
 */
object RecipientMessages {
    const val INVALID_EMAIL = "Invalid email address — check the format."
    const val ALREADY_ADDED = "This address is already on your list."
    const val ALREADY_CONFIRMED = "This address is already confirmed."
    const val GONE = "This address no longer exists."
    const val IN_USE =
        "This address is still used by one or more aliases — delete those aliases or move them to another " +
            "address first."
    const val LOAD_FAILED = "Your recipient addresses could not be loaded — please try again."
    const val ADD_FAILED = "The address could not be added — please try again."
    const val RESEND_FAILED = "The confirmation email could not be sent — please try again."
    const val DELETE_FAILED = "The address could not be deleted — please try again."

    fun error(failure: ApiResult<*>, action: RecipientAction): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> AuthMessages.TIMEOUT
            NetworkErrorKind.Unreachable -> AuthMessages.OFFLINE
        }

        is ApiResult.HttpError -> byCode(failure.amsErrorCode()) ?: byStatus(failure.status) ?: fallback(action)

        is ApiResult.Success -> fallback(action)
    }

    private fun byCode(code: ErrorCode?): String? = when (code) {
        // invalid_request: the body was not valid JSON, in practice an address the app could not send.
        ErrorCode.INVALID_DEST_EMAIL, ErrorCode.INVALID_REQUEST -> INVALID_EMAIL

        ErrorCode.ALREADY_EXISTS -> ALREADY_ADDED

        ErrorCode.ALREADY_VERIFIED -> ALREADY_CONFIRMED

        ErrorCode.RECIPIENT_IN_USE -> IN_USE

        ErrorCode.NOT_FOUND, ErrorCode.INVALID_RECIPIENT_ID -> GONE

        ErrorCode.RATE_LIMITED -> AliasMessages.RATE_LIMITED

        ErrorCode.PAYMENT_REQUIRED -> AliasMessages.NOT_ACTIVE

        ErrorCode.ACCOUNT_BLOCKED -> AuthMessages.ACCOUNT_BLOCKED

        ErrorCode.AUTH_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE

        ErrorCode.INTERNAL_ERROR -> AuthMessages.SERVER_ERROR

        // SessionManager ends the session first; this text shows only if a fresh token is refused too.
        ErrorCode.UNAUTHORIZED -> AliasMessages.SESSION_ENDED

        // Not returned by the recipient endpoints.
        ErrorCode.INVALID_ALIAS_ID,
        ErrorCode.INVALID_ACCOUNT_ID,
        ErrorCode.INVALID_TICKET_ID,
        ErrorCode.RECIPIENT_NOT_VERIFIED,
        ErrorCode.INVALID_TOKEN,
        ErrorCode.NOT_SHARED,
        ErrorCode.TICKET_LIMIT_REACHED,
        ErrorCode.TICKET_CLOSED,
        ErrorCode.ALIAS_GENERATION_FAILED,
        null -> null
    }

    private fun byStatus(status: Int): String? = when {
        status == HTTP_TOO_MANY_REQUESTS -> AliasMessages.RATE_LIMITED
        status == HTTP_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE
        status >= HTTP_SERVER_ERROR -> AuthMessages.SERVER_ERROR
        else -> null
    }

    private fun fallback(action: RecipientAction): String = when (action) {
        RecipientAction.Load -> LOAD_FAILED
        RecipientAction.Add -> ADD_FAILED
        RecipientAction.Resend -> RESEND_FAILED
        RecipientAction.Delete -> DELETE_FAILED
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
