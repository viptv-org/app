package org.viptv.app.features.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.viptv.app.components.TopNavigation
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.connection.Profile
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

@Composable
internal fun TvHomeScreen(home: HomeSnapshot?, open: (Card) -> Unit, play: (Card) -> Unit, retry: () -> Unit,
    navigate: (Screen) -> Unit, profile: Profile?, summaryCards: Map<String, Card> = emptyMap(), slides: List<Card> = emptyList(), save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }) {
    var browsing by remember(home?.scope?.profile_id) { mutableStateOf(false) }
    var focused by remember(home?.scope?.profile_id) { mutableStateOf<Card?>(null) }
    var settled by remember(home?.scope?.profile_id) { mutableStateOf<Card?>(null) }
    var landed by remember { mutableStateOf(true) }
    var lastRowHeight by remember(home?.scope?.profile_id, home?.rows?.lastOrNull()?.id) { mutableIntStateOf(0) }
    val firstCard = remember { FocusRequester() }; val playFocus = remember { FocusRequester() }
    val rows = rememberLazyListState(); val scope = rememberCoroutineScope()
    LaunchedEffect(home?.scope?.profile_id, home != null) {
        if (home != null) { withFrameNanos { }; playFocus.requestFocus() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val targetTop = (if (browsing) 512 else 824).dp * scale
        val top = animateDpAsState(targetTop, tween(450, easing = easing), label = "Billboard to rows", finishedListener = { landed = true })
        val measuredLastHeight = with(LocalDensity.current) { lastRowHeight.toDp() }
        val bottomSpace = maxOf(96.dp * scale, maxHeight - targetTop - measuredLastHeight)
        LaunchedEffect(focused?.identity, browsing) { if (browsing) { delay(400); settled = focused } else settled = null }
        val original = if (browsing) settled ?: home?.hero else home?.hero
        val card = original?.let { summaryCards[it.identity.id] ?: it }
        val arrival = rememberTvContentArrival(home != null)
        if (home == null) TvHomeLoading(scale) else {
            Box(Modifier.fillMaxSize().then(arrival)) {
                TvHomeBackdrop(card, browsing)
                if (card != null) {
                    TvHomeVisibility(browsing, scale, 24) {
                        Crossfade(card, animationSpec = tween(300, easing = easing), label = "Focused title summary") {
                            TvBrowseSummary(it, settled ?: it, scale)
                        }
                    }
                    TvHomeVisibility(!browsing, scale, -60, 360) {
                        home.hero?.let { hero ->
                            TvHero(hero, open, play, playFocus, if (home.rows.firstOrNull()?.cards?.isNotEmpty() == true) firstCard else null, slides.size.coerceAtLeast(1), save = save, saved = saved(hero))
                        }
                    }
                }
                TvFocusScroll(manual = true) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = targetTop).graphicsLayer {
                        translationY = (top.value - targetTop).toPx()
                    }, state = rows, verticalArrangement = Arrangement.spacedBy(40.dp * scale), contentPadding = PaddingValues(bottom = bottomSpace)) {
                        itemsIndexed(home.rows, key = { _, row -> row.id }) { index, row ->
                            TvHomeShelf(row, scale, if (index == 0) firstCard else null, showFocus = landed,
                                up = { browsing = false; focused = null; scope.launch { withFrameNanos { }; playFocus.requestFocus() } },
                                focus = { focused = it; if (!browsing) landed = false; browsing = true
                                    if (rows.firstVisibleItemIndex != index || rows.firstVisibleItemScrollOffset != 0)
                                        scope.launch { rows.scrollToItem(index) }
                                }, open, retry,
                                modifier = if (index == home.rows.lastIndex) Modifier.onSizeChanged { lastRowHeight = it.height } else Modifier)
                        }
                    }
                }
            }
        }
        TvHomeVisibility(!browsing, scale, -40, 375) { TopNavigation(Screen.Home, true, navigate, profile) }
    }
}
