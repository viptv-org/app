package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
internal fun TvHeroSummary(card: Card, scale: Float) {
    Box(Modifier.fillMaxSize()) {
        if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.offset(96.dp * scale, 306.dp * scale).size(410.dp * scale, 160.dp * scale)
            , contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
        else Box(Modifier.offset(96.dp * scale, 306.dp * scale).size(800.dp * scale, 160.dp * scale)) {
            Text(card.title, Modifier.align(Alignment.BottomStart), fontSize = (48 * scale).sp,
                lineHeight = (56 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.offset(96.dp * scale, 494.dp * scale)) {
            TvMediaMetadata(card, scale)
        }
        card.description?.let { Text(it, Modifier.offset(96.dp * scale, 544.dp * scale).width(780.dp * scale), fontSize = (26 * scale).sp,
            lineHeight = (38 * scale).sp, color = Palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
