package com.oneclickalias.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.theme.OcaSpacing

/** Plain top row with a back arrow and an optional [title]; no elevation. */
@Composable
fun OcaBackBar(onBack: () -> Unit, modifier: Modifier = Modifier, title: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp).padding(horizontal = OcaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // IconButton is 48dp: the minimum touch target.
        IconButton(onClick = onBack) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = OcaSpacing.xxs).semantics { heading() }
            )
        }
    }
}
