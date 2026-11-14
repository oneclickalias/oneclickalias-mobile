package com.oneclickalias.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.app.ui.aliases.AliasMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** @property message e.g. "no browser found" after Billing. */
data class AccountUiState(val loggingOut: Boolean = false, val message: String? = null)

class AccountViewModel(
    private val sessionManager: SessionManager,
    private val links: WebLinks,
    private val webBase: HttpUrl
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    /** Navigation to the login screen follows the session state once logout is done. */
    fun logout() {
        if (mutableState.value.loggingOut) return
        mutableState.value = AccountUiState(loggingOut = true)
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
