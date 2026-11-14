package com.oneclickalias.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.ui.account.AccountNumberFormat
import com.oneclickalias.app.ui.devices.AccountAction
import com.oneclickalias.app.ui.devices.AccountMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property digits the typed account number, digits only; kept here, never in
 *   saved instance state, and never printed.
 */
data class NumberConfirmUiState(
    val digits: String = "",
    val revealed: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = AccountNumberFormat.isComplete(digits) && !submitting

    override fun toString(): String =
        "NumberConfirmUiState(digits=<redacted>, revealed=$revealed, submitting=$submitting, error=$error)"
}

/**
 * A step the account number confirms, like on the website (it is the
 * credential of revoke-all and the re-authentication of account deletion).
 * On success the session ends and navigation follows the session state.
 */
class NumberConfirmViewModel(
    private val action: suspend (accountNumber: String) -> ApiResult<Unit>,
    private val accountAction: AccountAction
) : ViewModel() {
    private val mutableState = MutableStateFlow(NumberConfirmUiState())
    val state: StateFlow<NumberConfirmUiState> = mutableState.asStateFlow()

    fun onInput(raw: String) = mutableState.update { it.copy(digits = AccountNumberFormat.sanitize(raw), error = null) }

    fun toggleReveal() = mutableState.update { it.copy(revealed = !it.revealed) }

    fun submit() {
        val current = mutableState.value
        if (!current.canSubmit) return
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = action(current.digits)
            if (result !is ApiResult.Success) {
                mutableState.update {
                    it.copy(submitting = false, error = AccountMessages.error(result, accountAction))
                }
            }
        }
    }

    override fun onCleared() {
        mutableState.value = NumberConfirmUiState()
    }

    companion object {
        /** "Sign out all devices", this one included. */
        fun revokeAll(sessions: SessionManager) =
            NumberConfirmViewModel(sessions::revokeAllDevices, AccountAction.RevokeAll)

        /** "Delete account". */
        fun deleteAccount(sessions: SessionManager) =
            NumberConfirmViewModel(sessions::deleteAccount, AccountAction.DeleteAccount)
    }
}
