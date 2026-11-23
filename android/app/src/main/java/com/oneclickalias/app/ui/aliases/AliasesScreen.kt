package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.data.aliases.AliasGroup
import com.oneclickalias.app.data.aliases.groupByRecipient
import com.oneclickalias.app.ui.components.OcaBadge
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaEmptyState
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaSkeleton
import com.oneclickalias.app.ui.components.OcaSkeletonBlock
import com.oneclickalias.app.ui.components.OcaSkeletonSquare
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
    val onDismissMessage: () -> Unit = {},
    /** Opens or closes a recipient group, by [AliasGroup.key]. */
    val onToggleGroup: (key: String) -> Unit = {},
    /** The empty list's way to the "New alias" tab. */
    val onOpenNewAlias: () -> Unit = {}
)

/**
 * The "Aliases" tab: every active alias, grouped by the address it forwards
 * to, newest first. Each group is a card whose head names the recipient and
 * counts its aliases; a tap on the head opens or closes the group (all start
 * closed; a lone group is always open). An open head stays at the top while
 * its aliases scroll. A tap on a row copies the alias; "⋯" or a long press
 * opens its actions. New aliases come from the "New alias" tab.
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
                    // The payment notice takes the middle; the head still says why the list is empty.
                    empty && state.paymentRequired -> "No aliases yet. Copy your first one under New alias."

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
                // Air under the last card; none on an empty list, whose notice is centred in the
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

            state.list == ListStatus.Loading -> item(key = "loading") { AliasListSkeleton(itemModifier) }

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
                if (groups.isEmpty()) {
                    // Same place as the notices on the "New alias" tab: the middle of the screen.
                    item(key = if (state.paymentRequired) "payment" else "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize().padding(horizontal = CardGutter),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(itemModifier) {
                                if (state.paymentRequired) {
                                    PaymentNotice(onOpenBilling = callbacks.onOpenBilling)
                                } else {
                                    OcaEmptyState(
                                        icon = R.drawable.ic_list,
                                        title = "No aliases yet",
                                        body = "Aliases you copy under New alias show up here, grouped by the " +
                                            "inbox they forward to.",
                                        action = "Go to New alias",
                                        onAction = callbacks.onOpenNewAlias
                                    )
                                }
                            }
                        }
                    }
                }
                val single = groups.size == 1
                groups.forEach { group ->
                    aliasGroup(
                        group = group,
                        expanded = single || group.key in state.expandedGroups,
                        onToggle = if (single) null else ({ callbacks.onToggleGroup(group.key) }),
                        callbacks = callbacks,
                        itemModifier = itemModifier
                    )
                }
            }
        }
    }
}

/**
 * The head of a group card, then (when open) its aliases. The head stays at
 * the top while an open group's card scrolls under it.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.aliasGroup(
    group: AliasGroup,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    callbacks: AliasesCallbacks,
    itemModifier: Modifier
) {
    stickyHeader(key = "to ${group.key}", contentType = "recipient") {
        GroupHead(group = group, expanded = expanded, onToggle = onToggle, modifier = itemModifier)
    }
    if (!expanded) return
    val lastIndex = group.aliases.lastIndex
    itemsIndexed(group.aliases, key = { _, alias -> alias.id }, contentType = { _, _ -> "alias" }) { index, alias ->
        AliasRow(
            alias = alias,
            onCopy = { callbacks.onCopy(alias) },
            onOpenActions = { callbacks.onOpenActions(alias.id) },
            modifier = itemModifier
                .padding(horizontal = CardGutter)
                .groupCell(last = index == lastIndex)
        )
    }
}

/**
 * One alias row's share of the group card: the card fill, the side lines, a
 * full bottom line on the last row and an inset line between rows (it starts
 * where the text starts). The head above draws the top line.
 */
@Composable
private fun Modifier.groupCell(last: Boolean): Modifier {
    val c = OcaTheme.colors
    return drawBehind {
        val line = 1.dp.toPx()
        drawRect(c.card)
        drawRect(c.border, size = Size(line, size.height))
        drawRect(c.border, topLeft = Offset(size.width - line, 0f), size = Size(line, size.height))
        val start = if (last) 0f else RowStartPadding.toPx()
        drawRect(
            c.border,
            topLeft = Offset(start, size.height - line),
            size = Size(size.width - start - if (last) 0f else line, line)
        )
    }
}

/** The head cell of a group card: fill and lines all round (the line under it separates it from the aliases). */
@Composable
private fun Modifier.headCell(): Modifier {
    val c = OcaTheme.colors
    return drawBehind {
        val line = 1.dp.toPx()
        drawRect(c.card)
        drawRect(c.border, size = Size(line, size.height))
        drawRect(c.border, topLeft = Offset(size.width - line, 0f), size = Size(line, size.height))
        drawRect(c.border, size = Size(size.width, line))
        drawRect(c.border, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
    }
}

/** Space between the card's edge and an alias; the inset divider starts here too. */
private val RowStartPadding = 14.dp

/** Gap between two group cards, on the page background. */
private val GroupGap = OcaSpacing.sm

/**
 * Mark, address, count and (for a group that can close) a chevron. A tap
 * opens or closes the group; screen readers hear the address, the count and
 * whether it is open.
 */
@Composable
private fun GroupHead(group: AliasGroup, expanded: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier) {
    val count = group.aliases.size
    val description = "Forwards to ${group.destEmail}, $count ${if (count == 1) "alias" else "aliases"}"
    // The gap above sits on the page background, so a head stuck at the top hides what scrolls under it.
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background).padding(top = GroupGap)) {
        Row(
            modifier = Modifier
                .padding(horizontal = CardGutter)
                .fillMaxWidth()
                .headCell()
                .then(
                    if (onToggle != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = if (expanded) "collapse" else "expand",
                            onClick = onToggle
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    heading()
                    if (onToggle != null) stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .heightIn(min = 56.dp)
                .padding(start = RowStartPadding, end = OcaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
        ) {
            Row(
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
            ) {
                RecipientMark(group.destEmail, size = 24.dp)
                Text(
                    text = group.destEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                OcaBadge(text = count.toString())
            }
            if (onToggle != null) {
                // Decorative: the state description says whether the group is open.
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = OcaTheme.colors.mutedForeground,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f)
                )
            }
        }
    }
}

/** Closed group heads as placeholders: the shape most accounts see first. */
@Composable
private fun AliasListSkeleton(modifier: Modifier = Modifier) {
    OcaSkeleton(description = "Loading your aliases", modifier = modifier) {
        listOf(160.dp, 128.dp, 176.dp).forEach { width ->
            Spacer(Modifier.height(GroupGap))
            Row(
                modifier = Modifier
                    .padding(horizontal = CardGutter)
                    .fillMaxWidth()
                    .headCell()
                    .heightIn(min = 56.dp)
                    .padding(start = RowStartPadding, end = OcaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
            ) {
                OcaSkeletonSquare(size = 24.dp)
                OcaSkeletonBlock(height = 12.dp, width = width)
            }
        }
    }
}

/**
 * One alias: the address in Geist Mono ([AliasAddress]: up to two lines,
 * wrapping before the `@`, then an ellipsis), and a badge when it lets
 * trackers through; where it forwards to is the head of the card. Screen
 * readers hear "Alias …, forwards to …, double tap to copy"; the actions are
 * the long-press action and the separate "⋯" button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AliasRow(alias: Alias, onCopy: () -> Unit, onOpenActions: () -> Unit, modifier: Modifier = Modifier) {
    val description = buildString {
        append("Alias ${alias.alias}, forwards to ${alias.destEmail}")
        if (!alias.blockTrackers) append(", trackers allowed")
    }
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = OcaSpacing.sm)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            AliasAddress(alias = alias.alias)
            if (!alias.blockTrackers) OcaBadge(text = "Trackers allowed")
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
        Alias("a$it", "quiet-otter-$it$it$it$it@alias.oneclickalias.com", to.email, to.id, it != 4, "", "")
    }
    OcaTheme {
        AliasesScreen(
            state = AliasesUiState(
                aliases = aliases,
                list = ListStatus.Ready,
                expandedGroups = setOf("me@example.com")
            ),
            callbacks = AliasesCallbacks()
        )
    }
}
