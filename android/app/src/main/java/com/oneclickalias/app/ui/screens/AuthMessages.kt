package com.oneclickalias.app.ui.screens

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.contracts.auth.ErrorCode

/**
 * User-facing texts for Auth errors, worded like the website
 * (OCA-frontend/src/lib/api.ts). Only `error` and the HTTP status decide;
 * the server's `message` is never shown. Table: docs/flows.md.
 */
object AuthMessages {
    const val WRONG_NUMBER = "This account number does not exist."
    const val INVALID_NUMBER = "Invalid account number — check that it has exactly 16 digits."
    const val NOT_CONFIRMED =
        "Your registration isn't complete yet — confirm that you've saved your account number."
    const val CONFIRM_TO_CONTINUE = "Please confirm that you've saved your account number."
    const val DEVICE_LIMIT =
        "Device limit reached — sign out another device first, or sign out all devices."
    const val ACCOUNT_BLOCKED = "Your account is suspended — changes are not possible. You can still contact support."
    const val RATE_LIMITED = "Too many requests — please wait a moment and try again."
    const val SERVER_ERROR = "Server error — please try again later."
    const val UNAVAILABLE = "Service temporarily unavailable — please try again later."
    const val OFFLINE = "No connection — check your internet connection and try again."
    const val TIMEOUT = "The server is not responding — please try again."
    const val LOGIN_FAILED = "Login failed — please try again."
    const val CREATE_FAILED = "Your account could not be created — please try again."

    /** For `POST /auth/login` and `POST /auth/confirm-registration`. */
    fun login(failure: ApiResult<*>): String = message(failure, LOGIN_FAILED)

    /** For `POST /auth/account-number`. */
    fun createAccount(failure: ApiResult<*>): String = message(failure, CREATE_FAILED)

    private fun message(failure: ApiResult<*>, fallback: String): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> TIMEOUT
            NetworkErrorKind.Unreachable -> OFFLINE
        }

        is ApiResult.HttpError -> byCode(failure.authErrorCode()) ?: byStatus(failure.status) ?: fallback

        is ApiResult.Success -> fallback
    }

    private fun byCode(code: ErrorCode?): String? = when (code) {
        ErrorCode.UNAUTHORIZED -> WRONG_NUMBER

        ErrorCode.INVALID_REQUEST -> INVALID_NUMBER

        ErrorCode.REGISTRATION_NOT_CONFIRMED -> NOT_CONFIRMED

        ErrorCode.DEVICE_LIMIT_REACHED -> DEVICE_LIMIT

        ErrorCode.ACCOUNT_BLOCKED -> ACCOUNT_BLOCKED

        ErrorCode.RATE_LIMITED -> RATE_LIMITED

        ErrorCode.INTERNAL_ERROR -> SERVER_ERROR

        // Not returned by the endpoints of these flows; the status decides.
        ErrorCode.INVALID_DEVICE_ID,
        ErrorCode.INVALID_ACCOUNT_ID,
        ErrorCode.FORBIDDEN,
        ErrorCode.NOT_FOUND,
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
        status == HTTP_TOO_MANY_REQUESTS -> RATE_LIMITED
        status == HTTP_SERVICE_UNAVAILABLE -> UNAVAILABLE
        status >= HTTP_SERVER_ERROR -> SERVER_ERROR
        else -> null
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503
}
