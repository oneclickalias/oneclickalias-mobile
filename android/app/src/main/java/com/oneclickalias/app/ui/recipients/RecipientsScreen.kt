package com.oneclickalias.app.ui.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.aliases.ListStatus
import com.oneclickalias.app.ui.components.NoPersonalizedLearning
import com.oneclickalias.app.ui.components.OcaBadge
import com.oneclickalias.app.ui.components.OcaBadgeTone
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaEmptyState
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaInlineMessage
import com.oneclickalias.app.ui.components.OcaListError
import com.oneclickalias.app.ui.components.OcaListRow
import com.oneclickalias.app.ui.components.OcaListScaffold
import com.oneclickalias.app.ui.components.OcaListStatusLine
import com.oneclickalias.app.ui.components.OcaMessageTone
import com.oneclickalias.app.ui.components.OcaNotice
import com.oneclickalias.app.ui.components.OcaSheet
import com.oneclickalias.app.ui.components.OcaSheetHeader
import com.oneclickalias.app.ui.components.OcaSheetTitle
import com.oneclickalias.app.ui.components.OcaSkeletonRows
import com.oneclickalias.app.ui.components.OcaTextField
import com.oneclickalias.app.ui.components.listItemWidth
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Recipient
import kotlinx.coroutines.delay

/** Everything the recipient screen can ask for; the ViewModel provides the real ones. */
data class RecipientsCallbacks(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenAdd: () -> Unit = {},
    val onDismissAdd: () -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onSubmitAdd: () -> Unit = {},
    val onOpenActions: (recipientId: String) -> Unit = {},
    val onDismissActions: () -> Unit = {},
    val onResend: () -> Unit = {},
    val onRequestDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenBilling: () -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

/**
 * The recipient addresses: where aliases forward to. Rows show the address and
 * whether it is confirmed; a tap or "⋯" opens the actions. "Add recipient" is
 * pinned in the thumb zone, as "New alias" is on the alias screen.
 */
@Composable
fun RecipientsScreen(state: RecipientsUiState, callbacks: RecipientsCallbacks, modifier: Modifier = Modifier) {
    OcaListScaffold(
        title = "Recipients",
        onBack = callbacks.onBack,
        refreshing = state.refreshing,
        onRefresh = callbacks.onRefresh,
        modifier = modifier,
        bottom = {
            if (state.paymentRequired) {
                OcaNotice(
                    title = "Your account is not active yet",
                    body = "Adding and changing recipient addresses needs an active account. " +
                        "You can activate it on the website.",
                    action = "Open billing",
                    onAction = callbacks.onOpenBilling
                )
            }
            state.message?.let { MessageLine(message = it, onDismiss = callbacks.onDismissMessage) }
            OcaButton(text = "Add recipient", onClick = callbacks.onOpenAdd, modifier = Modifier.fillMaxWidth())
        }
    ) {
        when (state.list) {
            ListStatus.Failed -> item(key = "failed") {
                OcaListError(
                    text = state.listError ?: RecipientMessages.LOAD_FAILED,
                    retryEnabled = !state.loading,
                    onRetry = callbacks.onRetry,
                    compact = false
                )
            }

            ListStatus.Loading -> item(key = "loading") {
                // Right under the top bar, whose line is the first row's top line.
                OcaSkeletonRows(description = "Loading your recipient addresses", topDivider = false)
            }

            ListStatus.Ready -> {
                state.listError?.let { error ->
                    item(key = "reload-error") {
                        OcaListError(
                            text = error,
                            retryEnabled = !state.loading,
                            onRetry = callbacks.onRetry,
                            compact = true
                        )
                    }
                }
                if (state.recipients.isEmpty()) {
                    item(key = "empty") {
                        // "Add recipient" is pinned below, so the empty state needs no button of its own.
                        OcaEmptyState(
                            icon = R.drawable.ic_mail,
                            title = "No recipient addresses yet",
                            body = "Your aliases forward mail to these addresses. A new address gets a " +
                                "confirmation email; it can receive mail once you open the link in it.",
                            modifier = Modifier
                                .listItemWidth()
                                .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.xxxl)
                        )
                    }
                }
                items(state.recipients, key = { it.id }) { recipient ->
                    Column(Modifier.listItemWidth()) {
                        RecipientRow(recipient = recipient, onOpenActions = { callbacks.onOpenActions(recipient.id) })
                        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
                    }
                }
            }
        }
    }
    state.adding?.let { adding ->
        OcaSheet(onDismiss = callbacks.onDismissAdd) { AddRecipientContent(adding = adding, callbacks = callbacks) }
    }
    state.actions?.let { actions ->
        OcaSheet(onDismiss = callbacks.onDismissActions) {
            RecipientActionsContent(actions = actions, callbacks = callbacks)
        }
    }
}

private fun statusText(recipient: Recipient): String =
    if (recipient.verified) "Confirmed" else "Waiting for confirmation"

@Composable
private fun RecipientRow(recipient: Recipient, onOpenActions: () -> Unit) {
    OcaListRow(
        title = recipient.email,
        detailContent = { RecipientStatus(recipient) },
        onClick = onOpenActions,
        onClickLabel = "show actions",
        textSemantics = {
            contentDescription = "Recipient ${recipient.email}, ${statusText(recipient).lowercase()}"
        },
        trailingIsButton = true,
        trailing = {
            IconButton(onClick = onOpenActions) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = "Actions for ${recipient.email}",
                    tint = OcaTheme.colors.mutedForeground
                )
            }
        }
    )
}

/** A "Confirmed" badge with the primary check, or a quiet "Pending" one with a clock. */
@Composable
private fun RecipientStatus(recipient: Recipient) {
    if (recipient.verified) {
        OcaBadge(text = "Confirmed", tone = OcaBadgeTone.Primary, icon = R.drawable.ic_check)
    } else {
        OcaBadge(text = "Pending", icon = R.drawable.ic_schedule)
    }
}

/** "Sent" and "deleted" close themselves; an error stays until the next action. */
@Composable
private fun MessageLine(message: RecipientsMessage, onDismiss: () -> Unit) {
    when (message) {
        is RecipientsMessage.Error -> OcaInlineMessage(text = message.text, tone = OcaMessageTone.Error)

        is RecipientsMessage.ConfirmationSent -> OcaInlineMessage(
            text = "Confirmation email sent to ${message.email}",
            tone = OcaMessageTone.Success,
            onTimeout = onDismiss
        )

        is RecipientsMessage.Deleted ->
            OcaInlineMessage(text = "Address deleted", tone = OcaMessageTone.Success, onTimeout = onDismiss)
    }
}

/** Same wording as the website's "Add email" dialog (OCA-frontend/src/email-dialog.tsx). */
@Composable
private fun AddRecipientContent(adding: AddRecipient, callbacks: RecipientsCallbacks) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OcaSheetTitle("Add recipient")
    Column(
        modifier = Modifier.padding(horizontal = OcaSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Text(
            text = "Enter the inbox that alias mail should be forwarded to. We'll send a confirmation email to " +
                "verify the address.",
            style = MaterialTheme.typography.bodyMedium,
            color = OcaTheme.colors.mutedForeground
        )
        NoPersonalizedLearning {
            OcaTextField(
                value = adding.email,
                onValueChange = callbacks.onEmailChange,
                label = "Email address",
                enabled = !adding.submitting,
                isError = adding.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSend = { if (adding.canSubmit) callbacks.onSubmitAdd() }),
                fieldModifier = Modifier.focusRequester(focus)
            )
        }
        adding.error?.let { OcaErrorText(text = it) }
        OcaButton(
            text = if (adding.submitting) "Sending…" else "Send confirmation email",
            onClick = callbacks.onSubmitAdd,
            enabled = adding.canSubmit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecipientActionsContent(actions: RecipientActions, callbacks: RecipientsCallbacks) {
    val recipient = actions.recipient
    OcaSheetHeader(
        title = recipient.email,
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        subtitle = { RecipientStatus(recipient) }
    )
    Column(
        modifier = Modifier.padding(horizontal = OcaSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        if (actions.confirmingDelete) {
            DeleteConfirmation(actions = actions, callbacks = callbacks)
        } else {
            if (!recipient.verified) {
                Text(
                    text = "Open the link in the confirmation email to confirm this address. The link works for " +
                        "24 hours; a new one replaces it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OcaTheme.colors.mutedForeground
                )
                OcaButton(
                    text = if (actions.working) "Sending…" else "Resend confirmation email",
                    onClick = callbacks.onResend,
                    variant = OcaButtonVariant.Outline,
                    enabled = !actions.working,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OcaButton(
                text = "Delete recipient",
                onClick = callbacks.onRequestDelete,
                variant = OcaButtonVariant.DestructiveGhost,
                enabled = !actions.working,
                modifier = Modifier.fillMaxWidth()
            )
        }
        actions.error?.let { OcaErrorText(text = it) }
    }
}

@Composable
private fun DeleteConfirmation(actions: RecipientActions, callbacks: RecipientsCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)) {
        Text(
            text = "Delete recipient?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "The address is removed from your account. An address that aliases still forward to " +
                "can't be deleted.",
            style = MaterialTheme.typography.bodyMedium,
            color = OcaTheme.colors.mutedForeground
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
        OcaButton(
            text = "Cancel",
            onClick = callbacks.onCancelDelete,
            variant = OcaButtonVariant.Outline,
            enabled = !actions.working,
            modifier = Modifier.weight(1f)
        )
        OcaButton(
            text = if (actions.working) "Deleting…" else "Delete",
            onClick = callbacks.onConfirmDelete,
            variant = OcaButtonVariant.Destructive,
            enabled = !actions.working,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun RecipientsScreenPreview() {
    OcaTheme {
        RecipientsScreen(
            state = RecipientsUiState(
                recipients = listOf(
                    Recipient("r1", "me@example.com", true, "2026-11-01T10:00:00Z"),
                    Recipient("r2", "work@example.com", false, "2026-11-02T10:00:00Z")
                ),
                list = ListStatus.Ready
            ),
            callbacks = RecipientsCallbacks()
        )
    }
}
