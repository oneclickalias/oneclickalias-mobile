package com.oneclickalias.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * Flat bottom sheet: card surface, square corners, no elevation, like the
 * website's dialogs. Open while it is in the composition; it closes by state
 * (no hide animation). The sheet is its own window, so never show an account
 * number in it (`SecureWindow` only covers the activity window).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcaSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = OcaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = c.card,
        contentColor = c.cardForeground,
        tonalElevation = 0.dp,
        // A dark page darkens little under the default scrim; this one sets the sheet apart.
        scrimColor = Color.Black.copy(alpha = SCRIM_ALPHA),
        // The top line separates the card-colored sheet from the near-black page.
        modifier = Modifier.border(1.dp, c.border),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = OcaSpacing.lg),
            content = content
        )
    }
}

private const val SCRIM_ALPHA = 0.6f

/** Title line of an [OcaSheet]. */
@Composable
fun OcaSheetTitle(text: String) {
    OcaSheetHeader(title = text)
}

/**
 * Head of an [OcaSheet]: what the sheet is about as [title] (Geist Mono, e.g.
 * an alias or an address), and an optional line under it ([subtitle]).
 * [titleStyle] lets an address use the body size so it fits.
 */
@Composable
fun OcaSheetHeader(
    title: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    subtitle: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OcaSpacing.gutter, end = OcaSpacing.gutter, top = OcaSpacing.xs, bottom = OcaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)
    ) {
        Text(text = title, style = titleStyle, modifier = Modifier.semantics { heading() })
        subtitle?.invoke()
    }
}
