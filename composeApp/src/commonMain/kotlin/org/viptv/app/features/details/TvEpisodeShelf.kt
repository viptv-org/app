package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyRow as LazyRow
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
    var selectorHasFocus by remember { mutableStateOf(false) }
    var entrySequence by remember { mutableIntStateOf(0) }
    var pendingEntry by remember(parent.identity) { mutableStateOf<Pair<Long?, Int>?>(null) }
    fun focusEpisode(): Boolean = targets[lastFocused]?.requestPlacedFocus() == true ||
        targets[visible.find { it.identity.id == resumeId }?.let(::episodeKey)]?.requestPlacedFocus() == true ||
        visible.firstNotNullOfOrNull { targets[episodeKey(it)] }?.requestPlacedFocus() == true
    fun restore(): Boolean = focusEpisode() || seasonFocus.requestPlacedFocus()
    fun enterEpisodes(): Boolean {
        val wanted = selected.takeIf { it in seasons } ?: activeSeason
        if (wanted == activeSeason && focusEpisode()) { pendingEntry = null; return true }
        if (wanted == activeSeason && visible.isEmpty() && !busy && group?.nextOffset == null && nextCursor == null) return false
        pendingEntry = wanted to ++entrySequence
        return true
    }
    // A season selection can replace lazy children or fetch its first page. Keep
    // the Down intent until those children are placed, without needing an Up/Down detour.
    LaunchedEffect(pendingEntry, activeSeason, visible, selectorHasFocus) {
        val pending = pendingEntry ?: return@LaunchedEffect
        if (!selectorHasFocus || pending.first != activeSeason || visible.isEmpty()) return@LaunchedEffect
        repeat(32) {
            withFrameNanos { }
            if (pendingEntry != pending || !selectorHasFocus) return@LaunchedEffect
            if (focusEpisode()) { pendingEntry = null; return@LaunchedEffect }
        }
    }
    AutomaticEpisodes(group, nextCursor, busy, loadPage, scopeKey = parent.provider_id to parent.identity)
    SideEffect { restoreChanged(::restore) }
    DisposableEffect(parent.identity) { onDispose { restoreChanged(null) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {

        Column(Modifier.fillMaxWidth().heightIn(min = if (expanded) 656.dp * scale else 460.dp * scale)) {
            if (expanded) Box(Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 96.dp * scale, top = 56.dp * scale)) {
                if (parent.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(parent.logo), parent.title,
                    Modifier.height(64.dp * scale).width(260.dp * scale),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(parent.title, color = Palette.text, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvSeasonSelector(seasons, activeSeason, scale, seasonFocus, ::enterEpisodes,
                select = { season -> pendingEntry = null; selected = season; groups.find { it.number == season }?.nextOffset?.let(loadPage) },
                returnHero = returnToHero, entered = focused,
                cancelEntry = { pendingEntry = null }, focusChanged = { selectorHasFocus = it; if (!it) pendingEntry = null })
            Spacer(Modifier.height((if (expanded) 30 else 20).dp * scale))

                LazyRow(state = episodeRows, modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    itemsIndexed(visible, key = { _, episode -> episodeKey(episode) }) { index, episode ->
                        val requester = remember { FocusRequester() }
                        DisposableEffect(episodeKey(episode), targets) {
                            targets[episodeKey(episode)] = requester
                            onDispose { targets.remove(episodeKey(episode)) }
                        }
                        TvEpisodeCard(episode, scale, play,
                            { lastFocused = episodeKey(episode); focused() },
                            requester, seasonFocus)
                    }
                }

        }
    }
}
