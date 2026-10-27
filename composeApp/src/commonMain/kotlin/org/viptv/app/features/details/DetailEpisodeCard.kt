package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun DetailEpisodeCard(episode: Card, phone: Boolean, play: (Card) -> Unit, modifier: Modifier = Modifier, tablet: Boolean = false,
    /** The player's Episodes list marks the episode on screen; Details never sets it. */
    playing: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable { play(episode) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (phone) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EpisodeArtwork(episode, Modifier.width(if (tablet) 176.dp else 136.dp).aspectRatio(16f / 9))
            EpisodeCaption(episode, Modifier.weight(1f), true, playing)
        } else {
            EpisodeArtwork(episode, Modifier.fillMaxWidth().aspectRatio(16f / 9))
            EpisodeCaption(episode, Modifier.fillMaxWidth(), false, playing)
        }
        episode.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Palette.secondary, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EpisodeCaption(episode: Card, modifier: Modifier, phone: Boolean, playing: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val number = episode.identity.episode?.episode
        val label = number?.let { "$it. ${episode.title}" } ?: episode.title
        Text(label, fontSize = if (phone) 15.sp else 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = if (phone) 2 else 1, overflow = TextOverflow.Ellipsis)
        if (playing) Text(NOW_PLAYING, color = Palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        // Wraps onto its own caption line, so neither it nor the number is ellipsized.
        if (episode.watchedEpisode()) EpisodeWatchedPill(television = false)
        episode.subtitle?.let { Text(it, fontSize = 13.sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun EpisodeArtwork(episode: Card, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Palette.surface), contentAlignment = Alignment.Center) {
        AsyncImage(episode.landscape ?: episode.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.size(30.dp).background(Palette.background.copy(alpha = .55f), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(11.dp), tint = Palette.text)
        }
        episode.progress?.takeIf { it.isFinite() && it > 0 && !episode.watchedEpisode() }?.let { progress ->
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f))
                .progressSemantics(progress.toFloat().coerceIn(0f, 1f))) {
                Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Palette.accent))
            }
        }
    }
}

/** The player's Episodes list marks the episode on screen with this. */
internal const val NOW_PLAYING = "Now playing"
