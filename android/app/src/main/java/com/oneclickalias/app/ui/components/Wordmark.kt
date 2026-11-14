package com.oneclickalias.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneclickalias.app.R
import com.oneclickalias.app.ui.theme.OcaSpacing

// Width / height of the cursor logo (viewBox 600 x 950 in OCA-frontend/public/logo.svg).
private const val LOGO_ASPECT_RATIO = 600f / 950f

/** Logo plus "OneClickAlias" in Geist Mono, the lockup from the website navbar. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
    logoHeight: Dp = 32.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OcaSpacing.sm)
    ) {
        // Decorative: the text next to it already names the app.
        Image(
            painter = painterResource(R.drawable.oca_logo),
            contentDescription = null,
            modifier = Modifier.height(logoHeight).aspectRatio(LOGO_ASPECT_RATIO)
        )
        Text(text = "OneClickAlias", style = style, maxLines = 1)
    }
}
