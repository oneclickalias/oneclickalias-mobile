package com.oneclickalias.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.AuthApi
import com.oneclickalias.app.data.api.authErrorCode
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.contracts.auth.Device
import com.oneclickalias.contracts.auth.ErrorCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Sign out this device?" sheet. */
data class DeviceSignOut(val device: Device, val working: Boolean = false, val error: String? = null) {
    // Device ids are never printed (docs/flows.md, "Never log").
    override fun toString(): String = "DeviceSignOut(device=${device.name}, working=$working, error=$error)"
}

sealed interface DevicesMessage {
    data class SignedOut(val name: String) : DevicesMessage
}

/** Everything the device screen shows. Device ids stay out of [toString]. */
data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val list: ListStatus = ListStatus.Loading,
    val listError: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val signingOut: DeviceSignOut? = null,
    val message: DevicesMessage? = null
) {
    override fun toString(): String = "DevicesUiState(devices=${devices.map { it.name }}, list=$list, " +
        "listError=$listError, loading=$loading, refreshing=$refreshing, signingOut=$signingOut, message=$message)"
}

/**
 * The devices signed in to the account (`GET /devices`), with sign-out per
 * device. Signing out this device is a logout; "Sign out all devices" is a
 * screen of its own, because it needs the account number.
 */
class DevicesViewModel(private val sessions: SessionManager, private val auth: AuthApi) : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    /** Reloads the list. [userRefresh] shows the pull-to-refresh indicator. */
    fun load(userRefresh: Boolean = false) {
        mutableState.update { it.copy(refreshing = it.refreshing || userRefresh) }
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val result = sessions.authorized { auth.listDevices(it) }
            // Without this device the list belongs to a session that was ended elsewhere.
            if (result is ApiResult.Success && result.value.devices.none { it.current }) {
                return@launch sessions.endRemovedElsewhere()
            }
            mutableState.update { s ->
                when (result) {
                    // This device first, the others in server order.
                    is ApiResult.Success -> s.copy(
                        devices = result.value.devices.sortedByDescending { it.current },
                        list = ListStatus.Ready,
                        listError = null
                    )

                    else -> s.copy(
                        list = if (s.list == ListStatus.Ready) ListStatus.Ready else ListStatus.Failed,
                        listError = AccountMessages.error(result, AccountAction.LoadDevices)
                    )
                }.copy(loading = false, refreshing = false)
            }
        }
    }

    fun requestSignOut(deviceId: String) {
        val device = mutableState.value.devices.firstOrNull { it.deviceId == deviceId } ?: return
        mutableState.update { it.copy(signingOut = DeviceSignOut(device), message = null) }
    }

    fun dismissSignOut() {
        mutableState.update { it.copy(signingOut = null) }
    }

    fun confirmSignOut() {
        val signOut = mutableState.value.signingOut ?: return
        if (signOut.working) return
        mutableState.update { it.copy(signingOut = signOut.copy(working = true, error = null)) }
        viewModelScope.launch {
            // This device: a normal logout; navigation follows the session state.
            if (signOut.device.current) return@launch sessions.logout()
            val result = sessions.authorized { auth.deleteDevice(signOut.device.deviceId, it) }
            // 404: signed out elsewhere meanwhile; the outcome is the same.
            if (result is ApiResult.Success ||
                (result is ApiResult.HttpError && result.authErrorCode() == ErrorCode.NOT_FOUND)
            ) {
                mutableState.update { s ->
                    s.copy(
                        devices = s.devices.filterNot { it.deviceId == signOut.device.deviceId },
                        signingOut = null,
                        message = DevicesMessage.SignedOut(signOut.device.name)
                    )
                }
            } else {
                mutableState.update { s ->
                    s.copy(
                        signingOut = s.signingOut?.copy(
                            working = false,
                            error = AccountMessages.error(result, AccountAction.SignOutDevice)
                        )
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }
}
