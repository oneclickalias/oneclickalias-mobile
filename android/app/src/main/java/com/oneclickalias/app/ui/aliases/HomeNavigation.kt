package com.oneclickalias.app.ui.aliases

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/** The three areas of home, in the order of the bottom bar. */
enum class HomeTab(val label: String, @param:DrawableRes val icon: Int) {
    /** A fresh alias per recipient, copied in one tap. */
    NewAlias("New alias", R.drawable.ic_cursor),

    /** Every alias in use, by recipient. */
    Aliases("Aliases", R.drawable.ic_list),

    Account("Account", R.drawable.ic_account)
}

/**
 * Frame of home: the open tab's [content] and the bar that switches between
 * them, at the bottom where a thumb reaches it. The bar takes the bottom
 * inset, so the tabs inside only pad the top and the sides.
 */
@Composable
fun HomeFrame(
    tab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) { content() }
        HomeBottomBar(tab = tab, onSelectTab = onSelectTab)
    }
}

/** Icon above label per tab; the open one is in the foreground color under a short bar. */
@Composable
private fun HomeBottomBar(tab: HomeTab, onSelectTab: (HomeTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        Row(modifier = Modifier.widthIn(max = OcaSpacing.maxContentWidth).fillMaxWidth().selectableGroup()) {
            HomeTab.entries.forEach { entry ->
                HomeBarItem(
                    tab = entry,
                    selected = entry == tab,
                    onClick = { onSelectTab(entry) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HomeBarItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (selected) MaterialTheme.colorScheme.onBackground else OcaTheme.colors.mutedForeground
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The marker hangs from the bar's top line, above the open tab.
        Box(
            Modifier
                .size(width = 32.dp, height = 2.dp)
                .background(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
        )
        Spacer(Modifier.height(OcaSpacing.sm - 2.dp))
        // Decorative: the label below names the tab.
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(top = OcaSpacing.xxs, bottom = OcaSpacing.xs)
        )
    }
}

/**
 * Head of a home tab: its name, and optionally one muted line saying what the
 * tab is for. Pads the status bar itself; [horizontalPadding] lines it up with
 * the tab's content.
 */
@Composable
fun HomeHeader(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    horizontalPadding: Dp = OcaSpacing.gutter
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = OcaSpacing.maxContentWidth)
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = OcaSpacing.lg,
                    bottom = OcaSpacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OcaTheme.colors.mutedForeground
                )
            }
        }
    }
}
