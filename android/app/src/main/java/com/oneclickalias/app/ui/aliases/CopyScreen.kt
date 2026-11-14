package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.Recipient
import kotlinx.coroutines.delay

/** Everything the "New alias" tab can ask for; the ViewModel provides the real ones. */
data class CopyCallbacks(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCopy: (recipientId: String) -> Unit = {},
    val onOpenBilling: () -> Unit = {},
    /** The in-app recipient screen; [addNew] opens it with the "Add recipient" sheet. */
    val onOpenRecipients: (addNew: Boolean) -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

/** How long "Copied"/"Removed" stay. */
private const val CONFIRMATION_MILLIS = 3_000L

/**
 * The "New alias" tab: one card per confirmed recipient with its ready alias
 * (docs/flows.md, "New alias tab"), laid out like an authenticator app's
 * entries: who it is for on top, the whole address large below, a tap on the
 * card copies it. The cards stay in server order and sit together in the
 * middle of the screen; when there are more than fit, they start at the top
 * and scroll.
 */
@Composable
fun CopyScreen(state: CopyUiState, callbacks: CopyCallbacks, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            HomeHeader(
                title = "New alias",
                detail = "Tap an alias to copy it. A fresh one takes its place.",
                horizontalPadding = CardGutter
            )
        },
        bottomBar = { CopyStatusBar(state, callbacks) }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = callbacks.onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = OcaTheme.colors.card,
                    color = OcaTheme.colors.primary
                )
            }
        ) {
            CopyContent(state, callbacks)
        }
    }
}

/** What happened last (copied, removed, a failed reload), pinned above the tab bar. */
@Composable
private fun CopyStatusBar(state: CopyUiState, callbacks: CopyCallbacks) {
    val reloadError = state.loadError.takeIf { state.list == ListStatus.Ready }
    if (state.message == null && reloadError == null) return
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        Column(
            modifier = Modifier
                .widthIn(max = OcaSpacing.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
        ) {
            if (reloadError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OcaErrorText(text = reloadError, modifier = Modifier.weight(1f))
                    OcaButton(
                        text = "Retry",
                        onClick = callbacks.onRetry,
                        variant = OcaButtonVariant.Ghost,
                        enabled = !state.loading
                    )
                }
            }
            state.message?.let { MessageLine(it, callbacks.onDismissMessage) }
        }
    }
}

@Composable
private fun CopyContent(state: CopyUiState, callbacks: CopyCallbacks) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // At least the visible height, so a short content is centred in it.
                    .heightIn(min = this@BoxWithConstraints.maxHeight)
                    .padding(horizontal = CardGutter, vertical = OcaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OcaSpacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val itemModifier = Modifier.widthIn(max = OcaSpacing.maxContentWidth).fillMaxWidth()
                when {
                    state.list == ListStatus.Failed ->
                        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)) {
                            OcaErrorText(text = state.loadError ?: AliasMessages.LOAD_FAILED)
                            OcaButton(
                                text = "Retry",
                                onClick = callbacks.onRetry,
                                variant = OcaButtonVariant.Outline,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                    state.list == ListStatus.Loading -> Text(
                        text = "Loading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OcaTheme.colors.mutedForeground,
                        modifier = itemModifier
                    )

                    state.paymentRequired ->
                        Column(itemModifier) { PaymentNotice(onOpenBilling = callbacks.onOpenBilling) }

                    state.noRecipient != null -> Column(itemModifier) {
                        NoRecipientNotice(
                            awaitingConfirmation = state.noRecipient,
                            onOpenRecipients = callbacks.onOpenRecipients
                        )
                    }

                    else -> state.cards.forEach { card ->
                        AliasCard(
                            card = card,
                            onCopy = { callbacks.onCopy(card.recipient.id) },
                            onRetry = callbacks.onRetry,
                            modifier = itemModifier
                        )
                    }
                }
            }
        }
    }
}

/** Narrower than the page gutter: the whole address gets as much width as the phone has. */
internal val CardGutter = OcaSpacing.md

/**
 * One entry, like in an authenticator app: the recipient's mark and address on
 * top with the copy icon, the whole alias below in Geist Mono, as large as one
 * line allows. The whole card is the button: it copies, or retries after an
 * error. Screen readers hear "Alias …, forwards to …".
 */
@Composable
private fun AliasCard(card: CopyCard, onCopy: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = OcaTheme.colors
    val shown = card.copied ?: card.alias
    val waitingOnline = card.copied != null && !card.working
    val justCopied = card.copied != null && card.working
    val onClick: (() -> Unit)? = when {
        card.error != null -> onRetry
        waitingOnline -> onCopy
        card.alias != null && !card.working -> onCopy
        else -> null
    }
    Column(
        modifier = modifier
            .background(c.card)
            .border(1.dp, if (justCopied) c.primary else c.border)
            .clickable(
                enabled = onClick != null,
                role = Role.Button,
                onClickLabel = if (card.error != null) "retry" else "copy",
                onClick = { onClick?.invoke() }
            )
            .padding(horizontal = OcaSpacing.md, vertical = OcaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
        ) {
            RecipientMark(card.recipient.email)
            Text(
                text = card.recipient.email,
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "forwards to ${card.recipient.email}" }
            )
            // Decorative: the card itself is the button.
            Icon(
                painter = painterResource(if (justCopied) R.drawable.ic_check else R.drawable.ic_copy),
                contentDescription = null,
                tint = when {
                    justCopied -> c.primary
                    onClick == null -> c.mutedForeground.copy(alpha = 0.4f)
                    else -> c.mutedForeground
                },
                modifier = Modifier.size(18.dp)
            )
        }
        when {
            shown != null -> FullAddress(shown.alias)

            card.error != null -> OcaErrorText(text = card.error, modifier = Modifier.heightIn(min = AddressHeight))

            else -> Text(
                text = "Getting your alias…",
                style = MaterialTheme.typography.bodyMedium,
                color = c.mutedForeground,
                modifier = Modifier.heightIn(min = AddressHeight)
            )
        }
        when {
            waitingOnline -> Text(
                text = "Copied. Go online to activate this alias — until then it doesn't receive mail. " +
                    "Tap to copy it again.",
                style = MaterialTheme.typography.bodySmall,
                color = c.destructive,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            card.error != null -> Text(
                text = "Tap to try again.",
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground
            )
        }
    }
}

/** Room for one line of the address at its largest size, kept while the alias is on its way. */
private val AddressHeight = 32.dp

/**
 * The whole alias in one line: shrinks from 24sp to fit the card instead of
 * wrapping, so it reads as one address like an authenticator's code.
 */
@Composable
private fun FullAddress(alias: String) {
    BasicText(
        text = alias,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            color = OcaTheme.colors.cardForeground
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp, stepSize = 0.5.sp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AddressHeight)
            .wrapContentHeight(Alignment.CenterVertically)
            .semantics { contentDescription = "Alias $alias" }
    )
}

@Composable
private fun MessageLine(message: CopyMessage, onDismiss: () -> Unit) {
    Text(
        text = when (message) {
            CopyMessage.Copied -> "Copied to clipboard"
            CopyMessage.Removed -> "The alias you copied was removed — copy the new one."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = OcaTheme.colors.mutedForeground,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    LaunchedEffect(message) {
        delay(CONFIRMATION_MILLIS)
        onDismiss()
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CopyScreenPreview() {
    val home = Recipient("r", "me@example.com", true, "2026-11-01T10:00:00Z")
    val work = Recipient("w", "work@example.com", true, "2026-11-01T10:00:00Z")
    OcaTheme {
        CopyScreen(
            state = CopyUiState(
                cards = listOf(
                    CopyCard(
                        home,
                        alias = Alias("a", "quiet-otter-1234@alias.oneclickalias.com", home.email, "r", true, "", "")
                    ),
                    CopyCard(
                        work,
                        copied = Alias("b", "brave-heron-5678@alias.oneclickalias.com", work.email, "w", true, "", "")
                    )
                ),
                list = ListStatus.Ready
            ),
            callbacks = CopyCallbacks()
        )
    }
}
