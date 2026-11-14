package com.oneclickalias.app.ui.devices

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.auth.ErrorCode

/** What the user tried when a device or account call failed; picks the fallback text. */
enum class AccountAction { LoadDevices, SignOutDevice, RevokeAll, DeleteAccount }

/**
 * User-facing texts for the device and account calls of the Auth service,
 * worded like the website (OCA-frontend/src/lib/api.ts, DEVICE_ERROR_MESSAGES
 * and AUTH_ACCOUNT_ERROR_MESSAGES). Only `error` and the HTTP status decide;
 * the server's `message` is never shown. Table: docs/flows.md.
 */
object AccountMessages {
    const val DEVICE_GONE = "This device is no longer signed in."
    const val WRONG_ACCOUNT_NUMBER = "This doesn't match your current account number."
    const val LOAD_DEVICES_FAILED = "Your devices could not be loaded — please try again."
    const val SIGN_OUT_FAILED = "The device could not be signed out — please try again."
    const val REVOKE_ALL_FAILED = "Your devices could not be signed out — please try again."
    const val DELETE_FAILED = "Your account could not be deleted — please try again."

    fun error(failure: ApiResult<*>, action: AccountAction): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> AuthMessages.TIMEOUT
            NetworkErrorKind.Unreachable -> AuthMessages.OFFLINE
        }

        is ApiResult.HttpError ->
            byCode(failure.authErrorCode(), action) ?: byStatus(failure.status) ?: fallback(action)

        is ApiResult.Success -> fallback(action)
    }

    private fun byCode(code: ErrorCode?, action: AccountAction): String? = when (code) {
        // Revoke-all has no token: unauthorized means the number belongs to no account.
        ErrorCode.UNAUTHORIZED -> if (action == AccountAction.RevokeAll) {
            AuthMessages.WRONG_NUMBER
        } else {
            // SessionManager ends the session first; this text shows only if a fresh token is refused too.
            AliasMessages.SESSION_ENDED
        }

        ErrorCode.INVALID_REQUEST -> when (action) {
            AccountAction.RevokeAll, AccountAction.DeleteAccount -> AuthMessages.INVALID_NUMBER
            else -> null
        }

        ErrorCode.FORBIDDEN -> if (action == AccountAction.DeleteAccount) WRONG_ACCOUNT_NUMBER else null

        ErrorCode.NOT_FOUND, ErrorCode.INVALID_DEVICE_ID -> DEVICE_GONE

        ErrorCode.ACCOUNT_BLOCKED -> AuthMessages.ACCOUNT_BLOCKED

        ErrorCode.RATE_LIMITED -> AuthMessages.RATE_LIMITED

        ErrorCode.INTERNAL_ERROR -> AuthMessages.SERVER_ERROR

        // Not returned by these endpoints; the status decides.
        ErrorCode.INVALID_ACCOUNT_ID,
        ErrorCode.REGISTRATION_NOT_CONFIRMED,
        ErrorCode.DEVICE_LIMIT_REACHED,
        ErrorCode.INVALID_PAIRING_ID,
        ErrorCode.PAIRING_PENDING,
        ErrorCode.PAIRING_SLOW_DOWN,
        ErrorCode.PAIRING_DENIED,
        ErrorCode.PAIRING_EXPIRED,
        ErrorCode.PAIRING_USED,
        ErrorCode.PAIRING_NOT_CLAIMED,
        null -> null
    }

    private fun byStatus(status: Int): String? = when {
        status == HTTP_TOO_MANY_REQUESTS -> AuthMessages.RATE_LIMITED
        status == HTTP_SERVICE_UNAVAILABLE -> AuthMessages.UNAVAILABLE
        status >= HTTP_SERVER_ERROR -> AuthMessages.SERVER_ERROR
        else -> null
    }

    private fun fallback(action: AccountAction): String = when (action) {
        AccountAction.LoadDevices -> LOAD_DEVICES_FAILED
        AccountAction.SignOutDevice -> SIGN_OUT_FAILED
        AccountAction.RevokeAll -> REVOKE_ALL_FAILED
        AccountAction.DeleteAccount -> DELETE_FAILED
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
