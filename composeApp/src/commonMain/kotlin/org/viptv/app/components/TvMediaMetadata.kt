package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvMediaMetadata(card: Card, scale: Float, lineHeight: Float = 30f, subtitleParts: List<String>? = null) {
    val parts = subtitleParts ?: card.metadataParts()
    val badges = card.presentationBadges()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        if (parts.isNotEmpty()) {
            val fixedLines = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(
                LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Fixed))
            if (parts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                parts.forEachIndexed { index, part ->
                    if (index > 0) Text("·", color = Color(0xFF8F8D89), fontSize = (24 * scale).sp, lineHeight = (lineHeight * scale).sp, style = fixedLines)
                    Text(part, fontSize = (24 * scale).sp, lineHeight = (lineHeight * scale).sp, fontWeight = FontWeight.Medium, color = Palette.body, style = fixedLines)
                }
            }
            if (badges.isNotEmpty()) Spacer(Modifier.width(6.dp * scale))
        }
        badges.forEach { badge -> Box(Modifier.height(maxOf(30f, lineHeight).dp * scale), contentAlignment = Alignment.Center) {
            TvMediaBadge(badge, scale)
        } }
    }
}
