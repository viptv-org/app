package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvFocusScroll
import org.viptv.engine.wire.Card

@Composable
internal fun TvEpisodeShelf(parent: Card, episodes: List<Card>, scale: Float, play: (Card) -> Unit,
    focused: () -> Unit, expanded: Boolean, initial: FocusRequester, resumeId: String?, returnToHero: () -> Unit) {
    val seasons = episodes.map { it.identity.episode?.season }.distinct().sortedBy { it ?: 0 }
    var selected by remember(parent.identity) { mutableStateOf(seasons.firstOrNull()) }
    val visible = episodes.filter { it.identity.episode?.season == selected }
    Column(Modifier.fillMaxWidth().heightIn(min = if (expanded) 656.dp * scale else 460.dp * scale)) {
        if (expanded) Box(Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 96.dp * scale, top = 56.dp * scale)) {
            if (parent.logo != null) AsyncImage(parent.logo, parent.title, Modifier.height(64.dp * scale).width(260.dp * scale)
                .graphicsLayer { blendMode = BlendMode.Lighten; compositingStrategy = CompositingStrategy.Offscreen },
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else Text(parent.title, color = Palette.text, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
        }
        TvFocusScroll {
        LazyRow(contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            items(seasons.size) { index ->
                val season = seasons[index]
                var focus by remember { mutableStateOf(false) }
                Box(Modifier.height(52.dp * scale).onFocusChanged { focus = it.isFocused }
                    .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) { returnToHero(); true } else false }
                    .clip(RoundedCornerShape(26.dp * scale))
                    .background(if (focus) Palette.text else if (selected == season) Palette.text.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selected = season }
                    .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                    Text(season?.let { "Season $it" } ?: "Episodes", color = if (focus) Palette.background else if (selected == season) Palette.text else Palette.secondary,
                        fontSize = (24 * scale).sp, fontWeight = if (selected == season) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        }
        Spacer(Modifier.height((if (expanded) 30 else 20).dp * scale))
        TvFocusScroll {
        LazyRow(contentPadding = PaddingValues(start = 96.dp * scale, end = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            items(visible, key = { it.provider_id + ":" + it.identity.id }) { episode -> TvEpisodeCard(episode, scale, play, focused,
                if (episode.identity.id == (resumeId ?: visible.firstOrNull()?.identity?.id)) initial else null) }
        }
        }
    }
}

@Composable
private fun TvEpisodeCard(card: Card, scale: Float, play: (Card) -> Unit, onFocus: () -> Unit, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(448.dp * scale).then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { play(card) }) {
        val shape = RoundedCornerShape(12.dp * scale)
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(shape).background(Palette.surface)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.badges.firstOrNull()?.let { badge -> Text(badge, Modifier.align(Alignment.TopStart).padding(12.dp * scale)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale), color = Palette.text,
                fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.SemiBold) }
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp * scale, Palette.text, shape))
                Box(Modifier.align(Alignment.Center).size(72.dp * scale).background(Palette.text.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(30.dp * scale), tint = Palette.text)
                }
            }
            if (card.progress != null) LinearProgressIndicator(progress = { card.progress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp * scale).align(Alignment.BottomCenter), color = Palette.text, trackColor = Palette.text.copy(alpha = 0.3f))
        }
        Spacer(Modifier.height(14.dp * scale))
        Text(card.identity.episode?.let { "${it.episode}. ${card.title}" } ?: card.title, color = if (focused) Palette.text else Palette.body,
            fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let { Text(it, Modifier.padding(top = 4.dp * scale), fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, color = Palette.secondary) }
        card.description?.let { Text(it, Modifier.padding(top = 8.dp * scale), fontSize = (20 * scale).sp, lineHeight = (28 * scale).sp,
            color = Palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
