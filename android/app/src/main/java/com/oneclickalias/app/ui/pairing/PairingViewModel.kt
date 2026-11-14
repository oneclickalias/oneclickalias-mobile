package com.oneclickalias.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.pairing.PAIRING_MAX_LIFETIME_MILLIS
import com.oneclickalias.app.data.pairing.PairingQr
import com.oneclickalias.app.data.pairing.awaitPairingApproval
import com.oneclickalias.app.data.session.SessionManager
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the QR-login screen shows. Holds no code and no pickup secret. */
sealed interface PairingUiState {
    /** Camera on; [problem] says why the last scanned text was not taken. */
    data class Scanning(val problem: String? = null) : PairingUiState

    /** A valid code was scanned (camera off), `claim` is running. */
    data object Claiming : PairingUiState

    /** Claimed: show [deviceName] and wait for the approval in the dashboard. */
    data class Waiting(val deviceName: String) : PairingUiState

    data class Failed(val message: String) : PairingUiState
}

/**
 * QR login (docs/flows.md, "Log in with a code"):
 * scanned text → [PairingQr] → `claim` → poll `token` until the dashboard
 * decides. On approval [SessionManager.signInWithPairing] stores the pair like
 * a login, and navigation follows the session state. The pickup secret lives
 * only in the polling coroutine; clearing this ViewModel (leaving the screen)
 * stops polling.
 *
 * @param wallClock for the claim's `expiresAt` (absolute server time).
 * @param clock monotonic milliseconds for the polling deadline.
 */
class PairingViewModel(
    private val authApi: AuthApi,
    private val sessions: SessionManager,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) : ViewModel() {
    private val mutableState = MutableStateFlow<PairingUiState>(PairingUiState.Scanning())
    val state: StateFlow<PairingUiState> = mutableState.asStateFlow()

    /** Text recognised in a camera frame (or, in debug builds, injected over adb). */
    fun onScanned(text: String) {
        if (mutableState.value !is PairingUiState.Scanning) return
        when (val qr = PairingQr.parse(text)) {
            PairingQr.NotOurs -> mutableState.value = PairingUiState.Scanning(PairingMessages.NOT_OURS)

            PairingQr.UnsupportedVersion -> mutableState.value = PairingUiState.Scanning(PairingMessages.UPDATE_APP)

            is PairingQr.Valid -> {
                mutableState.value = PairingUiState.Claiming
                viewModelScope.launch { claimAndWait(qr.code) }
            }
        }
    }

    fun scanAgain() {
        if (mutableState.value is PairingUiState.Failed) mutableState.value = PairingUiState.Scanning()
    }

    private suspend fun claimAndWait(code: String) {
        val claim = when (val result = authApi.claimPairing(code)) {
            is ApiResult.Success -> result.value
            else -> return fail(result)
        }
        mutableState.value = PairingUiState.Waiting(claim.deviceName)
        val secret = claim.pickupSecret
        val result = awaitPairingApproval(
            pickUp = { sessions.signInWithPairing(secret) },
            pollIntervalSeconds = claim.pollInterval,
            remainingMillis = remainingMillis(claim.expiresAt),
            clock = clock,
            sleep = sleep
        )
        // Success: the session is stored and navigation leaves this screen.
        if (result !is ApiResult.Success) fail(result)
    }

    private fun remainingMillis(expiresAt: String): Long {
        val end = runCatching { OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli() }.getOrNull()
            ?: return DEFAULT_CLAIM_WINDOW_MILLIS
        return (end - wallClock()).coerceIn(0, PAIRING_MAX_LIFETIME_MILLIS)
    }

    private fun fail(result: ApiResult<*>) {
        mutableState.value = PairingUiState.Failed(PairingMessages.error(result))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** Claimed state lasts 120 s (contract); used if `expiresAt` cannot be read. */
        const val DEFAULT_CLAIM_WINDOW_MILLIS = 120_000L
    }
}
