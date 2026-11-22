package com.oneclickalias.app.ui.aliases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclickalias.app.data.aliases.ClaimDelivery
import com.oneclickalias.app.data.aliases.ClaimOutcome
import com.oneclickalias.app.data.api.AmsApi
import com.oneclickalias.app.data.api.ApiResult
import com.oneclickalias.app.data.api.amsErrorCode
import com.oneclickalias.app.platform.AliasClipboard
import com.oneclickalias.app.platform.WebLinks
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.ErrorCode
import com.oneclickalias.contracts.ams.Recipient
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

/**
 * One card of the "New alias" tab: a confirmed recipient and this device's ready
 * alias for it.
 *
 * @property alias the ready alias the Copy button copies; null while loading or after an error.
 * @property copied copied, but the server does not know yet: not active until it does.
 * @property working a claim or the next ready alias is on its way.
 * @property error why there is no ready alias; Retry reloads.
 */
data class CopyCard(
    val recipient: Recipient,
    val alias: Alias? = null,
    val copied: Alias? = null,
    val working: Boolean = false,
    val error: String? = null
)

/** Short line under the cards. */
sealed interface CopyMessage {
    /** Shown only where the system does not confirm copies itself (before Android 13). */
    data object Copied : CopyMessage

    /** The copied alias was removed on the server (its recipient was deleted). */
    data object Removed : CopyMessage
}

/**
 * The "New alias" tab (docs/flows.md, "New alias tab"): no tokens or account
 * numbers, only alias and recipient addresses.
 *
 * @property noRecipient no confirmed recipient: true = one waits for its confirmation, false = none added.
 * @property pendingRecipients recipients still waiting for their confirmation, shown next to the cards.
 */
data class CopyUiState(
    val cards: List<CopyCard> = emptyList(),
    val list: ListStatus = ListStatus.Loading,
    val loadError: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val paymentRequired: Boolean = false,
    val noRecipient: Boolean? = null,
    val pendingRecipients: Int = 0,
    val message: CopyMessage? = null
)

/**
 * App open, Copy, done: one card per confirmed recipient with a ready alias.
 * Copy puts the alias in the clipboard at once and then claims it, which
 * makes it active; the card shows the next one. Without a connection the
 * claim waits ([ClaimDelivery]) and the card says the alias needs the
 * phone to go online.
 */
class CopyViewModel(
    private val ams: AmsApi,
    private val claims: ClaimDelivery,
    private val markCopied: suspend (aliasId: String) -> Unit,
    private val clipboard: AliasClipboard,
    private val links: WebLinks,
    private val webBase: HttpUrl,
    private val accountStatus: suspend () -> ApiResult<AccountStatus>
) : ViewModel() {
    private val mutableState = MutableStateFlow(CopyUiState())
    val state: StateFlow<CopyUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    /** Every start and return to the foreground; [userRefresh] shows the pull-to-refresh indicator. */
    fun load(userRefresh: Boolean = false) {
        mutableState.update { it.copy(refreshing = it.refreshing || userRefresh) }
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            // Copies made offline first, so the server's ready list is current.
            claims.deliverAll()
            coroutineScope {
                val status = async { accountStatus() }
                val ready = async { ams.listReadyAliases() }
                val recipients = ams.listRecipients()
                apply(recipients, ready.await(), status.await())
            }
            // Part of the load: a reload waits until every card has its alias (or error).
            fillMissing()
            mutableState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    private suspend fun apply(
        recipients: ApiResult<com.oneclickalias.contracts.ams.RecipientList>,
        ready: ApiResult<com.oneclickalias.contracts.ams.AliasList>,
        status: ApiResult<AccountStatus>
    ) {
        val unpaid = (status is ApiResult.Success && !status.value.paid) || ready.isPaymentRequired()
        if (recipients !is ApiResult.Success) {
            mutableState.update { s ->
                s.copy(
                    list = if (s.list == ListStatus.Ready) ListStatus.Ready else ListStatus.Failed,
                    loadError = AliasMessages.error(recipients, AliasAction.Load),
                    paymentRequired = unpaid
                )
            }
            return
        }
        val confirmed = recipients.value.recipients.filter { it.verified }
        val readyByRecipient = (ready as? ApiResult.Success)?.value?.aliases.orEmpty()
            .filter { it.recipientId != null }
            .associateBy { it.recipientId }
        val pending = claims.pendingIds()
        val cards = if (unpaid) {
            emptyList()
        } else {
            confirmed.map { recipient ->
                val alias = readyByRecipient[recipient.id]
                when {
                    alias == null -> CopyCard(recipient)
                    alias.id in pending -> CopyCard(recipient, copied = alias)
                    else -> CopyCard(recipient, alias = alias)
                }
            }
        }
        mutableState.update { s ->
            s.copy(
                cards = cards,
                list = ListStatus.Ready,
                loadError = if (ready is ApiResult.Success ||
                    unpaid
                ) {
                    null
                } else {
                    AliasMessages.error(ready, AliasAction.Load)
                },
                paymentRequired = unpaid,
                noRecipient = if (confirmed.isEmpty()) recipients.value.recipients.isNotEmpty() else null,
                pendingRecipients = recipients.value.recipients.count { !it.verified }
            )
        }
    }

    /** Asks the server for the ready alias of every card that has none, one after the other (rate limit). */
    private suspend fun fillMissing() {
        val missing = mutableState.value.cards.filter { it.alias == null && it.copied == null && !it.working }
        for (card in missing) ensure(card.recipient.id)
    }

    private suspend fun ensure(recipientId: String) {
        updateCard(recipientId) { it.copy(working = true, error = null) }
        val result = ams.ensureReadyAlias(recipientId)
        when {
            result is ApiResult.Success -> updateCard(recipientId) { it.copy(alias = result.value, working = false) }

            result.isPaymentRequired() -> mutableState.update { it.copy(paymentRequired = true, cards = emptyList()) }

            else -> updateCard(recipientId) {
                it.copy(working = false, error = AliasMessages.error(result, AliasAction.Create))
            }
        }
    }

    /** The Copy button. A copied alias that still waits for the server can be copied again. */
    fun copy(recipientId: String) {
        val card = mutableState.value.cards.firstOrNull { it.recipient.id == recipientId } ?: return
        card.copied?.let { waiting ->
            showCopied(clipboard.copy(waiting.alias))
            return
        }
        val alias = card.alias ?: return
        if (card.working) return
        updateCard(recipientId) { it.copy(alias = null, copied = alias, working = true) }
        viewModelScope.launch {
            // Remember the copy before the address leaves the app, so it is claimed even after a crash.
            markCopied(alias.id)
            showCopied(clipboard.copy(alias.alias))
            when (val outcome = claims.claim(alias.id)) {
                is ClaimOutcome.Claimed -> {
                    val next = outcome.result.next
                    updateCard(recipientId) { it.copy(copied = null, alias = next, working = next == null) }
                    if (next == null) ensure(recipientId)
                }

                ClaimOutcome.Gone -> {
                    mutableState.update { s ->
                        s.copy(
                            message = CopyMessage.Removed,
                            cards = s.cards.map { if (it.recipient.id == recipientId) it.copy(copied = null) else it }
                        )
                    }
                    ensure(recipientId)
                }

                is ClaimOutcome.Failed -> {
                    if (outcome.failure.isPaymentRequired()) mutableState.update { it.copy(paymentRequired = true) }
                    updateCard(recipientId) { it.copy(working = false) }
                }
            }
        }
    }

    private fun showCopied(systemConfirms: Boolean) {
        if (!systemConfirms) mutableState.update { it.copy(message = CopyMessage.Copied) }
    }

    private fun updateCard(recipientId: String, change: (CopyCard) -> CopyCard) {
        mutableState.update { s ->
            s.copy(cards = s.cards.map { if (it.recipient.id == recipientId) change(it) else it })
        }
    }

    /** Payment notice: the website's billing page, in the browser. */
    fun openBilling() {
        val url = webBase.newBuilder().addPathSegments(BILLING_PATH).build()
        links.open(url)
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun ApiResult<*>.isPaymentRequired(): Boolean =
        this is ApiResult.HttpError && amsErrorCode() == ErrorCode.PAYMENT_REQUIRED

    private companion object {
        /** Website route (OCA-frontend/src/App.tsx). */
        const val BILLING_PATH = "dashboard/billing"
    }
}
