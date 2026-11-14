package com.oneclickalias.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.platform.SecretClipboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreateAccountUiState {
    data object Creating : CreateAccountUiState

    data class CreateFailed(val message: String) : CreateAccountUiState

    /**
     * The screen was recreated after the process died while a number was shown.
     * The number is never persisted, so it is gone; the user decides what next.
     */
    data object Interrupted : CreateAccountUiState

    data class Ready(
        val accountNumber: String,
        val saved: Boolean = false,
        val copied: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null
    ) : CreateAccountUiState {
        val canSubmit: Boolean get() = saved && !submitting

        override fun toString(): String =
            "Ready(accountNumber=<redacted>, saved=$saved, copied=$copied, submitting=$submitting, error=$error)"
    }
}

/**
 * Sign-up: `POST /auth/account-number`, show the number once, then confirm and
 * log in. The account number lives only in this ViewModel's memory: it survives
 * rotation (ViewModel), is dropped when the screen is left (ViewModel cleared)
 * and never goes into [SavedStateHandle] or a Bundle. Only the fact that a
 * number was issued is saved, so a restore after process death shows
 * [CreateAccountUiState.Interrupted] instead of silently creating a second
 * account.
 */
class CreateAccountViewModel(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val clipboard: SecretClipboard,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow<CreateAccountUiState>(CreateAccountUiState.Creating)
    val state: StateFlow<CreateAccountUiState> = mutableState.asStateFlow()
    private var copiedReset: Job? = null

    init {
        if (savedState.get<Boolean>(KEY_NUMBER_ISSUED) == true) {
            mutableState.value = CreateAccountUiState.Interrupted
        } else {
            create()
        }
    }

    fun retry() {
        if (mutableState.value is CreateAccountUiState.CreateFailed) create()
    }

    fun startOver() {
        if (mutableState.value is CreateAccountUiState.Interrupted) create()
    }

    fun setSaved(saved: Boolean) = updateReady { it.copy(saved = saved, error = null) }

    fun copy() {
        val ready = mutableState.value as? CreateAccountUiState.Ready ?: return
        clipboard.copy(CLIP_LABEL, ready.accountNumber)
        updateReady { it.copy(copied = true) }
        copiedReset?.cancel()
        copiedReset = viewModelScope.launch {
            delay(COPIED_FEEDBACK_MILLIS)
            updateReady { it.copy(copied = false) }
        }
    }

    /** Confirms the registration and logs in; navigation follows the session state. */
    fun submit() {
        val ready = mutableState.value as? CreateAccountUiState.Ready ?: return
        if (!ready.canSubmit) return
        updateReady { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = sessionManager.confirmAndSignIn(ready.accountNumber)
            if (result !is ApiResult.Success) {
                updateReady { it.copy(submitting = false, error = AuthMessages.login(result)) }
            }
        }
    }

    private fun create() {
        mutableState.value = CreateAccountUiState.Creating
        viewModelScope.launch {
            mutableState.value = when (val result = authApi.createAccountNumber()) {
                is ApiResult.Success -> {
                    savedState[KEY_NUMBER_ISSUED] = true
                    CreateAccountUiState.Ready(result.value.accountNumber)
                }

                else -> CreateAccountUiState.CreateFailed(AuthMessages.createAccount(result))
            }
        }
    }

    private fun updateReady(change: (CreateAccountUiState.Ready) -> CreateAccountUiState.Ready) {
        mutableState.update { if (it is CreateAccountUiState.Ready) change(it) else it }
    }

    override fun onCleared() {
        // Drop the last reference to the number (JVM strings cannot be wiped).
        mutableState.value = CreateAccountUiState.Creating
    }

    companion object {
        /** Saved-state flag: a number was shown on this screen (the number itself is not saved). */
        const val KEY_NUMBER_ISSUED = "account_number_issued"
        const val CLIP_LABEL = "Account number"
        private const val COPIED_FEEDBACK_MILLIS = 2_000L
    }
}
