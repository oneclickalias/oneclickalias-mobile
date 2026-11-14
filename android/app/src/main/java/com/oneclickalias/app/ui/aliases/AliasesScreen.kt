package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.data.aliases.AliasGroup
import com.oneclickalias.app.data.aliases.groupByRecipient
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.Recipient

/** Everything the alias screen can ask for; the ViewModel provides the real ones. */
data class AliasesCallbacks(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCopy: (Alias) -> Unit = {},
    val onOpenActions: (aliasId: String) -> Unit = {},
    val onDismissActions: () -> Unit = {},
    val onBlockTrackers: (Boolean) -> Unit = {},
    val onRequestDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenBilling: () -> Unit = {},
    val onDismissMessage: () -> Unit = {}
)

/**
 * The "Aliases" tab: every active alias, grouped by the address it forwards
 * to, newest first. Each group is a card under a quiet label with the
 * recipient; the label stays at the top while its card scrolls. A tap on a
 * row copies the alias; "⋯" or a long press opens its actions. New aliases
 * come from the "New alias" tab.
 */
@Composable
fun AliasesScreen(state: AliasesUiState, callbacks: AliasesCallbacks, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val empty = state.list == ListStatus.Ready && state.aliases.isEmpty()
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        // The bars pad themselves; the list gets their heights as content padding.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            HomeHeader(
                title = "Aliases",
                detail = when {
                    empty -> "No aliases yet. Copy your first one under New alias."
                    state.aliases.isNotEmpty() -> "Tap an alias to copy it."
                    else -> null
                },
                horizontalPadding = CardGutter
            )
        },
        // An empty list shows the payment notice in the middle instead.
        bottomBar = { AliasesBottomBar(state = state, callbacks = callbacks, showPayment = !empty) }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = callbacks.onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
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
            AliasList(
                state = state,
                callbacks = callbacks,
                // Air under the last card; none on an empty list, whose payment notice is centred in the
                // whole space like on the "New alias" tab (fillParentMaxSize leaves out the padding).
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + if (empty) 0.dp else OcaSpacing.md
                )
            )
        }
    }
    AliasSheets(state = state, callbacks = callbacks)
}

@Composable
private fun AliasList(state: AliasesUiState, callbacks: AliasesCallbacks, contentPadding: PaddingValues) {
    val groups = remember(state.aliases) { groupByRecipient(state.aliases) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val itemModifier = Modifier.widthIn(max = OcaSpacing.maxContentWidth).fillMaxWidth()
        when {
            state.list == ListStatus.Failed -> item(key = "failed") {
                Column(
                    modifier = itemModifier.padding(horizontal = CardGutter, vertical = OcaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
                ) {
                    OcaErrorText(text = state.listError ?: AliasMessages.LOAD_FAILED)
                    OcaButton(
                        text = "Retry",
                        onClick = callbacks.onRetry,
                        variant = OcaButtonVariant.Outline,
                        enabled = !state.loading
                    )
                }
            }

            state.list == ListStatus.Loading -> item(key = "loading") {
                StatusLine(text = "Loading your aliases…", modifier = itemModifier)
            }

            else -> {
                state.listError?.let { error ->
                    item(key = "reload-error") {
                        Row(
                            modifier = itemModifier.padding(start = CardGutter, top = OcaSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OcaErrorText(text = error, modifier = Modifier.weight(1f))
                            OcaButton(
                                text = "Retry",
                                onClick = callbacks.onRetry,
                                variant = OcaButtonVariant.Ghost,
                                enabled = !state.loading
                            )
                        }
                    }
                }
                if (groups.isEmpty() && state.paymentRequired) {
                    // Same place as on the "New alias" tab: the middle of the screen.
                    item(key = "payment") {
                        Box(
                            modifier = Modifier.fillParentMaxSize().padding(horizontal = CardGutter),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(itemModifier) { PaymentNotice(onOpenBilling = callbacks.onOpenBilling) }
                        }
                    }
                }
                groups.forEach { group -> aliasGroup(group, callbacks, itemModifier) }
            }
        }
    }
}

/** The recipient as a label that stays at the top while its card scrolls, then the card of aliases. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.aliasGroup(group: AliasGroup, callbacks: AliasesCallbacks, itemModifier: Modifier) {
    stickyHeader(key = "to ${group.destEmail}", contentType = "recipient") {
        RecipientLabel(group = group, modifier = itemModifier)
    }
    val lastIndex = group.aliases.lastIndex
    itemsIndexed(group.aliases, key = { _, alias -> alias.id }, contentType = { _, _ -> "alias" }) { index, alias ->
        AliasRow(
            alias = alias,
            onCopy = { callbacks.onCopy(alias) },
            onOpenActions = { callbacks.onOpenActions(alias.id) },
            modifier = itemModifier
                .padding(horizontal = CardGutter)
                .groupCell(first = index == 0, last = index == lastIndex)
        )
    }
}

/**
 * One row's share of the group card: the card fill, the side lines, the top
 * line on the first row, a full bottom line on the last and an inset line
 * between rows (it starts where the text starts).
 */
@Composable
private fun Modifier.groupCell(first: Boolean, last: Boolean): Modifier {
    val c = OcaTheme.colors
    return drawBehind {
        val line = 1.dp.toPx()
        drawRect(c.card)
        drawRect(c.border, size = Size(line, size.height))
        drawRect(c.border, topLeft = Offset(size.width - line, 0f), size = Size(line, size.height))
        if (first) drawRect(c.border, size = Size(size.width, line))
        val start = if (last) 0f else RowStartPadding.toPx()
        drawRect(
            c.border,
            topLeft = Offset(start, size.height - line),
            size = Size(size.width - start - if (last) 0f else line, line)
        )
    }
}

/** Space between the card's edge and an alias; the inset divider starts here too. */
private val RowStartPadding = 14.dp

/** Mark, address and count above a group card, on the page background. */
@Composable
private fun RecipientLabel(group: AliasGroup, modifier: Modifier = Modifier) {
    val count = group.aliases.size
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(start = CardGutter, end = CardGutter, top = OcaSpacing.lg, bottom = OcaSpacing.xs)
            .clearAndSetSemantics {
                contentDescription = "Forwards to ${group.destEmail}, $count ${if (count == 1) "alias" else "aliases"}"
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.xs)
    ) {
        RecipientMark(group.destEmail, size = 20.dp)
        Text(
            text = group.destEmail,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = OcaTheme.colors.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistMono),
            color = OcaTheme.colors.mutedForeground
        )
    }
}

@Composable
private fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OcaTheme.colors.mutedForeground,
        modifier = modifier.padding(horizontal = CardGutter, vertical = OcaSpacing.xs)
    )
}

/**
 * One alias: the address in Geist Mono ([AliasAddress]: up to two lines,
 * wrapping before the `@`, then an ellipsis); where it forwards to is the
 * label above the card. Screen readers hear "Alias …, forwards to …, double
 * tap to copy"; the actions are the long-press action and the separate "⋯"
 * button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AliasRow(alias: Alias, onCopy: () -> Unit, onOpenActions: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .combinedClickable(
                onClickLabel = "copy",
                onLongClickLabel = "show actions",
                onLongClick = onOpenActions,
                onClick = onCopy
            )
            .heightIn(min = 52.dp)
            .padding(start = RowStartPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = OcaSpacing.sm)
                .clearAndSetSemantics { contentDescription = "Alias ${alias.alias}, forwards to ${alias.destEmail}" }
        ) {
            AliasAddress(alias = alias.alias)
        }
        // 48dp touch target.
        IconButton(onClick = onOpenActions) {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = "Actions for ${alias.alias}",
                tint = OcaTheme.colors.mutedForeground
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun AliasesScreenPreview() {
    val home = Recipient("r", "me@example.com", true, "2026-11-01T10:00:00Z")
    val work = Recipient("w", "work@example.com", true, "2026-11-01T10:00:00Z")
    val aliases = (1..5).map {
        val to = if (it < 3) work else home
        Alias("a$it", "quiet-otter-$it$it$it$it@alias.oneclickalias.com", to.email, to.id, true, "", "")
    }
    OcaTheme {
        AliasesScreen(
            state = AliasesUiState(aliases = aliases, list = ListStatus.Ready),
            callbacks = AliasesCallbacks()
        )
    }
}
