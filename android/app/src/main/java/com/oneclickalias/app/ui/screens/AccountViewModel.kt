package com.oneclickalias.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.contracts.auth.AccountStatus
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Whether the account may create aliases, as the website's header says it. */
sealed interface PaidState {
    /** Paid; [until] is the end date ("Dec 10, 2026"), null when unreadable. */
    data class Active(val until: String?) : PaidState

    /** Was paid until [on]. */
    data class Expired(val on: String?) : PaidState

    /** Never paid. */
    data object NotActive : PaidState
}

private val paidDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** [status] as a [PaidState], dates in [zone] (the phone's time zone). */
fun paidState(status: AccountStatus, zone: ZoneId = ZoneId.systemDefault()): PaidState {
    val date = status.paidUntil?.let {
        try {
            OffsetDateTime.parse(it).atZoneSameInstant(zone).format(paidDateFormat)
        } catch (e: DateTimeParseException) {
            null
        }
    }
    return when {
        status.paid -> PaidState.Active(date)
        status.paidUntil != null -> PaidState.Expired(date)
        else -> PaidState.NotActive
    }
}

/**
 * @property message e.g. "no browser found" after Billing.
 * @property paidState null while loading or when the status could not be read.
 * @property statusLoaded the first status request has finished (with or without a status);
 *   until then the screen keeps the status card's place.
 */
data class AccountUiState(
    val loggingOut: Boolean = false,
    val message: String? = null,
    val paidState: PaidState? = null,
    val loadingStatus: Boolean = false,
    val statusLoaded: Boolean = false
)

class AccountViewModel(
    private val sessionManager: SessionManager,
    private val links: WebLinks,
    private val webBase: HttpUrl,
    /** `GET /auth/account`; an error leaves the status out. */
    private val accountStatus: suspend () -> ApiResult<AccountStatus> = { ApiResult.HttpError(0, "") }
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    /** Reads the account status; the screen calls it whenever it starts. */
    fun load() {
        if (mutableState.value.loadingStatus) return
        mutableState.update { it.copy(loadingStatus = true) }
        viewModelScope.launch {
            val result = accountStatus()
            mutableState.update { s ->
                s.copy(
                    loadingStatus = false,
                    statusLoaded = true,
                    // A failed reload keeps what was shown; the next start tries again.
                    paidState = if (result is ApiResult.Success) paidState(result.value) else s.paidState
                )
            }
        }
    }

    /** Navigation to the login screen follows the session state once logout is done. */
    fun logout() {
        if (mutableState.value.loggingOut) return
        mutableState.update { it.copy(loggingOut = true, message = null) }
        viewModelScope.launch { sessionManager.logout() }
    }

    /** The website's billing page, in the browser (no web view). */
    fun openBilling() {
        val url = webBase.newBuilder().addPathSegments(BILLING_PATH).build()
        val opened = links.open(url)
        mutableState.update { it.copy(message = if (opened) null else AliasMessages.noBrowser(url.toString())) }
    }

    private companion object {
        /** Website route (OCA-frontend/src/App.tsx). */
        const val BILLING_PATH = "dashboard/billing"
    }
}
