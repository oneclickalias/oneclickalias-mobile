package com.oneclickalias.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.NetworkErrorKind
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.app.data.pairing.LoginRequestQr
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.ui.screens.AuthMessages
import com.oneclickalias.contracts.auth.ErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The steps of signing in a browser from the app. */
sealed interface BrowserSignInUiState {
    /** Camera on; [problem] says why the last scan did not work. */
    data class Scanning(val problem: String? = null) : BrowserSignInUiState

    data object Claiming : BrowserSignInUiState

    /** Compare [deviceName] with the browser, then approve or deny. */
    data class Confirm(val pairingId: String, val deviceName: String, val working: Boolean = false) :
        BrowserSignInUiState

    data class Done(val deviceName: String) : BrowserSignInUiState

    data object Denied : BrowserSignInUiState

    /** The request cannot go on (expired, used, device limit…); scan a new code. */
    data class Failed(val message: String) : BrowserSignInUiState
}

object BrowserSignInMessages {
    const val NOT_OURS = "This is not a OneClickAlias sign-in code."
    const val APP_CODE =
        "This code signs in the app. To sign in a browser, open oneclickalias.com there and choose " +
            "“Sign in with your phone”."
    const val UPDATE_APP = "Update the app — this sign-in code is from a newer version of OneClickAlias."
    const val EXPIRED = "This code has expired. Show a new code in your browser and scan it again."
    const val USED = "This code was already used. Show a new code in your browser."
    const val DENIED = "This sign-in was denied."
    const val DEVICE_LIMIT = "All device slots are in use — sign out a device under Devices, then try again."
    const val FAILED = "Signing in the browser failed — please try again."

    fun error(failure: ApiResult<*>): String = when (failure) {
        is ApiResult.NetworkError -> when (failure.kind) {
            NetworkErrorKind.Timeout -> AuthMessages.TIMEOUT
            NetworkErrorKind.Unreachable -> AuthMessages.OFFLINE
        }

        is ApiResult.HttpError -> when (failure.authErrorCode()) {
            ErrorCode.PAIRING_EXPIRED -> EXPIRED
            ErrorCode.PAIRING_USED -> USED
            ErrorCode.PAIRING_DENIED -> DENIED
            ErrorCode.DEVICE_LIMIT_REACHED -> DEVICE_LIMIT
            else -> FAILED
        }

        is ApiResult.Success -> FAILED
    }
}

/**
 * Signs in a browser that shows a code (docs/flows.md, "Sign in a
 * browser"): scan, compare the name, approve. Approving needs the
 * phone's screen lock when app lock is on ([confirmIdentity]). Leaving the
 * screen before deciding denies the request, so it cannot be approved later.
 *
 * @param confirmIdentity true when the user may approve (no app lock, or the prompt succeeded).
 * @param leaveScope outlives this ViewModel; the deny on leaving runs there.
 */
class BrowserSignInViewModel(
    private val auth: AuthApi,
    private val sessions: SessionManager,
    private val confirmIdentity: suspend () -> Boolean,
    private val leaveScope: CoroutineScope
) : ViewModel() {
    private val mutableState = MutableStateFlow<BrowserSignInUiState>(BrowserSignInUiState.Scanning())
    val state: StateFlow<BrowserSignInUiState> = mutableState.asStateFlow()

    fun onScanned(text: String) {
        if (mutableState.value !is BrowserSignInUiState.Scanning) return
        val code = when (val qr = LoginRequestQr.parse(text)) {
            is LoginRequestQr.Valid -> qr.code
            LoginRequestQr.AppSignInCode -> return scanProblem(BrowserSignInMessages.APP_CODE)
            LoginRequestQr.UnsupportedVersion -> return scanProblem(BrowserSignInMessages.UPDATE_APP)
            LoginRequestQr.NotOurs -> return scanProblem(BrowserSignInMessages.NOT_OURS)
        }
        mutableState.value = BrowserSignInUiState.Claiming
        viewModelScope.launch {
            val result = sessions.authorized { auth.claimLoginRequest(code, it) }
            mutableState.value = if (result is ApiResult.Success) {
                BrowserSignInUiState.Confirm(result.value.pairingId, result.value.deviceName)
            } else {
                BrowserSignInUiState.Failed(BrowserSignInMessages.error(result))
            }
        }
    }

    private fun scanProblem(text: String) {
        mutableState.value = BrowserSignInUiState.Scanning(problem = text)
    }

    fun approve() {
        val confirm = mutableState.value as? BrowserSignInUiState.Confirm ?: return
        if (confirm.working) return
        mutableState.value = confirm.copy(working = true)
        viewModelScope.launch {
            if (!confirmIdentity()) {
                mutableState.value = confirm.copy(working = false)
                return@launch
            }
            val result = sessions.authorized { auth.approvePairing(confirm.pairingId, it) }
            mutableState.value = if (result is ApiResult.Success) {
                BrowserSignInUiState.Done(confirm.deviceName)
            } else {
                BrowserSignInUiState.Failed(BrowserSignInMessages.error(result))
            }
        }
    }

    fun deny() {
        val confirm = mutableState.value as? BrowserSignInUiState.Confirm ?: return
        if (confirm.working) return
        mutableState.value = confirm.copy(working = true)
        viewModelScope.launch {
            sessions.authorized { auth.denyPairing(confirm.pairingId, it) }
            // Denied either way: a failed deny ends by its deadline.
            mutableState.value = BrowserSignInUiState.Denied
        }
    }

    fun scanAgain() {
        mutableState.value = BrowserSignInUiState.Scanning()
    }

    override fun onCleared() {
        val confirm = mutableState.value as? BrowserSignInUiState.Confirm ?: return
        if (confirm.working) return
        leaveScope.launch { sessions.authorized { auth.denyPairing(confirm.pairingId, it) } }
    }
}
