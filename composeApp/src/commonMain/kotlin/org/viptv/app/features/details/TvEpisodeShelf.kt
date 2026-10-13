package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvFocusScroll
import org.viptv.engine.wire.Card

@Composable
internal fun TvEpisodeShelf(parent: Card, episodes: List<Card>, scale: Float, play: (Card) -> Unit,
    focused: () -> Unit, expanded: Boolean, initial: FocusRequester, resumeId: String?, returnToHero: () -> Unit) {
    val seasons = episodes.map { it.identity.episode?.season }.distinct().sortedBy { it ?: 0 }
    val resumeSeason = episodes.find { it.identity.id == resumeId }?.identity?.episode?.season
    var selected by remember(parent.identity) { mutableStateOf(resumeSeason ?: seasons.firstOrNull()) }
    val activeSeason = if (selected in seasons) selected else resumeSeason ?: seasons.firstOrNull()
    val visible = episodes.filter { it.identity.episode?.season == activeSeason }
    val targetId = visible.find { it.identity.id == resumeId }?.identity?.id ?: visible.firstOrNull()?.identity?.id
    val seasonFocus = remember { FocusRequester() }
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
                Box(Modifier.height(52.dp * scale).then(if (season == activeSeason) Modifier.focusRequester(seasonFocus) else Modifier)
                    .focusProperties { if (visible.isNotEmpty()) down = initial }.onFocusChanged { focus = it.isFocused }
                    .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) { returnToHero(); true } else false }
                    .clip(RoundedCornerShape(26.dp * scale))
                    .background(if (focus) Palette.text else if (activeSeason == season) Palette.text.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selected = season }
                    .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                    Text(season?.let { "Season $it" } ?: "Episodes", color = if (focus) Palette.background else if (activeSeason == season) Palette.text else Palette.secondary,
                        fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = if (activeSeason == season) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        }
        Spacer(Modifier.height((if (expanded) 30 else 20).dp * scale))
        TvFocusScroll {
        LazyRow(contentPadding = PaddingValues(start = 96.dp * scale, end = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            items(visible, key = { it.provider_id + ":" + it.identity.id }) { episode -> TvEpisodeCard(episode, scale, play, focused,
                if (episode.identity.id == targetId) initial else null, seasonFocus) }
        }
        }
    }
}
