package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.app.components.TextButton

/** The rows of the TV Details page, top to bottom. */
internal enum class TvDetailRow { Hero, Episodes, Recommendations }

/** How this composition of the TV Details page began, and whether it was on screen at the last check (app#66). */
private class TvDetailVisit(val firstEntry: Boolean) { var shown = firstEntry }

internal data class TvDetailExtras(
    val cast: String? = null,
    val resume: Card? = null,
    val resumeLabel: String? = null,
    val remaining: String? = null,
    val recommendations: List<Card> = emptyList(),
)

@Composable
internal fun TvDetailsScreen(detail: DetailSnapshot?, busy: Boolean, error: String?, sources: (Card) -> Unit,
    extras: TvDetailExtras = TvDetailExtras(), open: (Card) -> Unit = sources, requestInitialFocus: Boolean = true, save: (Card) -> Unit = {}, saved: Boolean = false, loadPage: (Long) -> Unit = {}, playFromStart: ((Card) -> Unit)? = null,
    back: (() -> Unit)? = null, continuing: List<Card> = emptyList(),
    /**
     * False while the page is composed behind the source selector: it neither pages nor
     * takes focus. Shown again means the viewer came back: see the return rule below (app#66).
     */
    shown: Boolean = true) {
    if (detail == null && busy) { DetailLoadingLayout(true); return }
    if (detail == null) {
        // NAV-04: never a blank, focus-dead page. Explain the failure and give
        // the remote a real action.
        TvDetailUnavailable(error, scale = 1f, back = back, retry = if (error != null && back == null) null else back)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val card = detail?.item
        // Saveable so the page behind the sources sheet keeps its position
        // instead of visibly resetting to the hero when the sheet opens.
        val scroll = key(card?.identity) { rememberSaveable(saver = LazyListState.Saver) { LazyListState() } }
        val scope = rememberCoroutineScope()
        val playFocus = remember { FocusRequester() }
        val shelfFocus = remember(card?.identity) { ShelfFocusHandle() }
        var notice by remember { mutableStateOf<String?>(null) }
        // Return rule (app#66). Details composed while shown is a first entry: focus starts on
        // the hero. While the source selector covers Details (opened from an episode or the
        // hero, or reopened as the player closes), the page keeps the row the viewer left from,
        // per title and across the player, where it leaves composition. Shown again, focus goes
        // back to that row: the last focused episode, else the continued episode (app#40),
        // else the hero. Any other row returns to the hero.
        // Keyed by title, not by position: one saved-state slot serves every title's Details.
        var leftFrom by rememberSaveable(card?.provider_id, card?.identity,
            key = card?.let { "tv-detail-left-from:${it.provider_id}:${it.identity.kind}:${it.identity.id}" }) { mutableStateOf<TvDetailRow?>(null) }
        val visit = remember(card?.identity) { TvDetailVisit(firstEntry = shown) }
        var activeRow by remember(card?.identity) { mutableStateOf(if (shown) TvDetailRow.Hero else leftFrom ?: TvDetailRow.Hero) }
        var episodeMode by remember(card?.identity) { mutableStateOf(activeRow == TvDetailRow.Episodes) }
        val sectionScroll = org.viptv.app.components.rememberTvSectionScroll(scroll)
        val density = LocalDensity.current
        fun offset(index: Int): Float = with(density) {
            when (index) { 0 -> 0.dp; 1 -> 816.dp; else -> (816 + if (episodeMode) 656 else 461).dp }.toPx() * scale
        }
        /** The row's item in the page's list: recommendations move up when there are no episodes. */
        fun index(row: TvDetailRow): Int = when (row) {
            TvDetailRow.Hero -> 0
            TvDetailRow.Episodes -> 1
            TvDetailRow.Recommendations -> if (detail.episodes.isEmpty()) 1 else 2
        }
        fun focusRow(row: TvDetailRow) {
            activeRow = row
            sectionScroll.focus(index(row))
        }
        fun returnHero() {
            activeRow = TvDetailRow.Hero; episodeMode = false
            sectionScroll.focus(0, force = true) { if (activeRow == TvDetailRow.Hero) playFocus.requestFocus() }
        }
        /** Into the episode shelf; a [returning] viewer (app#66) gets their episode or the hero, never another episode. */
        fun focusEpisodes(returning: Boolean = false) {
            if (detail.episodes.isEmpty()) { returnHero(); return }
            activeRow = TvDetailRow.Episodes; episodeMode = true
            sectionScroll.focus(index(TvDetailRow.Episodes), force = true)
            scope.launch {
                repeat(TV_FOCUS_RETRY_FRAMES) {
                    withFrameNanos { }
                    if (activeRow != TvDetailRow.Episodes) return@launch
                    when (shelfFocus.request?.invoke(returning)) {
                        ShelfFocus.Focused -> return@launch
                        ShelfFocus.Missing -> { returnHero(); return@launch }
                        ShelfFocus.Pending, null -> Unit
                    }
                }
                if (returning && activeRow == TvDetailRow.Episodes) returnHero()
            }
        }
        LaunchedEffect(card?.identity, shown) {
            if (!shown) { leftFrom = activeRow; visit.shown = false; return@LaunchedEffect }
            leftFrom = null
            if (visit.shown) return@LaunchedEffect
            visit.shown = true
            if (activeRow == TvDetailRow.Episodes) focusEpisodes(returning = true) else returnHero()
        }
        key(card?.identity) {
            TvDetailBackdrop(card, scale) { offset(scroll.firstVisibleItemIndex) + scroll.firstVisibleItemScrollOffset }
        }

        LazyColumn(state = scroll, sectionControlled = true, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
            if (detail != null) {
                item(key = "hero") {
                    TvDetailHero(detail.item, extras, scale,
                        play = { sources(extras.resume ?: detail.item) },
                        save = { save(detail.item) }, saved = saved,
                        trailer = { notice = "No trailer is available for this title." },
                        restart = { playFromStart?.invoke(extras.resume ?: detail.item) ?: sources(extras.resume ?: detail.item) }, first = playFocus,
                        down = null, requestInitialFocus = requestInitialFocus && visit.firstEntry && activeRow == TvDetailRow.Hero,
                        downAction = if (detail.episodes.isEmpty()) null else ({ focusEpisodes() }))
                }
                if (detail.episodes.isNotEmpty()) item(key = "episodes") {
                    TvEpisodeShelf(detail.item, detail.episodes, scale, sources,
                        focused = { episodeMode = true; focusRow(TvDetailRow.Episodes) }, expanded = episodeMode,
                        resume = remember(detail, continuing) { detailResume(detail, continuing) },
                        returnToHero = ::returnHero, seasonIndex = detail.seasons, nextCursor = detail.next_cursor,
                        busy = busy, loadPage = loadPage, focus = shelfFocus, paging = shown)
                }
                if (extras.recommendations.isNotEmpty()) item(key = "recommendations") {
                    TvRecommendationShelf(extras.recommendations, scale, open,
                        focused = { focusRow(TvDetailRow.Recommendations) }, up = { focusEpisodes() })
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item { Text(it, Modifier.padding(96.dp * scale), color = Palette.danger) } }
        }

        notice?.let { message -> AlertDialog(onDismissRequest = { notice = null },
            title = { Text("Title options") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
    }
}


@Composable
private fun TvDetailUnavailable(error: String?, scale: Float, back: (() -> Unit)?, retry: (() -> Unit)?) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(30) { if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; withFrameNanos { } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = maxWidth / 1920.dp
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp * s)) {
            Text("This title couldn’t load", fontSize = (40 * s).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(error ?: "The provider didn’t return this title’s details.", color = Palette.secondary,
                fontSize = (24 * s).sp, lineHeight = (34 * s).sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 900.dp * s))
            (retry ?: back)?.let { action ->
                org.viptv.app.components.TvAction(Modifier.height(72.dp * s).width(300.dp * s).focusRequester(focus), s, primary = true, ring = false,
                    activate = action, contentPadding = PaddingValues(horizontal = 24.dp * s)) {
                    Text("Go back", color = it, fontSize = (24 * s).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
