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
internal fun TvBrowseSummary(card: Card, scale: Float) {
    Box(Modifier.fillMaxWidth().height(508.dp * scale)) {
        if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.offset(96.dp * scale, 96.dp * scale).size(420.dp * scale, 120.dp * scale)
            , contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
        else Box(Modifier.offset(96.dp * scale, 96.dp * scale).size(1000.dp * scale, 120.dp * scale)) {
            Text(card.title, Modifier.align(Alignment.BottomStart), fontSize = (48 * scale).sp,
                lineHeight = (58 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.offset(96.dp * scale, 240.dp * scale)) {
            TvMediaMetadata(card, scale)
        }
        card.description?.let { Text(it, Modifier.offset(96.dp * scale, 290.dp * scale).width(700.dp * scale), color = Palette.body,
            fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}
