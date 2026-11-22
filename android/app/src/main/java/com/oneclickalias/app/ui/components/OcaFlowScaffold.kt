package com.oneclickalias.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.Locale

/**
 * Page frame of the sign-up/login screens: [topBar], scrollable [content] and
 * a [bottom] area (main buttons) pinned under it. The frame pads with
 * `safeDrawing`, which includes the keyboard, so the bottom buttons sit right
 * above the keyboard instead of behind it. [horizontalPadding] is the side
 * gutter of content and bottom area.
 */
@Composable
fun OcaFlowScaffold(
    topBar: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = OcaSpacing.gutter,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = OcaSpacing.maxContentWidth).fillMaxWidth().weight(1f)) {
            topBar()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = OcaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OcaSpacing.xl),
                content = content
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = OcaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OcaSpacing.sm),
                content = bottom
            )
        }
    }
}

/** Small uppercase eyebrow plus the screen title, like the website's card headers. */
@Composable
fun OcaScreenHeading(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
        Text(
            text = eyebrow.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            color = OcaTheme.colors.mutedForeground
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Error line in the destructive color, announced by screen readers when it appears. */
@Composable
fun OcaErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = OcaTheme.colors.destructive,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
