package com.oneclickalias.app.ui.aliases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.aliases.newestFirst
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.app.platform.AliasClipboard
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.AliasList
import com.oneclickalias.contracts.ams.ErrorCode
import com.oneclickalias.contracts.auth.AccountStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

enum class ListStatus {
    /** First load still running. */
    Loading,

    /** The list was loaded at least once. */
    Ready,

    /** The list never loaded; [AliasesUiState.listError] says why. */
    Failed
}

/** Short line at the bottom of the list. */
sealed interface AliasesMessage {
    /** Shown only where the system does not confirm copies itself (before Android 13). */
    data class Copied(val alias: String) : AliasesMessage

    data class Deleted(val alias: String) : AliasesMessage

    data class Error(val text: String) : AliasesMessage
}

/** The actions sheet of one alias (tracker switch, delete with confirmation). */
data class AliasActions(
    val alias: Alias,
    val saving: Boolean = false,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null
)

/**
 * Everything the alias screen shows. Holds alias and recipient addresses,
 * which are not secret; no tokens or account numbers.
 *
 * @property paymentRequired the account status says unpaid, or a write was refused with
 *   `payment_required`; decided again by the account status of the next load.
 */
data class AliasesUiState(
    val aliases: List<Alias> = emptyList(),
    val list: ListStatus = ListStatus.Loading,
    val listError: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val message: AliasesMessage? = null,
    val paymentRequired: Boolean = false,
    val actions: AliasActions? = null
)

/**
 * The "Aliases" tab (docs/flows.md, "Aliases"); new aliases come from the
 * "New alias" tab ([CopyViewModel]). The screen calls
 * [load] whenever it starts, so every app start and every return to the
 * foreground reloads the list; that is also how a device signed out elsewhere
 * is noticed (the AMS refuses its token, SessionManager ends the session).
 */
class AliasesViewModel(
    private val ams: AmsApi,
    private val clipboard: AliasClipboard,
    private val links: WebLinks,
    private val webBase: HttpUrl,
    /** `GET /auth/account`; an error (older server, network) changes nothing. */
    private val accountStatus: suspend () -> ApiResult<AccountStatus>
) : ViewModel() {
    private val mutableState = MutableStateFlow(AliasesUiState())
    val state: StateFlow<AliasesUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Reloads aliases, recipients and the account status. [userRefresh] shows
     * the pull-to-refresh indicator.
     */
    fun load(userRefresh: Boolean = false) {
        mutableState.update { it.copy(refreshing = it.refreshing || userRefresh) }
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            coroutineScope {
                val status = async { accountStatus() }
                applyAliases(ams.listAliases())
                applyPaymentStatus(status.await())
            }
            mutableState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    private fun applyAliases(result: ApiResult<AliasList>) {
        mutableState.update { s ->
            when (result) {
                is ApiResult.Success ->
                    s.copy(aliases = newestFirst(result.value.aliases), list = ListStatus.Ready, listError = null)

                else -> s.copy(
                    list = if (s.list == ListStatus.Ready) ListStatus.Ready else ListStatus.Failed,
                    listError = AliasMessages.error(result, AliasAction.Load)
                )
            }
        }
    }

    /**
     * An unpaid account shows the payment notice at once instead of after the
     * first refused write. Without a status (older server, network error) the
     * notice is cleared as before, and the next write checks again.
     */
    private fun applyPaymentStatus(result: ApiResult<AccountStatus>) {
        val unpaid = result is ApiResult.Success && !result.value.paid
        mutableState.update { it.copy(paymentRequired = unpaid) }
    }

    /** Tap on a row. */
    fun copy(alias: Alias) {
        val systemConfirms = clipboard.copy(alias.alias)
        mutableState.update { it.copy(message = if (systemConfirms) null else AliasesMessage.Copied(alias.alias)) }
    }

    fun openActions(aliasId: String) {
        val alias = mutableState.value.aliases.firstOrNull { it.id == aliasId } ?: return
        mutableState.update { it.copy(actions = AliasActions(alias)) }
    }

    fun dismissActions() {
        mutableState.update { it.copy(actions = null) }
    }

    fun setBlockTrackers(enabled: Boolean) {
        val actions = mutableState.value.actions ?: return
        if (actions.saving || actions.deleting) return
        mutableState.update { it.copy(actions = actions.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val result = ams.setBlockTrackers(actions.alias.id, enabled)
            if (result is ApiResult.Success) {
                val saved = result.value
                mutableState.update { s ->
                    s.copy(
                        aliases = s.aliases.map { if (it.id == saved.id) saved else it },
                        actions = s.actions?.takeIf { it.alias.id == saved.id }?.copy(alias = saved, saving = false)
                    )
                }
            } else {
                failAction(result, AliasAction.Trackers)
            }
        }
    }

    fun requestDelete() {
        mutableState.update { s -> s.copy(actions = s.actions?.copy(confirmingDelete = true, error = null)) }
    }

    fun cancelDelete() {
        mutableState.update { s -> s.copy(actions = s.actions?.copy(confirmingDelete = false, error = null)) }
    }

    fun confirmDelete() {
        val actions = mutableState.value.actions ?: return
        if (actions.deleting || actions.saving) return
        mutableState.update { it.copy(actions = actions.copy(deleting = true, error = null)) }
        viewModelScope.launch {
            val result = ams.deleteAlias(actions.alias.id)
            // 404: already deleted (e.g. on the website); the outcome is the same.
            if (result is ApiResult.Success || result.isNotFound()) {
                mutableState.update { s ->
                    s.copy(
                        aliases = s.aliases.filterNot { it.id == actions.alias.id },
                        actions = null,
                        message = AliasesMessage.Deleted(actions.alias.alias)
                    )
                }
            } else {
                failAction(result, AliasAction.Delete)
            }
        }
    }

    private fun failAction(result: ApiResult<*>, action: AliasAction) {
        when {
            result.isPaymentRequired() -> mutableState.update { it.copy(actions = null, paymentRequired = true) }

            result.isNotFound() -> {
                mutableState.update {
                    it.copy(actions = null, message = AliasesMessage.Error(AliasMessages.error(result, action)))
                }
                load()
            }

            else -> mutableState.update { s ->
                s.copy(
                    actions = s.actions?.copy(
                        saving = false,
                        deleting = false,
                        error = AliasMessages.error(result, action)
                    )
                )
            }
        }
    }

    /** Payment notice: the website's billing page, in the browser. */
    fun openBilling() {
        val url = webBase.newBuilder().addPathSegments(BILLING_PATH).build()
        if (!links.open(url)) {
            mutableState.update { it.copy(message = AliasesMessage.Error(AliasMessages.noBrowser(url.toString()))) }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun ApiResult<*>.isPaymentRequired(): Boolean =
        this is ApiResult.HttpError && amsErrorCode() == ErrorCode.PAYMENT_REQUIRED

    private fun ApiResult<*>.isNotFound(): Boolean = this is ApiResult.HttpError &&
        (amsErrorCode() == ErrorCode.NOT_FOUND || amsErrorCode() == ErrorCode.INVALID_ALIAS_ID)

    private companion object {
        /** Website route (OCA-frontend/src/App.tsx). */
        const val BILLING_PATH = "dashboard/billing"
    }
}

/** Wires the screen to this ViewModel. */
fun AliasesViewModel.callbacks(): AliasesCallbacks = AliasesCallbacks(
    onRefresh = { load(userRefresh = true) },
    onRetry = { load() },
    onCopy = ::copy,
    onOpenActions = ::openActions,
    onDismissActions = ::dismissActions,
    onBlockTrackers = ::setBlockTrackers,
    onRequestDelete = ::requestDelete,
    onCancelDelete = ::cancelDelete,
    onConfirmDelete = ::confirmDelete,
    onOpenBilling = ::openBilling,
    onDismissMessage = ::dismissMessage
)
