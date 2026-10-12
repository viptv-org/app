package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvFocusScroll
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
    extras: TvDetailExtras = TvDetailExtras(), open: (Card) -> Unit = sources, requestInitialFocus: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val scroll = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val playFocus = remember { FocusRequester() }
        val episodeFocus = remember { FocusRequester() }
        var notice by remember { mutableStateOf<String?>(null) }
        val card = detail?.item
        var episodeMode by remember(card?.identity) { mutableStateOf(false) }
        TvDetailBackdrop(card, scale, scroll.firstVisibleItemIndex > 0,
            with(LocalDensity.current) { scroll.firstVisibleItemScrollOffset.toDp().value.toInt() })
        TvFocusScroll(manual = true) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
            if (detail != null) {
                item(key = "hero") {
                    TvDetailHero(detail.item, extras, scale,
                        play = { sources(extras.resume ?: detail.item) },
                        save = { notice = "Saving titles to My List is not available on this connection yet." },
                        trailer = { notice = "No trailer is available for this title." },
                        restart = { sources(detail.item) }, first = playFocus,
                        down = if (detail.episodes.isEmpty()) null else episodeFocus, requestInitialFocus = requestInitialFocus)
                }
                if (detail.episodes.isNotEmpty()) item(key = "episodes") {
                    TvEpisodeShelf(detail.item, detail.episodes, scale, sources,
                        focused = { episodeMode = true; scope.launch { withFrameNanos { }; scroll.animateScrollToItem(1) } }, expanded = episodeMode,
                        initial = episodeFocus, resumeId = extras.resume?.identity?.id,
                        returnToHero = { scope.launch { scroll.animateScrollToItem(0); episodeMode = false; playFocus.requestFocus() } })
                }
                if (extras.recommendations.isNotEmpty()) item(key = "recommendations") {
                    TvRecommendationShelf(extras.recommendations, scale, open,
                        focused = { scope.launch { scroll.animateScrollToItem(if (detail.episodes.isEmpty()) 1 else 2) } })
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
