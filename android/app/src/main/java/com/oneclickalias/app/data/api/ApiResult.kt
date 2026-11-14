package com.oneclickalias.app.data.api

import com.oneclickalias.contracts.auth.ErrorCode
import kotlinx.serialization.json.JsonPrimitive

/**
 * Outcome of one API call. Every client in the app (Auth now, AMS next) returns
 * this instead of throwing, so callers handle all three cases explicitly.
 */
sealed interface ApiResult<out T> {
    /** 2xx with a body that matched the expected model (or no body for 204). */
    data class Success<T>(val value: T) : ApiResult<T> {
        // Values are API models such as LoginResponse, whose generated toString
        // prints tokens. Results must stay safe to print by accident.
        override fun toString(): String = "Success(<redacted>)"
    }

    /**
     * The server answered with an error status, or with a 2xx body that did not
     * match the model. [code] is the raw `error` string of the `{error, message}`
     * body, or [UNKNOWN_ERROR_CODE] when there was no readable code. `message`
     * is deliberately dropped: the app shows its own texts.
     */
    data class HttpError(val status: Int, val code: String) : ApiResult<Nothing>

    /** No HTTP response at all: no connection, DNS or TLS failure, or a timeout. */
    data class NetworkError(val kind: NetworkErrorKind) : ApiResult<Nothing>

    companion object {
        /** Code of an [HttpError] whose body carried no usable `error` string. */
        const val UNKNOWN_ERROR_CODE = "unknown"
    }
}

enum class NetworkErrorKind { Timeout, Unreachable }

/**
 * The Auth error code of this error, or null when the code is unknown to this
 * app version. The generated [ErrorCode] enum is strict, so the raw string is
 * matched here instead of decoding the whole body with it.
 */
fun ApiResult.HttpError.authErrorCode(): ErrorCode? =
    runCatching { ApiJson.decodeFromJsonElement(ErrorCode.serializer(), JsonPrimitive(code)) }.getOrNull()
