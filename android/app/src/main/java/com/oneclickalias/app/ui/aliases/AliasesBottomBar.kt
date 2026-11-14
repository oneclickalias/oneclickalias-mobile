package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.data.aliases.RecipientDecision
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaCard
import com.oneclickalias.app.ui.components.OcaCardSize
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaNotice
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import kotlinx.coroutines.delay

/** How long "Copied"/"Deleted" stay; errors stay until the next action. */
private const val CONFIRMATION_MILLIS = 3_000L

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

@Composable
private fun MessageLine(message: AliasesMessage, onDismiss: () -> Unit) {
    when (message) {
        is AliasesMessage.Error -> OcaErrorText(text = message.text)
        is AliasesMessage.Copied -> QuietLine("Copied to clipboard")
        is AliasesMessage.Deleted -> QuietLine("Alias deleted")
    }
    if (message !is AliasesMessage.Error) {
        LaunchedEffect(message) {
            delay(CONFIRMATION_MILLIS)
            onDismiss()
        }
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OcaTheme.colors.mutedForeground,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** Instead of the cards while no recipient is confirmed; leads to the recipient screen. */
@Composable
internal fun NoRecipientNotice(awaitingConfirmation: Boolean, onOpenRecipients: (addNew: Boolean) -> Unit) {
    if (awaitingConfirmation) {
        OcaNotice(
            title = "Confirm a recipient address first",
            body = "Aliases forward to one of your email addresses. Open the confirmation link we emailed you, " +
                "then come back here.",
            action = "Open recipients",
            onAction = { onOpenRecipients(false) }
        )
    } else {
        OcaNotice(
            title = "Confirm a recipient address first",
            body = "Aliases forward to one of your email addresses. Add one and open the confirmation link " +
                "we email you.",
            action = "Add recipient",
            onAction = { onOpenRecipients(true) }
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
