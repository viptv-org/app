package org.viptv.app.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun MediaMetadata(card: Card, fontSize: Float = 14f, lineHeight: Float = 20f,
    badgeSize: Float = 10f, scale: Float = 1f) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        card.subtitle?.let { Text(it, fontSize = (fontSize * scale).sp, lineHeight = (lineHeight * scale).sp, color = Palette.body) }
        card.badges.forEach { badge ->
            Text(badge, Modifier.border(1.dp * scale, Palette.secondary.copy(alpha = 0.5f), RoundedCornerShape(3.dp * scale))
                .padding(horizontal = 4.dp * scale, vertical = 2.dp * scale),
                fontSize = (badgeSize * scale).sp, lineHeight = ((badgeSize + 4) * scale).sp,
                fontWeight = FontWeight.SemiBold, color = Palette.body)
        }
    }
}
