package com.oneclickalias.app.ui.pairing

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.auth.ErrorCode

/**
 * Texts of the QR login (table in docs/flows.md, "Log in with a
 * code"). Only `error` and the HTTP status decide; the
 * server's `message` is never shown.
 */
object PairingMessages {
    const val NOT_OURS = "This is not a OneClickAlias sign-in code."
    const val UPDATE_APP = "Update the app — this sign-in code is from a newer version of OneClickAlias."
    const val EXPIRED = "This code has expired. Show a new one in the dashboard."
    const val USED = "This code was already used. If you did not do this, deny the request in the dashboard."
    const val DENIED = "The sign-in was denied in the dashboard."
    const val ENDED = "This sign-in has ended. Show a new code in the dashboard."
    const val FAILED = "Signing in with the code failed — please try again."

    /** For `POST /auth/pairing/claim` and `POST /auth/pairing/token`. */
    fun error(failure: ApiResult<*>): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> AuthMessages.TIMEOUT
            NetworkErrorKind.Unreachable -> AuthMessages.OFFLINE
        }

        is ApiResult.HttpError -> byCode(failure.authErrorCode()) ?: byStatus(failure.status) ?: FAILED

        is ApiResult.Success -> FAILED
    }

    private fun byCode(code: ErrorCode?): String? = when (code) {
        ErrorCode.PAIRING_EXPIRED -> EXPIRED

        ErrorCode.PAIRING_USED -> USED

        ErrorCode.PAIRING_DENIED -> DENIED

        // Only from the pickup: the pickup secret is unknown (the pairing was deleted).
        ErrorCode.UNAUTHORIZED -> ENDED

        ErrorCode.RATE_LIMITED -> AuthMessages.RATE_LIMITED

        ErrorCode.INTERNAL_ERROR -> AuthMessages.SERVER_ERROR

        // invalid_request is a bug in this app (the code was checked); the rest are
        // not returned to the new device, or only while polling goes on.
        ErrorCode.INVALID_REQUEST,
        ErrorCode.PAIRING_PENDING,
        ErrorCode.PAIRING_SLOW_DOWN,
        ErrorCode.PAIRING_NOT_CLAIMED,
        ErrorCode.INVALID_PAIRING_ID,
        ErrorCode.REGISTRATION_NOT_CONFIRMED,
        ErrorCode.DEVICE_LIMIT_REACHED,
        ErrorCode.ACCOUNT_BLOCKED,
        ErrorCode.INVALID_DEVICE_ID,
        ErrorCode.INVALID_ACCOUNT_ID,
        ErrorCode.FORBIDDEN,
        ErrorCode.NOT_FOUND,
        null -> null
    }

    private fun byStatus(status: Int): String? = when {
        status == HTTP_TOO_MANY_REQUESTS -> AuthMessages.RATE_LIMITED
        status == HTTP_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE
        status >= HTTP_SERVER_ERROR -> AuthMessages.SERVER_ERROR
        else -> null
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
