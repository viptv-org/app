package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun TvSettingsHeading(title: String, scale: Float, subtitle: String? = null, reserveSubtitle: Boolean = false) {
    Column(Modifier.fillMaxWidth().heightIn(min = (if (subtitle != null || reserveSubtitle) 112 else 48).dp * scale),
        verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Text(title, Modifier.offset(y = (-3).dp * scale), fontSize = (40 * scale).sp, lineHeight = (48 * scale).sp,
            letterSpacing = (-0.8f * scale).sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary) }
    }
}
