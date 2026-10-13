package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import org.viptv.app.components.rememberTvShelfScroll
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
    focused: () -> Unit, expanded: Boolean, initial: FocusRequester, resumeId: String?, returnToHero: () -> Unit,
    seasonIndex: List<org.viptv.engine.wire.EpisodeSeason> = emptyList(), nextCursor: String? = null,
    busy: Boolean = false, loadPage: (Long) -> Unit = {}) {
    val groups = remember(episodes, seasonIndex) { detailSeasons(episodes, seasonIndex) }
    val seasons = groups.map { it.number }
    val resumeSeason = episodes.find { it.identity.id == resumeId }?.identity?.episode?.season
    var selected by remember(parent.identity) { mutableStateOf(resumeSeason ?: seasons.firstOrNull { (it ?: 0L) > 0L } ?: seasons.firstOrNull()) }
    val activeSeason = if (selected in seasons) selected else resumeSeason ?: seasons.firstOrNull { (it ?: 0L) > 0L } ?: seasons.firstOrNull()
    val group = groups.find { it.number == activeSeason }
    val visible = group?.episodes.orEmpty()
    var lastFocused by remember(parent.identity, activeSeason) { mutableStateOf<String?>(null) }
    val targetKey = lastFocused ?: visible.find { it.identity.id == resumeId }?.let(::episodeKey) ?: visible.firstOrNull()?.let(::episodeKey)
    val episodeRows = key(parent.identity, activeSeason) { rememberLazyListState() }
    val seasonFocus = remember { FocusRequester() }
    val nextOffset = group?.nextOffset ?: if (group?.total == null) nextCursor?.toLongOrNull() else null
    var moreFocused by remember(activeSeason) { mutableStateOf(false) }
    LaunchedEffect(nextOffset) { if (nextOffset == null && moreFocused && visible.isNotEmpty()) initial.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val shelfScroll = rememberTvShelfScroll(episodeRows, 472.dp * scale, maxWidth, visible.size, 96.dp * scale)
    Column(Modifier.fillMaxWidth().heightIn(min = if (expanded) 656.dp * scale else 460.dp * scale)) {
        if (expanded) Box(Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 96.dp * scale, top = 56.dp * scale)) {
            if (parent.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(parent.logo), parent.title, Modifier.height(64.dp * scale).width(260.dp * scale)
                ,
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
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        selected = season
                        groups.find { it.number == season }?.takeIf { it.episodes.isEmpty() }?.nextOffset?.let(loadPage)
                    }
                    .padding(horizontal = 22.dp * scale), contentAlignment = Alignment.Center) {
                    Text(season?.let { "Season $it" } ?: "Episodes", color = if (focus) Palette.background else if (activeSeason == season) Palette.text else Palette.secondary,
                        fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = if (activeSeason == season) FontWeight.Bold else FontWeight.Medium)
                }
            }
            if (nextOffset != null || moreFocused) item(key = "more") {
                org.viptv.app.components.TvAction(Modifier.height(52.dp * scale).onFocusChanged { moreFocused = it.isFocused }
                    .focusProperties { if (visible.isNotEmpty()) down = initial; up = seasonFocus }, scale,
                    activate = { if (!busy) nextOffset?.let(loadPage) }, contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
                    Text(if (busy) "Loading episodes…" else "Load more episodes", color = it, fontSize = (24 * scale).sp)
                }
            }
        }
        }
        Spacer(Modifier.height((if (expanded) 30 else 20).dp * scale))
        TvFocusScroll(manual = true) {
        LazyRow(state = episodeRows, modifier = Modifier.focusRestorer().focusGroup(), contentPadding = PaddingValues(start = 96.dp * scale, end = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            itemsIndexed(visible, key = { _, episode -> episodeKey(episode) }) { index, episode -> TvEpisodeCard(episode, scale, play, { lastFocused = episodeKey(episode); shelfScroll(index); focused() },
                if (episodeKey(episode) == targetKey) initial else null, seasonFocus) }
        }
        }
    }
}
}
