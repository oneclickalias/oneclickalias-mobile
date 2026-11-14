package com.oneclickalias.app.data.aliases

import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.contracts.ams.ClaimResult
import com.oneclickalias.contracts.ams.ErrorCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What became of one claim. */
sealed interface ClaimOutcome {
    /** The alias is active; [result] carries the next ready alias when the server made one. */
    data class Claimed(val result: ClaimResult) : ClaimOutcome

    /** The server no longer has it (its recipient was deleted, or it was cleaned up): dropped. */
    data object Gone : ClaimOutcome

    /** Not delivered (offline, server error, unpaid, rate limit): stays pending, delivery is scheduled. */
    data class Failed(val failure: ApiResult<*>) : ClaimOutcome
}

/**
 * Delivers the claims of copied ready aliases (docs/flows.md, "New alias
 * tab"). A copy puts the alias ID in [pending] before the
 * address reaches the clipboard; this class then tells the server. What fails
 * stays pending and is delivered later: by [scheduleBackground] (WorkManager,
 * as soon as the phone is online), at every load of the Copy tab and before a
 * logout. One claim at a time, so the background worker and the screen never
 * send the same claim twice in parallel.
 */
class ClaimDelivery(
    private val ams: AmsApi,
    private val pending: PendingClaims,
    private val scheduleBackground: () -> Unit
) {
    private val mutex = Mutex()

    suspend fun pendingIds(): Set<String> = pending.all()

    /** Claims [id]; it must already be in [pending]. */
    suspend fun claim(id: String): ClaimOutcome = mutex.withLock { claimLocked(id) }

    /** Every pending claim, oldest first does not matter; the outcome per ID. */
    suspend fun deliverAll(): Map<String, ClaimOutcome> = mutex.withLock {
        pending.all().associateWith { claimLocked(it) }
    }

    private suspend fun claimLocked(id: String): ClaimOutcome {
        val result = ams.claimReadyAlias(id)
        return when {
            result is ApiResult.Success -> {
                pending.remove(id)
                ClaimOutcome.Claimed(result.value)
            }

            result is ApiResult.HttpError &&
                result.amsErrorCode() in setOf(ErrorCode.NOT_FOUND, ErrorCode.INVALID_ALIAS_ID) -> {
                pending.remove(id)
                ClaimOutcome.Gone
            }

            else -> {
                scheduleBackground()
                ClaimOutcome.Failed(result)
            }
        }
    }
}
