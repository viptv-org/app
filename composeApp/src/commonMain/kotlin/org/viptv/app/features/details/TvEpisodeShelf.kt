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
import kotlinx.coroutines.launch

/** One attempt to put focus in the episode shelf; the Details page retries a [Pending] one each frame. */
internal enum class ShelfFocus {
    Focused,
    /** The target is in the list but not placed yet. */
    Pending,
    /** A return (app#66) has no target: neither the last focused nor the continued episode is in the list. */
    Missing,
}

/**
 * The shelf's focus request, published to the Details page that hosts it. Entering the
 * shelf takes any episode, else the season selector. Returning to Details (app#66) takes
 * the last focused episode, else the continued one, and never another.
 */
internal class ShelfFocusHandle { var request: ((returning: Boolean) -> ShelfFocus)? = null }

/** How many frames a TV focus request waits for lazy children to be placed. */
internal const val TV_FOCUS_RETRY_FRAMES = 32

@Composable
internal fun TvEpisodeShelf(parent: Card, episodes: List<Card>, scale: Float, play: (Card) -> Unit,
    focused: () -> Unit, expanded: Boolean, resume: DetailResume?, returnToHero: () -> Unit,
    seasonIndex: List<org.viptv.engine.wire.EpisodeSeason> = emptyList(), nextCursor: String? = null,
    busy: Boolean = false, loadPage: (Long) -> Unit = {}, focus: ShelfFocusHandle = remember { ShelfFocusHandle() },
    paging: Boolean = true) {
    val groups = remember(episodes, seasonIndex) { detailSeasons(episodes, seasonIndex) }
    val seasons = groups.map { it.number }
    // The viewer's season once they pick one or move into the shelf; until then the
    // continued episode's season as soon as Details know it (app#40), else the first numbered one.
    var kept by rememberSaveable(parent.provider_id, parent.identity.kind, parent.identity.id) { mutableStateOf(false) }
    var selected by rememberSaveable(parent.provider_id, parent.identity.kind, parent.identity.id) { mutableStateOf<Long?>(null) }
    val activeSeason = when {
        kept && selected in seasons -> selected
        resume != null && resume.season in seasons -> resume.season
        else -> seasons.firstOrNull { (it ?: 0L) > 0L } ?: seasons.firstOrNull()
    }
    val group = groups.find { it.number == activeSeason }
    val visible = group?.episodes.orEmpty()
    var lastFocused by rememberSaveable(parent.provider_id, parent.identity.id, activeSeason) { mutableStateOf<String?>(null) }
    val episodeRows = key(parent.identity, activeSeason) { rememberLazyListState() }
    // Place the episode the viewer last focused, else the continued one, at the start of the
    // row so focus can land on it. Once per composition: the row's position isn't saved.
    var landed by remember(parent.identity) { mutableStateOf(false) }
    val landingIndex = visible.indexOfFirst { episodeKey(it) == lastFocused }.takeIf { it >= 0 } ?: resume.indexIn(visible)
    LaunchedEffect(episodeRows, landingIndex) {
        if (landingIndex == null || landed) return@LaunchedEffect
        episodeRows.scrollToItem(landingIndex)
        landed = true; kept = true; selected = activeSeason
    }
    val seasonFocus = remember { FocusRequester() }
    val targets = remember(parent.identity, activeSeason) { mutableMapOf<String, FocusRequester>() }
    var selectorHasFocus by remember { mutableStateOf(false) }
    var entrySequence by remember { mutableIntStateOf(0) }
    var pendingEntry by remember(parent.identity) { mutableStateOf<Pair<Long?, Int>?>(null) }
    fun focusEpisode(): Boolean = targets[lastFocused]?.requestPlacedFocus() == true ||
        targets[visible.find { resume?.matches(it) == true }?.let(::episodeKey)]?.requestPlacedFocus() == true ||
        visible.firstNotNullOfOrNull { targets[episodeKey(it)] }?.requestPlacedFocus() == true
    val scope = rememberCoroutineScope()
    fun requestFocus(returning: Boolean): ShelfFocus {
        if (!returning) return if (focusEpisode() || seasonFocus.requestPlacedFocus()) ShelfFocus.Focused else ShelfFocus.Pending
        // The landing target: an unplaced one is brought into the row first.
        val index = landingIndex ?: return ShelfFocus.Missing
        if (targets[episodeKey(visible[index])]?.requestPlacedFocus() == true) return ShelfFocus.Focused
        if (episodeRows.layoutInfo.visibleItemsInfo.none { it.index == index }) scope.launch { episodeRows.scrollToItem(index) }
        return ShelfFocus.Pending
    }
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
        repeat(TV_FOCUS_RETRY_FRAMES) {
            withFrameNanos { }
            if (pendingEntry != pending || !selectorHasFocus) return@LaunchedEffect
            if (focusEpisode()) { pendingEntry = null; return@LaunchedEffect }
        }
    }
    AutomaticEpisodes(group, nextCursor, busy, loadPage, enabled = paging, scopeKey = parent.provider_id to parent.identity)
    SideEffect { focus.request = ::requestFocus }
    DisposableEffect(parent.identity, focus) { onDispose { focus.request = null } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {

        Column(Modifier.fillMaxWidth().heightIn(min = if (expanded) 656.dp * scale else 460.dp * scale)) {
            if (expanded) Box(Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 96.dp * scale, top = 56.dp * scale)) {
                if (parent.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(parent.logo), parent.title,
                    Modifier.height(64.dp * scale).width(260.dp * scale),
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                else Text(parent.title, color = Palette.text, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvSeasonSelector(seasons, activeSeason, scale, seasonFocus, ::enterEpisodes,
                select = { season -> pendingEntry = null; kept = true; selected = season; groups.find { it.number == season }?.nextOffset?.let(loadPage) },
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
                        TvEpisodeCard(resume.shown(episode), scale, { play(episode) },
                            {
                                lastFocused = episodeKey(episode)
                                // The viewer is in the shelf: their season stays put.
                                if (!kept) { kept = true; selected = activeSeason }
                                focused()
                            },
                            requester, seasonFocus)
                    }
                }

        }
    }
}
