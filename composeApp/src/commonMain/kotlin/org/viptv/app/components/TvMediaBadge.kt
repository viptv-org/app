package org.viptv.app.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun TvMediaBadge(label: String, scale: Float) {
    Box(Modifier.height(30.dp * scale).border(1.5.dp * scale, Palette.text.copy(alpha = .55f), RoundedCornerShape(5.dp * scale))
        .padding(horizontal = 8.dp * scale), contentAlignment = Alignment.Center) {
        Text(label, color = Palette.body, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp,
            letterSpacing = (.36f * scale).sp, fontWeight = FontWeight.Bold)
    }
}
