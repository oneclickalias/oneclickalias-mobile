package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.components.OcaButton
import com.oneclickalias.app.ui.components.OcaButtonVariant
import com.oneclickalias.app.ui.components.OcaErrorText
import com.oneclickalias.app.ui.components.OcaSheet
import com.oneclickalias.app.ui.components.OcaSheetTitle
import com.oneclickalias.app.ui.components.OcaSwitchRow
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import com.oneclickalias.contracts.ams.Recipient

/** The bottom sheets of the alias screen; open while their state is set. */
@Composable
internal fun AliasSheets(state: AliasesUiState, callbacks: AliasesCallbacks) {
    state.actions?.let { actions ->
        OcaSheet(onDismiss = callbacks.onDismissActions) {
            AliasActionsContent(actions = actions, callbacks = callbacks)
        }
    }
}

@Composable
private fun AliasActionsContent(actions: AliasActions, callbacks: AliasesCallbacks) {
    val alias = actions.alias
    Column(
        modifier = Modifier.padding(horizontal = OcaSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(OcaSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xxs)) {
            Text(
                text = aliasDisplayText(alias.alias),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = GeistMono, fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Forwards to ${alias.destEmail}",
                style = MaterialTheme.typography.bodySmall,
                color = OcaTheme.colors.mutedForeground
            )
        }
        if (actions.confirmingDelete) {
            DeleteConfirmation(actions = actions, callbacks = callbacks)
        } else {
            TrackerSwitch(
                checked = alias.blockTrackers,
                enabled = !actions.saving && !actions.deleting,
                onChange = callbacks.onBlockTrackers
            )
            OcaButton(
                text = "Copy alias",
                onClick = {
                    callbacks.onCopy(alias)
                    callbacks.onDismissActions()
                },
                variant = OcaButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth()
            )
            OcaButton(
                text = "Delete alias",
                onClick = callbacks.onRequestDelete,
                variant = OcaButtonVariant.Destructive,
                enabled = !actions.saving,
                modifier = Modifier.fillMaxWidth()
            )
        }
        actions.error?.let { OcaErrorText(text = it) }
    }
}

@Composable
private fun TrackerSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    OcaSwitchRow(
        title = "Block trackers",
        detail = "Removes tracking pixels and tracking links from forwarded mail.",
        checked = checked,
        enabled = enabled,
        onChange = onChange
    )
}

/** Same wording as the website (OCA-frontend/src/delete-alias-dialog.tsx). */
@Composable
private fun DeleteConfirmation(actions: AliasActions, callbacks: AliasesCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(OcaSpacing.xs)) {
        Text(
            text = "Delete alias?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "${actions.alias.alias} stops receiving mail right away. " +
                "This can't be undone, and the name won't be reused.",
            style = MaterialTheme.typography.bodyMedium,
            color = OcaTheme.colors.mutedForeground
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)) {
        OcaButton(
            text = "Cancel",
            onClick = callbacks.onCancelDelete,
            variant = OcaButtonVariant.Outline,
            enabled = !actions.deleting,
            modifier = Modifier.weight(1f)
        )
        OcaButton(
            text = if (actions.deleting) "Deleting…" else "Delete",
            onClick = callbacks.onConfirmDelete,
            variant = OcaButtonVariant.Destructive,
            enabled = !actions.deleting,
            modifier = Modifier.weight(1f)
        )
    }
}
