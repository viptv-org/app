package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.components.TvMediaMetadata
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvBrowseSummary(card: Card, original: Card, scale: Float) {
    Box(Modifier.fillMaxWidth().height(508.dp * scale)) {
        if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.offset(96.dp * scale, 96.dp * scale).size(420.dp * scale, 120.dp * scale)
            .graphicsLayer { blendMode = BlendMode.Lighten; compositingStrategy = CompositingStrategy.Offscreen }, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
        else Text(card.title, Modifier.offset(96.dp * scale, 96.dp * scale).width(1000.dp * scale), fontSize = (48 * scale).sp, lineHeight = (58 * scale).sp, fontWeight = FontWeight.Bold)
        Box(Modifier.offset(96.dp * scale, 240.dp * scale)) { TvMediaMetadata(card, scale) }
        card.description?.let { Text(it, Modifier.offset(96.dp * scale, 290.dp * scale).width(800.dp * scale), color = Palette.body,
            fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        val progress = original.progress ?: card.progress
        if (progress != null && original.subtitle != null) Row(Modifier.offset(96.dp * scale, 418.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.width(160.dp * scale).height(4.dp * scale),
                color = Palette.accent, trackColor = Palette.text.copy(alpha = 0.3f))
            Text("Resume ${original.subtitle}", fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
