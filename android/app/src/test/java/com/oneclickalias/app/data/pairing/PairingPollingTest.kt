package com.oneclickalias.app.data.pairing

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.NetworkErrorKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PairingPollingTest {
    private val pending = ApiResult.HttpError(400, "pairing_pending")
    private val slowDown = ApiResult.HttpError(400, "pairing_slow_down")
    private val offline = ApiResult.NetworkError(NetworkErrorKind.Unreachable)
    private val rateLimited = ApiResult.HttpError(429, "rate_limited")

    /** Answers in order; the last answer repeats. Records the virtual time of every call. */
    private class FakePickUp(private val scope: TestScope, vararg answers: ApiResult<Unit>) {
        private val queue = ArrayDeque(answers.toList())
        val times = mutableListOf<Long>()

        suspend fun invoke(): ApiResult<Unit> {
            times += scope.currentTime
            return if (queue.size > 1) queue.removeFirst() else queue.first()
        }
    }

    private suspend fun TestScope.poll(pickUp: FakePickUp, intervalSeconds: Int = 2, remainingMillis: Long = 120_000) =
        awaitPairingApproval(
            pickUp = pickUp::invoke,
            pollIntervalSeconds = intervalSeconds,
            remainingMillis = remainingMillis,
            clock = { currentTime }
        )

    @Test
    fun `waits one interval, polls while pending, ends signed in`() = runTest {
        val pickUp = FakePickUp(this, pending, pending, ApiResult.Success(Unit))

        val result = poll(pickUp)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(listOf(2_000L, 4_000L, 6_000L), pickUp.times)
    }

    @Test
    fun `slow down adds five seconds for the rest of the pairing`() = runTest {
        val pickUp = FakePickUp(this, pending, slowDown, pending, pending, ApiResult.Success(Unit))

        poll(pickUp)

        assertEquals(listOf(2_000L, 4_000L, 11_000L, 18_000L, 25_000L), pickUp.times)
    }

    @Test
    fun `final answers end polling`() = runTest {
        for (code in listOf("pairing_denied", "pairing_expired", "pairing_used", "invalid_request")) {
            val pickUp = FakePickUp(this, pending, ApiResult.HttpError(400, code), pending)
            assertEquals(ApiResult.HttpError(400, code), poll(pickUp))
            assertEquals(2, pickUp.times.size)
        }
        val unknownSecret = FakePickUp(this, ApiResult.HttpError(401, "unauthorized"), pending)
        assertEquals(ApiResult.HttpError(401, "unauthorized"), poll(unknownSecret))
        val serverError = FakePickUp(this, ApiResult.HttpError(500, "internal_error"), pending)
        assertEquals(ApiResult.HttpError(500, "internal_error"), poll(serverError))
    }

    @Test
    fun `network errors and 429 keep polling until the deadline`() = runTest {
        val pickUp = FakePickUp(this, offline, rateLimited, offline, ApiResult.Success(Unit))

        assertEquals(ApiResult.Success(Unit), poll(pickUp))
        assertEquals(4, pickUp.times.size)
    }

    @Test
    fun `network errors past the deadline give up with the last error`() = runTest {
        val pickUp = FakePickUp(this, offline)

        val result = poll(pickUp, remainingMillis = 9_000)

        assertEquals(offline, result)
        // 2, 4, 6, 8 s are before the deadline; the call at 10 s is past it.
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L, 10_000L), pickUp.times)
    }

    @Test
    fun `pending past the deadline keeps polling, the server decides`() = runTest {
        val pickUp = FakePickUp(this, pending, pending, pending, ApiResult.Success(Unit))

        assertEquals(ApiResult.Success(Unit), poll(pickUp, remainingMillis = 3_000))
    }

    @Test
    fun `pending never ends after the longest life of a pairing`() = runTest {
        val pickUp = FakePickUp(this, pending)

        val result = poll(pickUp)

        assertEquals(pending, result)
        assertEquals(PAIRING_MAX_LIFETIME_MILLIS, pickUp.times.last())
    }

    @Test
    fun `cancelling stops polling`() = runTest {
        val pickUp = FakePickUp(this, pending)
        val job = async(start = CoroutineStart.UNDISPATCHED) { poll(pickUp) }
        advanceTimeBy(5_000)
        runCurrent()

        job.cancel()
        advanceTimeBy(60_000)

        assertEquals(2, pickUp.times.size)
        assertFalse(job.isActive)
    }
}
