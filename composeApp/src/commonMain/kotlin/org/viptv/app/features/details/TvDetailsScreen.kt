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
import org.viptv.app.components.TvFocusHandle
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

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
    back: (() -> Unit)? = null, continuing: List<Card> = emptyList(), paging: Boolean = true) {
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
        val episodesRestore = remember(card?.identity) { TvFocusHandle() }
        var notice by remember { mutableStateOf<String?>(null) }
        var episodeMode by remember(card?.identity) { mutableStateOf(false) }
        var activeRow by remember(card?.identity) { mutableIntStateOf(0) }
        val sectionScroll = org.viptv.app.components.rememberTvSectionScroll(scroll)
        val density = LocalDensity.current
        fun offset(index: Int): Float = with(density) {
            when (index) { 0 -> 0.dp; 1 -> 816.dp; else -> (816 + if (episodeMode) 656 else 461).dp }.toPx() * scale
        }
        fun focusRow(index: Int) {
            activeRow = index
            sectionScroll.focus(index)
        }
        fun returnHero() {
            activeRow = 0; episodeMode = false
            sectionScroll.focus(0, force = true) { if (activeRow == 0) playFocus.requestFocus() }
        }
        fun returnEpisodes() {
            if (detail.episodes.isEmpty()) { returnHero(); return }
            activeRow = 1; episodeMode = true
            sectionScroll.focus(1, force = true)
            scope.launch {
                repeat(32) {
                    withFrameNanos { }
                    if (activeRow != 1 || episodesRestore.request?.invoke() == true) return@launch
                }
            }
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
                        down = null, requestInitialFocus = requestInitialFocus && activeRow == 0,
                        downAction = if (detail.episodes.isEmpty()) null else ::returnEpisodes)
                }
                if (detail.episodes.isNotEmpty()) item(key = "episodes") {
                    TvEpisodeShelf(detail.item, detail.episodes, scale, sources,
                        focused = { episodeMode = true; focusRow(1) }, expanded = episodeMode,
                        resume = remember(detail, continuing) { detailResume(detail, continuing) },
                        returnToHero = ::returnHero, seasonIndex = detail.seasons, nextCursor = detail.next_cursor,
                        busy = busy, loadPage = loadPage, restoreChanged = { episodesRestore.request = it }, paging = paging)
                }
                if (extras.recommendations.isNotEmpty()) item(key = "recommendations") {
                    TvRecommendationShelf(extras.recommendations, scale, open,
                        focused = { focusRow(if (detail.episodes.isEmpty()) 1 else 2) }, up = ::returnEpisodes)
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
