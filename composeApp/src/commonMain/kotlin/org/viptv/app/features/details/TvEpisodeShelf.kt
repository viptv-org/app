package org.viptv.app.features.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import org.viptv.app.components.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvEpisodeShelf(parent: Card, episodes: List<Card>, scale: Float, play: (Card) -> Unit,
    focused: () -> Unit, expanded: Boolean, resumeId: String?, returnToHero: () -> Unit,
    seasonIndex: List<org.viptv.engine.wire.EpisodeSeason> = emptyList(), nextCursor: String? = null,
    busy: Boolean = false, loadPage: (Long) -> Unit = {}, restoreChanged: ((() -> Boolean)?) -> Unit = {}) {
    val groups = remember(episodes, seasonIndex) { detailSeasons(episodes, seasonIndex) }
    val seasons = groups.map { it.number }
    val resumeSeason = episodes.find { it.identity.id == resumeId }?.identity?.episode?.season
    var selected by rememberSaveable(parent.provider_id, parent.identity.kind, parent.identity.id) {
        mutableStateOf(resumeSeason ?: seasons.firstOrNull { (it ?: 0L) > 0L } ?: seasons.firstOrNull())
    }
    val activeSeason = if (selected in seasons) selected else resumeSeason ?: seasons.firstOrNull { (it ?: 0L) > 0L } ?: seasons.firstOrNull()
    val group = groups.find { it.number == activeSeason }
    val visible = group?.episodes.orEmpty()
    var lastFocused by rememberSaveable(parent.provider_id, parent.identity.id, activeSeason) { mutableStateOf<String?>(null) }
    val episodeRows = key(parent.identity, activeSeason) { rememberLazyListState() }
    val seasonFocus = remember { FocusRequester() }
    val targets = remember(parent.identity, activeSeason) { mutableMapOf<String, FocusRequester>() }
    fun restore(): Boolean = targets[lastFocused]?.requestPlacedFocus() == true ||
        targets[visible.find { it.identity.id == resumeId }?.let(::episodeKey)]?.requestPlacedFocus() == true ||
        targets.values.firstOrNull()?.requestPlacedFocus() == true || seasonFocus.requestPlacedFocus()
    AutomaticEpisodes(group, nextCursor, busy, loadPage, scopeKey = parent.provider_id to parent.identity)
    SideEffect { restoreChanged(::restore) }
    DisposableEffect(parent.identity) { onDispose { restoreChanged(null) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val shelfScroll = rememberTvShelfScroll(episodeRows, 472.dp * scale, maxWidth, visible.size, 96.dp * scale)
        Column(Modifier.fillMaxWidth().heightIn(min = if (expanded) 656.dp * scale else 460.dp * scale)) {
            if (expanded) Box(Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 96.dp * scale, top = 56.dp * scale)) {
                if (parent.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(parent.logo), parent.title,
                    Modifier.height(64.dp * scale).width(260.dp * scale),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(parent.title, color = Palette.text, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvSeasonSelector(seasons, activeSeason, scale, seasonFocus, ::restore,
                select = { season -> selected = season; groups.find { it.number == season }?.nextOffset?.let(loadPage) },
                returnHero = returnToHero, entered = focused)
            Spacer(Modifier.height((if (expanded) 30 else 20).dp * scale))
            TvFocusScroll(manual = true) {
                LazyRow(state = episodeRows, modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    itemsIndexed(visible, key = { _, episode -> episodeKey(episode) }) { index, episode ->
                        val requester = remember { FocusRequester() }
                        DisposableEffect(episodeKey(episode), targets) {
                            targets[episodeKey(episode)] = requester
                            onDispose { targets.remove(episodeKey(episode)) }
                        }
                        TvEpisodeCard(episode, scale, play,
                            { lastFocused = episodeKey(episode); shelfScroll(index); focused() },
                            requester, seasonFocus)
                    }
                }
            }
        }
    }
}
