package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.animation.core.tween
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvFocusScroll
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
    extras: TvDetailExtras = TvDetailExtras(), open: (Card) -> Unit = sources, requestInitialFocus: Boolean = true, save: (Card) -> Unit = {}, saved: Boolean = false, loadPage: (Long) -> Unit = {}, playFromStart: ((Card) -> Unit)? = null) {
    if (detail == null && busy) { DetailLoadingLayout(true); return }
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
        var motion by remember(card?.identity) { mutableStateOf<Job?>(null) }
        val density = LocalDensity.current
        DisposableEffect(card?.identity) { onDispose { motion?.cancel() } }
        fun offset(index: Int): Float = with(density) {
            when (index) { 0 -> 0.dp; 1 -> 816.dp; else -> (816 + if (episodeMode) 656 else 461).dp }.toPx() * scale
        }
        suspend fun moveRow(index: Int) {
            withFrameNanos { }
            val current = offset(scroll.firstVisibleItemIndex) + scroll.firstVisibleItemScrollOffset
            scroll.animateScrollBy(offset(index) - current, tween(350))
        }
        fun focusRow(index: Int) {
            if (activeRow == index) return
            activeRow = index; motion?.cancel()
            motion = scope.launch { moveRow(index) }
        }
        fun returnHero() {
            activeRow = 0; episodeMode = false; motion?.cancel()
            motion = scope.launch {
                moveRow(0)
                if (activeRow == 0) playFocus.requestFocus()
            }
        }
        fun returnEpisodes() {
            if (detail?.episodes.isNullOrEmpty()) { returnHero(); return }
            activeRow = 1; episodeMode = true; motion?.cancel()
            motion = scope.launch {
                launch { moveRow(1) }
                repeat(32) {
                    withFrameNanos { }
                    if (episodesRestore.request?.invoke() == true) return@launch
                }
            }
        }
        key(card?.identity) {
            TvDetailBackdrop(card, scale) { offset(scroll.firstVisibleItemIndex) + scroll.firstVisibleItemScrollOffset }
        }
        TvFocusScroll(manual = true) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
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
                        resumeId = extras.resume?.identity?.id,
                        returnToHero = ::returnHero, seasonIndex = detail.seasons, nextCursor = detail.next_cursor,
                        busy = busy, loadPage = loadPage, restoreChanged = { episodesRestore.request = it })
                }
                if (extras.recommendations.isNotEmpty()) item(key = "recommendations") {
                    TvRecommendationShelf(extras.recommendations, scale, open,
                        focused = { focusRow(if (detail.episodes.isEmpty()) 1 else 2) }, up = ::returnEpisodes)
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item { Text(it, Modifier.padding(96.dp * scale), color = Palette.danger) } }
        }
        }
        notice?.let { message -> AlertDialog(onDismissRequest = { notice = null },
            title = { Text("Title options") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
    }
}
