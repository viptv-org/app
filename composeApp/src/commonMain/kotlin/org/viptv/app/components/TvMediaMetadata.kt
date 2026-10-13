package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvMediaMetadata(card: Card, scale: Float, lineHeight: Float = 30f) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        card.subtitle?.let { Text(it, fontSize = (24 * scale).sp, lineHeight = (lineHeight * scale).sp,
            fontWeight = FontWeight.Medium, color = Palette.body) }
        card.badges.forEach { badge -> Box(Modifier.height(maxOf(30f, lineHeight).dp * scale), contentAlignment = Alignment.Center) {
            TvMediaBadge(badge, scale)
        } }
    }
}
