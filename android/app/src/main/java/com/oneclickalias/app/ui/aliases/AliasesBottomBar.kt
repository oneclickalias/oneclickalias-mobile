package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaCard
import com.oneclickalias.app.ui.components.OcaCardSize
import com.oneclickalias.app.ui.components.OcaInlineMessage
import com.oneclickalias.app.ui.components.OcaMessageTone
import com.oneclickalias.app.ui.components.OcaNotice
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * The pinned area under the list: the payment notice (unless [showPayment] is
 * false, then the list shows it) and the message line, right above the tab
 * bar; nothing when there is neither.
 */
@Composable
internal fun AliasesBottomBar(state: AliasesUiState, callbacks: AliasesCallbacks, showPayment: Boolean = true) {
    val payment = state.paymentRequired && showPayment
    if (!payment && state.message == null) return
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        Column(
            modifier = Modifier
                .widthIn(max = OcaSpacing.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
        ) {
            if (payment) PaymentNotice(onOpenBilling = callbacks.onOpenBilling)
            state.message?.let { MessageLine(message = it, onDismiss = callbacks.onDismissMessage) }
        }
    }
}

/** "Copied"/"Deleted" close themselves; an error stays until the next action. */
@Composable
private fun MessageLine(message: AliasesMessage, onDismiss: () -> Unit) {
    when (message) {
        is AliasesMessage.Error -> OcaInlineMessage(text = message.text, tone = OcaMessageTone.Error)

        is AliasesMessage.Copied ->
            OcaInlineMessage(text = "Copied to clipboard", tone = OcaMessageTone.Success, onTimeout = onDismiss)

        is AliasesMessage.Deleted ->
            OcaInlineMessage(text = "Alias deleted", tone = OcaMessageTone.Success, onTimeout = onDismiss)
    }
}

/**
 * Instead of the cards while no recipient is confirmed: the two steps to the
 * first alias, the done ones ticked, and the way to the recipient screen.
 */
@Composable
internal fun NoRecipientNotice(awaitingConfirmation: Boolean, onOpenRecipients: (addNew: Boolean) -> Unit) {
    OcaCard(size = OcaCardSize.Small, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Text(
                text = if (awaitingConfirmation) "Confirm your inbox" else "Add an inbox to get started",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Aliases forward mail to an inbox you confirm.",
                style = MaterialTheme.typography.bodyMedium,
                color = OcaTheme.colors.mutedForeground
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
            SetupStep(number = 1, text = "Add the inbox your aliases forward to", done = awaitingConfirmation)
            SetupStep(number = 2, text = "Open the confirmation link we email you", done = false)
        }
        OcaButton(
            text = if (awaitingConfirmation) "Open recipients" else "Add recipient",
            onClick = { onOpenRecipients(!awaitingConfirmation) },
            variant = OcaButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A numbered step; a done one shows a check instead of its number. */
@Composable
private fun SetupStep(number: Int, text: String, done: Boolean) {
    val c = OcaTheme.colors
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Step $number, $text${if (done) ", done" else ""}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
    ) {
        Box(
            modifier = Modifier.size(24.dp).border(1.dp, if (done) c.primary else c.border),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = GeistMono),
                    color = c.mutedForeground
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) c.mutedForeground else c.foreground
        )
    }
}

/** The account is not paid (status, or a write refused with `payment_required`). */
@Composable
internal fun PaymentNotice(onOpenBilling: () -> Unit) {
    OcaNotice(
        title = "Your account is not active yet",
        body = "Creating and changing aliases needs an active account. You can activate it on the website.",
        action = "Open billing",
        onAction = onOpenBilling
    )
}
