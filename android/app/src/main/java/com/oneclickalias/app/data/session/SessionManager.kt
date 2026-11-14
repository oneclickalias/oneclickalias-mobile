package com.oneclickalias.app.data.session

import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.contracts.auth.AccountStatus
import com.oneclickalias.contracts.auth.LoginResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns this device's session: signs in, hands out valid access tokens to API
 * calls, rotates them, and signs out. The single source of [state] for
 * navigation.
 *
 * Refresh rules (the server rotates the refresh token on every use and revokes
 * the whole token family when a spent token comes back after a ~30 s grace
 * window, so a duplicate refresh must never happen):
 * - **Proactive:** a token expiring within [EXPIRY_MARGIN_MILLIS] is refreshed
 *   before the call. **Reactive:** a `401` triggers one refresh and one retry.
 * - **Single flight:** all refreshes run under one [Mutex]. Callers that waited
 *   see the pair the first caller fetched and reuse it. The new pair is stored
 *   before the lock is released, so no call ever continues with a token that is
 *   not persisted yet.
 * - Refresh, persisting and logout run [NonCancellable]: a caller leaving the
 *   screen must not drop a rotated pair the server already issued.
 * - `400`/`401` from refresh (expired, reused, device removed) ends the session
 *   with [SignOutNotice.SessionEnded]. Network and server errors keep it.
 * - No refresh at app start or on the way to the foreground: the AMS checks
 *   every access token against the device list (introspection), so a device
 *   signed out elsewhere gets a `401` on the next API call, and the reactive
 *   refresh above ends the session. Screens reload their data when they start.
 *
 * @param clearAccountData deletes per-account data outside the session (e.g.
 *   the remembered recipient) whenever the session ends, by logout or server.
 * @param beforeLogout runs at the start of [logout] while the session still
 *   works (delivers copied aliases that wait for their claim); bounded by the caller.
 */
class SessionManager(
    private val authApi: AuthApi,
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val clearAccountData: suspend () -> Unit = {},
    private val beforeLogout: suspend () -> Unit = {}
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    /** Reads the stored session once at app start. */
    suspend fun restore() {
        val restored = mutex.withLock { store.read().also { session = it } }
        mutableState.value = if (restored != null) SessionState.SignedIn else SessionState.SignedOut()
    }

    /** `POST /auth/login`; on success the session is stored and [state] becomes signed in. */
    suspend fun signIn(accountNumber: String): ApiResult<Unit> = withContext(NonCancellable) {
        // Not cancellable: once the server registered the device, its tokens must be kept.
        when (val result = authApi.login(accountNumber)) {
            is ApiResult.Success -> {
                start(result.value)
                ApiResult.Success(Unit)
            }

            is ApiResult.HttpError -> result

            is ApiResult.NetworkError -> result
        }
    }

    /**
     * QR login: `POST /auth/pairing/token` with the pickup secret from the claim.
     * After the approval in the dashboard the answer is a token pair, stored
     * exactly like one from [signIn]; before it, `400 pairing_pending` (and the
     * other pairing errors) come back unchanged and nothing is stored.
     */
    suspend fun signInWithPairing(pickupSecret: String): ApiResult<Unit> = withContext(NonCancellable) {
        // Not cancellable: the first successful pickup completes the pairing on the
        // server, so a pair it hands out is the only one this device will ever get.
        when (val result = authApi.pickUpPairingTokens(pickupSecret)) {
            is ApiResult.Success -> {
                start(result.value)
                ApiResult.Success(Unit)
            }

            is ApiResult.HttpError -> result

            is ApiResult.NetworkError -> result
        }
    }

    /** `GET /auth/account`: whether writes to the AMS will pass its paid gate. */
    suspend fun accountStatus(): ApiResult<AccountStatus> = authorized { authApi.accountStatus(it) }

    /** Confirms the registration (idempotent), then signs in. */
    suspend fun confirmAndSignIn(accountNumber: String): ApiResult<Unit> =
        when (val result = authApi.confirmRegistration(accountNumber)) {
            is ApiResult.Success -> signIn(accountNumber)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }

    /**
     * Runs [block] with a valid access token, refreshing first if it is about to
     * expire and once more after a `401`. Every authenticated client (Auth
     * devices, AMS) goes through here.
     */
    suspend fun <T> authorized(block: suspend (accessToken: String) -> ApiResult<T>): ApiResult<T> {
        var current = session ?: return NOT_SIGNED_IN
        if (current.expiresSoon()) {
            current = when (val outcome = refresh(current)) {
                is RefreshOutcome.Refreshed -> outcome.session
                is RefreshOutcome.Failed -> return outcome.result
                RefreshOutcome.Ended -> return NOT_SIGNED_IN
            }
        }
        val result = block(current.accessToken)
        if (result !is ApiResult.HttpError || result.status != HTTP_UNAUTHORIZED) return result
        return when (val outcome = refresh(current)) {
            is RefreshOutcome.Refreshed -> block(outcome.session.accessToken)
            is RefreshOutcome.Failed -> outcome.result
            RefreshOutcome.Ended -> NOT_SIGNED_IN
        }
    }

    /**
     * Signs this device out on the server (`DELETE /devices/{id}`) and deletes
     * the local session. Local data is deleted even when the server cannot be
     * reached; [state] then carries [SignOutNotice.SignedOutLocally], because the
     * device stays in the account's device list until it is removed elsewhere or
     * its refresh token expires.
     */
    suspend fun logout(): Unit = withContext(NonCancellable) {
        val current = session
        if (current == null) {
            mutableState.value = SessionState.SignedOut()
            return@withContext
        }
        beforeLogout()
        val result = authorized { authApi.deleteDevice(current.deviceId, it) }
        // 401: the session was already dead; 404: the device was already removed.
        val serverDone = result is ApiResult.Success ||
            (result is ApiResult.HttpError && result.status in setOf(HTTP_UNAUTHORIZED, HTTP_NOT_FOUND))
        mutex.withLock { endLocked(if (serverDone) null else SignOutNotice.SignedOutLocally) }
    }

    /**
     * `POST /auth/devices/revoke-all`: signs out every device of the account
     * that [accountNumber] belongs to, this one included. No token needed, so it
     * works signed out too (device limit at login). A session on this device
     * ends with [SignOutNotice.AllDevicesSignedOut]; on failure it stays.
     *
     * The number may belong to another account than this session's. So after
     * success this device also signs itself out with its own access token
     * (no refresh, result ignored: with the own number the device is gone
     * already and the call fails harmlessly), then the local data is deleted.
     */
    suspend fun revokeAllDevices(accountNumber: String): ApiResult<Unit> = withContext(NonCancellable) {
        val result = authApi.revokeAllDevices(accountNumber)
        if (result is ApiResult.Success) {
            mutex.withLock {
                val current = session ?: return@withLock
                authApi.deleteDevice(current.deviceId, current.accessToken)
                endLocked(SignOutNotice.AllDevicesSignedOut)
            }
        }
        result
    }

    /** Device limit at login: signs out every device of the account, then logs in on this one. */
    suspend fun revokeAllAndSignIn(accountNumber: String): ApiResult<Unit> =
        when (val result = revokeAllDevices(accountNumber)) {
            is ApiResult.Success -> signIn(accountNumber)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }

    /**
     * `DELETE /auth/account`, with [accountNumber] as re-authentication. The
     * server signs out every device at once, so on success only the local data
     * is deleted ([SignOutNotice.AccountDeletionRequested]); on failure the
     * session stays.
     */
    suspend fun deleteAccount(accountNumber: String): ApiResult<Unit> = withContext(NonCancellable) {
        val result = authorized { authApi.deleteAccount(accountNumber, it) }
        if (result is ApiResult.Success) {
            mutex.withLock { endLocked(SignOutNotice.AccountDeletionRequested) }
        }
        result
    }

    /**
     * Ends the session locally, with [SignOutNotice.SessionEnded], when a
     * successful device list no longer contains this device: it was signed out
     * elsewhere, and its refresh token is gone with it. Auth answers `GET /devices`
     * to the access token for up to 15 minutes after that, so no `401` comes.
     * No logout call: the device does not exist anymore.
     */
    suspend fun endRemovedElsewhere(): Unit = withContext(NonCancellable) {
        mutex.withLock { if (session != null) endLocked(SignOutNotice.SessionEnded) }
    }

    /** The user has seen the sign-out notice. */
    fun clearNotice() {
        mutableState.update { if (it is SessionState.SignedOut && it.notice != null) SessionState.SignedOut() else it }
    }

    private suspend fun start(login: LoginResponse) {
        mutex.withLock {
            val time = now()
            val next = Session(
                accessToken = login.accessToken,
                refreshToken = login.refreshToken,
                deviceId = login.deviceId,
                accessTokenExpiresAtMillis = time + login.expiresIn * MILLIS_PER_SECOND,
                obtainedAtMillis = time
            )
            persist(next)
            session = next
        }
        mutableState.value = SessionState.SignedIn
    }

    private suspend fun refresh(stale: Session): RefreshOutcome = withContext(NonCancellable) {
        mutex.withLock {
            val current = session ?: return@withLock RefreshOutcome.Ended
            // Someone else refreshed while this caller waited for the lock.
            if (current.accessToken != stale.accessToken) return@withLock RefreshOutcome.Refreshed(current)
            when (val result = authApi.refresh(current.refreshToken, current.deviceId)) {
                is ApiResult.Success -> {
                    val time = now()
                    val next = current.copy(
                        accessToken = result.value.accessToken,
                        refreshToken = result.value.refreshToken,
                        accessTokenExpiresAtMillis = time + result.value.expiresIn * MILLIS_PER_SECOND,
                        obtainedAtMillis = time
                    )
                    persist(next)
                    session = next
                    RefreshOutcome.Refreshed(next)
                }

                is ApiResult.HttpError -> if (result.status == HTTP_BAD_REQUEST || result.status == HTTP_UNAUTHORIZED) {
                    endLocked(SignOutNotice.SessionEnded)
                    RefreshOutcome.Ended
                } else {
                    RefreshOutcome.Failed(result)
                }

                is ApiResult.NetworkError -> RefreshOutcome.Failed(result)
            }
        }
    }

    /** Must hold [mutex]. */
    private suspend fun endLocked(notice: SignOutNotice?) {
        session = null
        try {
            store.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing more to do; the in-memory session is gone either way.
        }
        try {
            clearAccountData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort, like the store above; it holds nothing secret.
        }
        mutableState.value = SessionState.SignedOut(notice)
    }

    private suspend fun persist(next: Session) {
        try {
            store.write(next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore or disk failure: keep the session in memory for this run.
            // The blob on disk may still hold the previous pair, whose refresh
            // token the server has just spent; delete it so a restart starts
            // cleanly signed out instead of presenting a spent token.
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing more to do: a spent token is rejected and ends the session.
            }
        }
    }

    private fun Session.expiresSoon(): Boolean = accessTokenExpiresAtMillis - now() <= EXPIRY_MARGIN_MILLIS

    private sealed interface RefreshOutcome {
        data class Refreshed(val session: Session) : RefreshOutcome

        data class Failed(val result: ApiResult<Nothing>) : RefreshOutcome

        data object Ended : RefreshOutcome
    }

    companion object {
        /** Refresh this long before the access token expires. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L

        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private val NOT_SIGNED_IN = ApiResult.HttpError(HTTP_UNAUTHORIZED, "unauthorized")
    }
}
