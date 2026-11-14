package com.oneclickalias.app.data.pairing

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.contracts.auth.ErrorCode
import kotlinx.coroutines.delay

/** A pairing lives at most this long (server deadlines: 120 + 120 + 60 s). */
const val PAIRING_MAX_LIFETIME_MILLIS = 300_000L

/** `pairing_slow_down` raises the interval by this much for the rest of the pairing (RFC 8628 §3.5). */
private const val SLOW_DOWN_STEP_MILLIS = 5_000L

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Waits for the approval in the dashboard after a successful claim
 * (docs/flows.md, "Log in with a code"): calls [pickUp] every
 * [pollIntervalSeconds], starting one interval after the claim, until an
 * answer comes that is neither `pairing_pending` nor `pairing_slow_down`.
 *
 * - `pairing_slow_down` adds 5 s to the interval for good.
 * - Network errors and `429` keep polling until [remainingMillis] (the
 *   claim's `expiresAt`) has passed; then the last error is returned.
 * - `pairing_pending` keeps polling past that deadline, since the server
 *   knows when the pairing really ends (the approved state has its own
 *   deadline), but never past [PAIRING_MAX_LIFETIME_MILLIS].
 * - Anything else ends polling and is returned: success (signed in), the
 *   final pairing errors, `401` and other errors.
 *
 * Cancelling the caller stops polling (the screen was left).
 *
 * @param clock monotonic milliseconds, so a changed phone clock cannot shorten or stretch the wait.
 */
suspend fun awaitPairingApproval(
    pickUp: suspend () -> ApiResult<Unit>,
    pollIntervalSeconds: Int,
    remainingMillis: Long,
    clock: () -> Long,
    sleep: suspend (Long) -> Unit = { delay(it) }
): ApiResult<Unit> {
    val start = clock()
    val deadline = start + remainingMillis
    var interval = pollIntervalSeconds.coerceAtLeast(1) * MILLIS_PER_SECOND
    while (true) {
        sleep(interval)
        val result = pickUp()
        val code = (result as? ApiResult.HttpError)?.authErrorCode()
        val now = clock()
        when {
            code == ErrorCode.PAIRING_PENDING || code == ErrorCode.PAIRING_SLOW_DOWN -> {
                if (now - start >= PAIRING_MAX_LIFETIME_MILLIS) return result
                if (code == ErrorCode.PAIRING_SLOW_DOWN) interval += SLOW_DOWN_STEP_MILLIS
            }

            result.isTransient() -> if (now >= deadline) return result

            else -> return result
        }
    }
}

private fun ApiResult<*>.isTransient(): Boolean =
    this is ApiResult.NetworkError || (this is ApiResult.HttpError && status == HTTP_TOO_MANY_REQUESTS)
