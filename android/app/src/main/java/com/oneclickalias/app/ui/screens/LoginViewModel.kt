package com.oneclickalias.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.ui.account.AccountNumberFormat
import com.oneclickalias.contracts.auth.ErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property digits the typed account number, digits only. Kept here rather than
 *   in `rememberSaveable`, so it never lands in the saved-instance Bundle.
 * @property needsConfirmation the server said `registration_not_confirmed`:
 *   the user must confirm having saved the number before logging in.
 * @property deviceLimitReached the server said `device_limit_reached`: offer
 *   to sign out all devices with this number and log in, as the website does.
 */
data class LoginUiState(
    val digits: String = "",
    val revealed: Boolean = false,
    val needsConfirmation: Boolean = false,
    val confirmed: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    val deviceLimitReached: Boolean = false
) {
    val canSubmit: Boolean
        get() = AccountNumberFormat.isComplete(digits) && !submitting && !signedIn &&
            (!needsConfirmation || confirmed)

    override fun toString(): String = "LoginUiState(digits=<redacted>, revealed=$revealed, " +
        "needsConfirmation=$needsConfirmation, confirmed=$confirmed, submitting=$submitting, " +
        "error=$error, signedIn=$signedIn, deviceLimitReached=$deviceLimitReached)"
}

class LoginViewModel(private val sessionManager: SessionManager) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun onInput(raw: String) = mutableState.update {
        // The device-limit offer belongs to the number it was made for.
        it.copy(digits = AccountNumberFormat.sanitize(raw), error = null, deviceLimitReached = false)
    }

    fun toggleReveal() = mutableState.update { it.copy(revealed = !it.revealed) }

    fun setConfirmed(confirmed: Boolean) = mutableState.update { it.copy(confirmed = confirmed, error = null) }

    /** Logs in (confirming first if the server asked for it); navigation follows the session state. */
    fun submit() {
        val current = mutableState.value
        if (!current.canSubmit) return
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = if (current.needsConfirmation) {
                sessionManager.confirmAndSignIn(current.digits)
            } else {
                sessionManager.signIn(current.digits)
            }
            mutableState.update { state ->
                when {
                    result is ApiResult.Success -> state.copy(signedIn = true)

                    result is ApiResult.HttpError && result.authErrorCode() == ErrorCode.REGISTRATION_NOT_CONFIRMED ->
                        state.copy(
                            submitting = false,
                            needsConfirmation = true,
                            confirmed = false,
                            error = null
                        )

                    result is ApiResult.HttpError && result.authErrorCode() == ErrorCode.DEVICE_LIMIT_REACHED ->
                        state.copy(submitting = false, deviceLimitReached = true, error = null)

                    else -> state.copy(submitting = false, error = AuthMessages.login(result))
                }
            }
        }
    }

    /**
     * Device limit: `POST /auth/devices/revoke-all` with the typed number (signs
     * out every device of the account, also ones out of reach), then logs in on
     * this one.
     */
    fun revokeAllAndLogIn() {
        val current = mutableState.value
        if (!current.deviceLimitReached || !current.canSubmit) return
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = sessionManager.revokeAllAndSignIn(current.digits)
            mutableState.update { state ->
                if (result is ApiResult.Success) {
                    state.copy(signedIn = true, deviceLimitReached = false)
                } else {
                    state.copy(submitting = false, error = AuthMessages.login(result))
                }
            }
        }
    }

    override fun onCleared() {
        mutableState.value = LoginUiState()
    }
}
