package com.oneclickalias.app.ui.aliases

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaTheme

private const val ZERO_WIDTH_SPACE = '​'
private const val WORD_JOINER = '⁠'

/**
 * [alias] for display: a break opportunity right before the last `@` and none
 * inside the domain, so an address too long for one line wraps as
 * `local-part` / `@domain` instead of in the middle of the domain. Only
 * invisible characters are added; copy and screen readers use the plain alias.
 */
fun aliasDisplayText(alias: String): String {
    val at = alias.lastIndexOf('@')
    if (at < 0) return alias
    val domain = alias.substring(at + 1).toList().joinToString(WORD_JOINER.toString())
    return alias.substring(0, at) + ZERO_WIDTH_SPACE + "@" + WORD_JOINER + domain
}

/**
 * An alias address in a list row: Geist Mono at 13sp, so the longest diceware
 * aliases (`word-word-NNNN@domain`, words of up to 9 letters) fit in one line
 * inside a group card on a 384dp phone. Longer ones wrap before the `@`, then end with an ellipsis.
 * The domain is muted: it is the same for every alias, the name tells them apart.
 */
@Composable
internal fun AliasAddress(alias: String, modifier: Modifier = Modifier) {
    val shown = aliasDisplayText(alias)
    val at = shown.lastIndexOf('@')
    Text(
        text = buildAnnotatedString {
            append(shown)
            if (at >= 0) addStyle(SpanStyle(color = OcaTheme.colors.mutedForeground), at, shown.length)
        },
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistMono, fontSize = 13.sp, lineHeight = 20.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
