package com.oneclickalias.app.ui.aliases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * The letter that stands for a recipient: the first letter of its mail
 * provider ("outlook.de" → O), like the issuer icon in an authenticator app.
 * Addresses of one provider share it; the address next to it tells them apart.
 */
fun recipientInitial(email: String): Char {
    val domain = email.substringAfterLast('@', missingDelimiterValue = "")
    return (domain.firstOrNull { it.isLetterOrDigit() } ?: email.firstOrNull { it.isLetterOrDigit() })
        ?.uppercaseChar() ?: '?'
}

/** [recipientInitial] on a small muted square; decorative, the address is always next to it. */
@Composable
internal fun RecipientMark(email: String, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(OcaTheme.colors.muted)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = recipientInitial(email).toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = GeistMono, fontWeight = FontWeight.SemiBold),
            color = OcaTheme.colors.mutedForeground
        )
    }
}
