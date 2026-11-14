package com.oneclickalias.app.data.lock

import com.oneclickalias.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * What the UI needs to know about app lock.
 *
 * @property ready the setting and the session are known; show nothing before.
 * @property enabled the user turned app lock on (the window is `FLAG_SECURE` then).
 * @property locked the lock screen replaces the app: enabled, signed in, not unlocked.
 * @property canAuthenticate the phone has a screen lock the prompt can use.
 * @property prompting the system prompt is open.
 * @property loggingOut "Log out" from the lock screen is running.
 */
data class AppLockState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val canAuthenticate: Boolean = false,
    val prompting: Boolean = false,
    val loggingOut: Boolean = false
)

/**
 * Decides when the app is locked. A screen guard, not extra encryption: the
 * session key in the Keystore needs no user authentication, so a newly
 * enrolled fingerprint never costs the session.
 *
 * - Locked at every cold start (a new process) and after more than
 *   [BACKGROUND_LOCK_MILLIS] in the background; shorter switches (pasting an
 *   alias, opening a link in the browser) do not lock.
 * - Only while signed in and only when [AppLockSetting] is on.
 * - Locking opens the prompt once by itself, as soon as the activity is in the
 *   foreground; after a cancel the lock screen stays and [unlock] reopens it.
 * - A phone that lost its screen lock stays locked and only offers [logOut].
 *
 * Main thread only: [onForeground]/[onBackground] come from the activity's
 * lifecycle, the rest from the UI; [scope] runs on the main dispatcher.
 *
 * @param clock monotonic milliseconds (`SystemClock.elapsedRealtime`).
 * @param logout the normal logout (`SessionManager.logout`); its session end
 *   clears [setting] through `clearAccountData`.
 */
class AppLock(
    private val setting: AppLockSetting,
    private val authenticator: DeviceAuthenticator,
    sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
    private val logout: suspend () -> Unit,
    private val clock: () -> Long
) {
    private val mutableState = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    /** null until read. */
    private var enabled: Boolean? = null

    /** null while the session is still loading. */
    private var signedIn: Boolean? = null

    /** A new process starts locked; it only shows when enabled and signed in. */
    private var locked = true
    private var autoPromptDue = true
    private var foreground = false
    private var backgroundSince: Long? = null
    private var prompting = false
    private var loggingOut = false
    private var canAuthenticate = authenticator.canAuthenticate()

    init {
        scope.launch {
            combine(setting.enabled, sessionState) { on, session -> on to session }.collect { (on, session) ->
                enabled = on
                signedIn = when (session) {
                    SessionState.Loading -> null
                    SessionState.SignedIn -> true
                    is SessionState.SignedOut -> false
                }
                update()
            }
        }
    }

    /** The activity started (cold start or back from the background). */
    fun onForeground() {
        if (foreground) return
        foreground = true
        val since = backgroundSince
        backgroundSince = null
        if (since != null && clock() - since > BACKGROUND_LOCK_MILLIS) lock()
        // The screen lock may have been set up or removed meanwhile.
        canAuthenticate = authenticator.canAuthenticate()
        update()
    }

    /** The activity stopped. Time in the prompt (its own activity before Android 10) does not count. */
    fun onBackground() {
        if (!foreground) return
        foreground = false
        if (!prompting) backgroundSince = clock()
    }

    /** "Unlock" on the lock screen. */
    fun unlock() {
        if (mutableState.value.locked && !prompting && !loggingOut) prompt(AuthPurpose.Unlock)
    }

    /** The switch on the account screen: on needs a successful prompt, off needs none. */
    fun setEnabled(on: Boolean) {
        if (prompting) return
        if (!on) {
            locked = false
            save(false)
            return
        }
        canAuthenticate = authenticator.canAuthenticate()
        if (canAuthenticate) prompt(AuthPurpose.Enable) else update()
    }

    /** "Log out" on the lock screen: the normal logout. */
    fun logOut() {
        if (loggingOut) return
        loggingOut = true
        update()
        scope.launch {
            try {
                logout()
            } finally {
                loggingOut = false
                update()
            }
        }
    }

    private fun lock() {
        locked = true
        autoPromptDue = true
    }

    private fun prompt(purpose: AuthPurpose) {
        if (!canAuthenticate) return
        prompting = true
        update()
        scope.launch {
            val confirmed = try {
                authenticator.authenticate(purpose)
            } finally {
                prompting = false
            }
            if (confirmed) {
                when (purpose) {
                    AuthPurpose.Unlock -> locked = false

                    AuthPurpose.Enable -> save(true)

                    // Not asked by AppLock (the browser sign-in asks itself).
                    AuthPurpose.ApproveSignIn -> Unit
                }
            } else {
                // A cancel, or the screen lock is gone: the lock screen tells which.
                canAuthenticate = authenticator.canAuthenticate()
            }
            update()
        }
    }

    private fun save(on: Boolean) {
        scope.launch {
            try {
                setting.set(on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Disk failure: the switch keeps showing the stored value.
            }
        }
    }

    private fun update() {
        val on = enabled
        val session = signedIn
        if (on == null || session == null) {
            mutableState.value = AppLockState(canAuthenticate = canAuthenticate, prompting = prompting)
            return
        }
        if (!on || !session) {
            // Nothing to guard; after the next login (or turning it on) the app starts unlocked.
            locked = false
            autoPromptDue = false
        }
        val showLock = on && session && locked
        if (showLock && autoPromptDue && foreground && !prompting) {
            autoPromptDue = false
            if (canAuthenticate) {
                // prompt() publishes the state itself.
                prompt(AuthPurpose.Unlock)
                return
            }
        }
        mutableState.value = AppLockState(
            ready = true,
            enabled = on,
            locked = showLock,
            canAuthenticate = canAuthenticate,
            prompting = prompting,
            loggingOut = loggingOut
        )
    }

    companion object {
        /** Longer than this in the background locks the app. */
        const val BACKGROUND_LOCK_MILLIS = 60_000L
    }
}
