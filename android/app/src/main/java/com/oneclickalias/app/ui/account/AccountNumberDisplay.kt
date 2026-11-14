package com.oneclickalias.app.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.theme.GeistMono
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme

/**
 * The account number large in Geist Mono, grouped in fours, inside a dashed
 * frame (like the website's account-number card). Screen readers get it digit
 * by digit per group. Callers must also hold [com.oneclickalias.app.platform.SecureWindow].
 *
 * @param onCopy shows a copy icon at the end of the frame; [copied] turns it into a check.
 */
@Composable
fun AccountNumberDisplay(
    digits: String,
    modifier: Modifier = Modifier,
    onCopy: (() -> Unit)? = null,
    copied: Boolean = false
) {
    val c = OcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .drawBehind {
                drawRect(
                    color = c.foreground.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                )
            }
            .padding(start = OcaSpacing.md, end = if (onCopy == null) OcaSpacing.md else OcaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = OcaSpacing.xl)
                .semantics(mergeDescendants = true) {
                    // Read instead of the grouped text: "4 7 1 1, 0 0 0 0, ..."
                    contentDescription = "Account number: ${AccountNumberFormat.spoken(digits)}"
                    isSensitiveData = true
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = AccountNumberFormat.grouped(digits),
                style = NumberStyle.copy(color = c.foreground),
                maxLines = 1,
                // One line on every width and font scale: shrink instead of wrapping mid-number.
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp)
            )
        }
        if (onCopy != null) {
            // 48dp touch target.
            IconButton(
                onClick = onCopy,
                modifier = Modifier.semantics { if (copied) liveRegion = LiveRegionMode.Polite }
            ) {
                Icon(
                    painter = painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_copy),
                    contentDescription = if (copied) "Account number copied" else "Copy account number",
                    tint = if (copied) c.primary else c.mutedForeground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val NumberStyle = TextStyle(
    fontFamily = GeistMono,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.05.em,
    textAlign = TextAlign.Center
)
