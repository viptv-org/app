package org.viptv.app.features.details

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
internal fun TvDetailMetadata(card: Card, scale: Float) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(30.dp * scale), verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        card.subtitle?.let { Text(it, color = Palette.body, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Medium) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            card.badges.forEach { badge ->
                Text(badge, Modifier.border(1.5.dp * scale, Palette.secondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp * scale))
                    .padding(horizontal = 6.dp * scale, vertical = 2.dp * scale), color = Palette.body,
                    fontSize = (18 * scale).sp, lineHeight = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
