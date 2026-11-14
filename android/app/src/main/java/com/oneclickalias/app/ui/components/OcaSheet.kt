package com.oneclickalias.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = OcaSpacing.lg),
            content = content
        )
    }
}

/** Title line of an [OcaSheet]. */
@Composable
fun OcaSheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = OcaSpacing.gutter, vertical = OcaSpacing.xs)
            .semantics { heading() }
    )
}
