package com.oneclickalias.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Frame of the list screens under Account (recipients, devices): back bar with
 * [title], a pull-to-refresh list and a pinned [bottom] area above the
 * navigation bar, like the alias screen. List items should use
 * [Modifier.listItemWidth] so they stay readable on tablets.
 */
@Composable
fun OcaListScaffold(
    title: String,
    onBack: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(
                modifier = Modifier
                    .background(colors.background)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
            ) {
                OcaBackBar(onBack = onBack, title = title)
                HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bottom != null) {
                    HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
                    Column(
                        modifier = Modifier
                            .widthIn(max = OcaSpacing.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm),
                        content = bottom
                    )
                }
            }
        }
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = OcaTheme.colors.card,
                    color = OcaTheme.colors.primary
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + OcaSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

/** Width of an [OcaListScaffold] item: full width up to the content maximum. */
fun Modifier.listItemWidth(): Modifier = widthIn(max = OcaSpacing.maxContentWidth).fillMaxWidth()

/** Quiet muted text line in a list (loading, empty, intro). */
@Composable
fun OcaListStatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OcaTheme.colors.mutedForeground,
        modifier = modifier.listItemWidth().padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.lg)
    )
}

/** Error line with a Retry button: full block before the first load, compact above a loaded list. */
@Composable
fun OcaListError(text: String, retryEnabled: Boolean, onRetry: () -> Unit, compact: Boolean) {
    if (compact) {
        Row(
            modifier = Modifier.listItemWidth().padding(start = OcaSpacing.gutter, top = OcaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OcaErrorText(text = text, modifier = Modifier.weight(1f))
            OcaButton(text = "Retry", onClick = onRetry, variant = OcaButtonVariant.Ghost, enabled = retryEnabled)
        }
    } else {
        Column(
            modifier = Modifier.listItemWidth().padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
        ) {
            OcaErrorText(text = text)
            OcaButton(text = "Retry", onClick = onRetry, variant = OcaButtonVariant.Outline, enabled = retryEnabled)
        }
    }
}
