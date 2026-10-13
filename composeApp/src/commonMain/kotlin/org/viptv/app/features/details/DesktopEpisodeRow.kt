package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun DesktopEpisodeRow(episode: Card, play: (Card) -> Unit, browser: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(if (browser) 10.dp else 12.dp)).clickable { play(episode) }
        .padding(horizontal = if (browser) 12.dp else 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (browser) 16.dp else 18.dp)) {
        Text(episode.identity.episode?.episode?.toString().orEmpty(), Modifier.width(if (browser) 28.dp else 32.dp),
            fontSize = 20.sp, color = Palette.secondary, textAlign = TextAlign.Center)
        Box(Modifier.width(if (browser) 170.dp else 220.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp)).background(Palette.surface)) {
            AsyncImage(episode.landscape ?: episode.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            episode.progress?.takeIf { it.isFinite() && it > 0 }?.let { progress ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Palette.accent))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (browser) 4.dp else 6.dp)) {
            Text(episode.title, fontSize = if (browser) 15.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.description?.let { Text(it, color = Palette.secondary, fontSize = if (browser) 13.sp else 14.sp,
                lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            episode.subtitle?.let { Text(it, fontSize = 13.sp, color = Palette.secondary) }
        }
    }
}
