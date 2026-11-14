package com.oneclickalias.app.ui.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.app.ui.aliases.AliasMessages
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.contracts.ams.ErrorCode
import com.oneclickalias.contracts.ams.Recipient
import com.oneclickalias.contracts.auth.AccountStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** The "Add recipient" sheet. */
data class AddRecipient(val email: String = "", val submitting: Boolean = false, val error: String? = null) {
    val canSubmit: Boolean get() = email.isNotBlank() && !submitting
}

/** The actions sheet of one recipient (resend, delete with confirmation). */
data class RecipientActions(
    val recipient: Recipient,
    val confirmingDelete: Boolean = false,
    val working: Boolean = false,
    val error: String? = null
)

/** Short line above the "Add recipient" button. */
sealed interface RecipientsMessage {
    /** After adding or resending: the link is on its way. */
    data class ConfirmationSent(val email: String) : RecipientsMessage

    data class Deleted(val email: String) : RecipientsMessage

    data class Error(val text: String) : RecipientsMessage
}

/**
 * Everything the recipient screen shows. Holds email addresses, which are not
 * secret; no tokens or account numbers.
 *
 * @property paymentRequired the account status says unpaid, or a write was refused with
 *   `payment_required`; decided again by the account status of the next load.
 */
data class RecipientsUiState(
    val recipients: List<Recipient> = emptyList(),
    val list: ListStatus = ListStatus.Loading,
    val listError: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val adding: AddRecipient? = null,
    val actions: RecipientActions? = null,
    val message: RecipientsMessage? = null,
    val paymentRequired: Boolean = false
)

/**
 * The recipient addresses (docs/flows.md, "Recipients"): list, add, resend the
 * confirmation email, delete. The screen calls [load] whenever it starts, so a
 * link confirmed on the website shows after the return to the app.
 *
 * @param startWithAdd opened through "Add recipient" on the alias screen.
 */
class RecipientsViewModel(
    private val ams: AmsApi,
    private val links: WebLinks,
    private val webBase: HttpUrl,
    startWithAdd: Boolean = false,
    /** `GET /auth/account`; an error (older server, network) changes nothing. */
    private val accountStatus: suspend () -> ApiResult<AccountStatus>
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipientsUiState(adding = if (startWithAdd) AddRecipient() else null))
    val state: StateFlow<RecipientsUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    /** Reloads the list. [userRefresh] shows the pull-to-refresh indicator. */
    fun load(userRefresh: Boolean = false) {
        mutableState.update { it.copy(refreshing = it.refreshing || userRefresh) }
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val status = async { accountStatus() }
            val result = ams.listRecipients()
            // Unpaid: the notice shows at once. Without a status it clears, as before.
            val unpaid = status.await().let { it is ApiResult.Success && !it.value.paid }
            mutableState.update { s ->
                when (result) {
                    is ApiResult.Success -> s.copy(
                        recipients = result.value.recipients,
                        list = ListStatus.Ready,
                        listError = null,
                        // The sheet's recipient may have changed (confirmed meanwhile).
                        actions = s.actions?.let { a ->
                            result.value.recipients.firstOrNull {
                                it.id == a.recipient.id
                            }?.let { a.copy(recipient = it) }
                        }
                    )

                    else -> s.copy(
                        list = if (s.list == ListStatus.Ready) ListStatus.Ready else ListStatus.Failed,
                        listError = RecipientMessages.error(result, RecipientAction.Load)
                    )
                }.copy(loading = false, refreshing = false, paymentRequired = unpaid)
            }
        }
    }

    fun openAdd() {
        mutableState.update { it.copy(adding = AddRecipient(), message = null) }
    }

    fun dismissAdd() {
        mutableState.update { it.copy(adding = null) }
    }

    fun setEmail(email: String) {
        mutableState.update { s -> s.copy(adding = s.adding?.copy(email = email, error = null)) }
    }

    /** `POST /api/recipients`; the confirmation link is opened on the website. */
    fun submitAdd() {
        val adding = mutableState.value.adding ?: return
        if (!adding.canSubmit) return
        val email = adding.email.trim()
        mutableState.update { it.copy(adding = adding.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            when (val result = ams.createRecipient(email)) {
                is ApiResult.Success -> mutableState.update { s ->
                    s.copy(
                        recipients = s.recipients.filterNot { it.id == result.value.id } + result.value,
                        adding = null,
                        message = RecipientsMessage.ConfirmationSent(result.value.email)
                    )
                }

                else -> if (result.isPaymentRequired()) {
                    mutableState.update { it.copy(adding = null, paymentRequired = true) }
                } else {
                    mutableState.update { s ->
                        s.copy(
                            adding = s.adding?.copy(
                                submitting = false,
                                error = RecipientMessages.error(result, RecipientAction.Add)
                            )
                        )
                    }
                }
            }
        }
    }

    fun openActions(recipientId: String) {
        val recipient = mutableState.value.recipients.firstOrNull { it.id == recipientId } ?: return
        mutableState.update { it.copy(actions = RecipientActions(recipient), message = null) }
    }

    fun dismissActions() {
        mutableState.update { it.copy(actions = null) }
    }

    /** `POST /api/recipients/{id}/resend`: a new link, the old one stops working. */
    fun resend() {
        val actions = mutableState.value.actions ?: return
        if (actions.working) return
        mutableState.update { it.copy(actions = actions.copy(working = true, error = null)) }
        viewModelScope.launch {
            val result = ams.resendRecipientConfirmation(actions.recipient.id)
            if (result is ApiResult.Success) {
                mutableState.update {
                    it.copy(actions = null, message = RecipientsMessage.ConfirmationSent(actions.recipient.email))
                }
            } else {
                failAction(result, RecipientAction.Resend)
            }
        }
    }

    fun requestDelete() {
        mutableState.update { s -> s.copy(actions = s.actions?.copy(confirmingDelete = true, error = null)) }
    }

    fun cancelDelete() {
        mutableState.update { s -> s.copy(actions = s.actions?.copy(confirmingDelete = false, error = null)) }
    }

    /** `DELETE /api/recipients/{id}`; refused while active aliases forward to it. */
    fun confirmDelete() {
        val actions = mutableState.value.actions ?: return
        if (actions.working) return
        mutableState.update { it.copy(actions = actions.copy(working = true, error = null)) }
        viewModelScope.launch {
            val result = ams.deleteRecipient(actions.recipient.id)
            // 404: already deleted (e.g. on the website); the outcome is the same.
            if (result is ApiResult.Success || result.hasCode(ErrorCode.NOT_FOUND)) {
                mutableState.update { s ->
                    s.copy(
                        recipients = s.recipients.filterNot { it.id == actions.recipient.id },
                        actions = null,
                        message = RecipientsMessage.Deleted(actions.recipient.email)
                    )
                }
            } else {
                failAction(result, RecipientAction.Delete)
            }
        }
    }

    private fun failAction(result: ApiResult<*>, action: RecipientAction) {
        when {
            result.isPaymentRequired() -> mutableState.update { it.copy(actions = null, paymentRequired = true) }

            // The list is out of date: say so and show the server's state.
            result.hasCode(ErrorCode.ALREADY_VERIFIED) || result.hasCode(ErrorCode.NOT_FOUND) ||
                result.hasCode(ErrorCode.INVALID_RECIPIENT_ID) -> {
                mutableState.update {
                    it.copy(
                        actions = null,
                        message = RecipientsMessage.Error(RecipientMessages.error(result, action))
                    )
                }
                load()
            }

            else -> mutableState.update { s ->
                s.copy(actions = s.actions?.copy(working = false, error = RecipientMessages.error(result, action)))
            }
        }
    }

    /** Payment notice: the website's billing page, in the browser. */
    fun openBilling() {
        val url = webBase.newBuilder().addPathSegments(BILLING_PATH).build()
        if (!links.open(url)) {
            mutableState.update {
                it.copy(message = RecipientsMessage.Error(AliasMessages.noBrowser(url.toString())))
            }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun ApiResult<*>.isPaymentRequired(): Boolean = hasCode(ErrorCode.PAYMENT_REQUIRED)

    private fun ApiResult<*>.hasCode(code: ErrorCode): Boolean = this is ApiResult.HttpError && amsErrorCode() == code

    private companion object {
        /** Website route (OCA-frontend/src/App.tsx). */
        const val BILLING_PATH = "dashboard/billing"
    }
}

/** Wires the screen to this ViewModel; navigation stays with the caller. */
fun RecipientsViewModel.callbacks(onBack: () -> Unit): RecipientsCallbacks = RecipientsCallbacks(
    onBack = onBack,
    onRefresh = { load(userRefresh = true) },
    onRetry = { load() },
    onOpenAdd = ::openAdd,
    onDismissAdd = ::dismissAdd,
    onEmailChange = ::setEmail,
    onSubmitAdd = ::submitAdd,
    onOpenActions = ::openActions,
    onDismissActions = ::dismissActions,
    onResend = ::resend,
    onRequestDelete = ::requestDelete,
    onCancelDelete = ::cancelDelete,
    onConfirmDelete = ::confirmDelete,
    onOpenBilling = ::openBilling,
    onDismissMessage = ::dismissMessage
)
