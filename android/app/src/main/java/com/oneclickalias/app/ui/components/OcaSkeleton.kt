package com.oneclickalias.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Placeholder for content that is still loading, shaped like it: static
 * blocks in the `muted` color, no shimmer. Screen readers hear only
 * [description] (e.g. "Loading your aliases"), not the blocks.
 */
@Composable
fun OcaSkeleton(
    description: String,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** One placeholder block; [width] null fills the available width. */
@Composable
fun OcaSkeletonBlock(height: Dp, modifier: Modifier = Modifier, width: Dp? = null) {
    Box(
        modifier = modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .background(OcaTheme.colors.muted)
    )
}

/**
 * Placeholder rows of an [OcaListScaffold] list (recipients, devices): a title
 * and a detail line per row, the row height and dividers of the real rows.
 */
@Composable
fun OcaSkeletonRows(description: String, modifier: Modifier = Modifier, rows: Int = 3, topDivider: Boolean = true) {
    OcaSkeleton(description = description, modifier = modifier.listItemWidth()) {
        if (topDivider) HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        repeat(rows) { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs, Alignment.CenterVertically)
            ) {
                // Different widths per row, so the block reads as a list and not as one shape.
                OcaSkeletonBlock(height = 14.dp, width = SkeletonTitleWidths[index % SkeletonTitleWidths.size])
                OcaSkeletonBlock(height = 10.dp, width = 96.dp)
            }
            HorizontalDivider(thickness = 1.dp, color = OcaTheme.colors.border)
        }
    }
}

/** A small square placeholder, e.g. for a recipient mark. */
@Composable
fun OcaSkeletonSquare(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).background(OcaTheme.colors.muted))
}

private val SkeletonTitleWidths = listOf(180.dp, 140.dp, 200.dp)
