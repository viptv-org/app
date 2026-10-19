package org.viptv.app.features.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.Job
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import org.viptv.app.components.TopNavigation
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.connection.Profile
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

@Composable
internal fun TvHomeScreen(home: HomeSnapshot?, open: (Card) -> Unit, play: (Card) -> Unit, retry: () -> Unit,
    navigate: (Screen) -> Unit, profile: Profile?, summaryCards: Map<String, Card> = emptyMap(), slides: List<Card> = emptyList(), save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }, backChanged: ((() -> Unit)?) -> Unit = {}, busy: Boolean = home == null, error: String? = null) {
    var browsing by remember(home?.scope?.profile_id) { mutableStateOf(false) }
    // Timestamp of the last D-pad event: the billboard and prefetch wait for
    // navigation to go quiet instead of thrashing artwork while a key repeats.
    val lastNavigation = remember { mutableStateOf<TimeMark?>(null) }
    val quietForMillis = { lastNavigation.value?.elapsedNow()?.inWholeMilliseconds }
    val focused = remember(home?.scope?.profile_id) { mutableStateOf<Card?>(null) }
    var activeRow by remember(home?.scope?.profile_id) { mutableIntStateOf(-1) }
    var rowMotion by remember { mutableStateOf<Job?>(null) }
    var lastRowHeight by remember(home?.scope?.profile_id, home?.rows?.lastOrNull()?.id) { mutableIntStateOf(0) }
    var heroFocusEpoch by remember(home?.scope?.profile_id) { mutableIntStateOf(0) }
    val firstCard = remember { FocusRequester() }; val playFocus = remember { FocusRequester() }
    val navigationFocus = remember { FocusRequester() }
    var navigationFocusEpoch by remember(home?.scope?.profile_id) { mutableIntStateOf(0) }
    var initialShelfFocusClaimed by remember(home?.scope?.profile_id) { mutableStateOf(false) }
    var navigationAttached by remember { mutableStateOf(false) }
    val input = LocalInputModeManager.current
    val rows = rememberLazyListState(); val scope = rememberCoroutineScope()
    val visibleRows = remember(home?.rows) {
        home?.rows.orEmpty().filter { it.cards.isNotEmpty() || it.state == RowState.PENDING || it.state == RowState.FAILED }
    }
    // Row fades are driven here, not from inside the LazyColumn item scope:
    // reading browsing/activeRow there recomposes every visible shelf on each
    // focus move. The alphas are only read inside graphicsLayer blocks.
    val rowAlphas = remember(visibleRows) { mutableMapOf<String, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    LaunchedEffect(browsing, activeRow, rowAlphas) {
        if (rowAlphas.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            visibleRows.forEachIndexed { index, row ->
                val alpha = rowAlphas[row.id] ?: return@forEachIndexed
                val target = if (browsing && index < activeRow) 0f else 1f
                launch { alpha.animateTo(target, tween(200)) }
            }
        }
    }
    val featured = slides.ifEmpty { home?.featured.orEmpty().ifEmpty { listOfNotNull(home?.hero) } }
    val firstReadyRow = visibleRows.indexOfFirst { it.state == RowState.READY && it.cards.isNotEmpty() }
    var firstReadyAttached by remember(home?.scope?.profile_id, visibleRows.getOrNull(firstReadyRow)?.id) { mutableStateOf(false) }
    suspend fun focusWhenPlaced(requester: FocusRequester, ready: () -> Boolean): Boolean = withTimeoutOrNull(1500) {
        snapshotFlow { ready() }.first { it }
        input.requestInputMode(InputMode.Keyboard)
        while (!runCatching { requester.requestFocus() }.getOrDefault(false)) withFrameNanos { }
        true
    } ?: false
    LaunchedEffect(home?.scope?.profile_id, visibleRows.getOrNull(firstReadyRow)?.id, featured.isEmpty(), error) {
        if (!initialShelfFocusClaimed && featured.isEmpty() && error == null && firstReadyRow >= 0) {
            rows.scrollToItem(firstReadyRow)
            initialShelfFocusClaimed = focusWhenPlaced(firstCard) { firstReadyAttached }
        }
    }
    LaunchedEffect(navigationFocusEpoch) {
        if (navigationFocusEpoch != 0) {
            focusWhenPlaced(navigationFocus) { navigationAttached }
        }
    }
    val density = LocalDensity.current
    var rowScale by remember { mutableFloatStateOf(1f) }
    fun rowOffset(index: Int, scale: Float): Float = with(density) {
        visibleRows.take(index).sumOf { if (it.id == "continue") 384 else 516 }.dp.toPx() * scale
    }
    fun returnHero() {
        browsing = false; focused.value = null; activeRow = -1; heroFocusEpoch++
        if (featured.isNotEmpty()) playFocus.requestFocus() else navigationFocusEpoch++
        rowMotion?.cancel()
        rowMotion = scope.launch { rows.animateScrollBy(-rowOffset(rows.firstVisibleItemIndex, rowScale) - rows.firstVisibleItemScrollOffset, tween(450)) }
    }
    DisposableEffect(browsing) { backChanged(if (browsing) ::returnHero else null); onDispose { backChanged(null) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val viewport = maxWidth
        SideEffect { rowScale = scale }
        val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val targetTop = (if (browsing) 512 else 824).dp * scale
        val top = animateDpAsState(targetTop, tween(450, easing = easing), label = "Billboard to rows", finishedListener = {})
        val measuredLastHeight = with(LocalDensity.current) { lastRowHeight.toDp() }
        val bottomSpace = maxOf(96.dp * scale, maxHeight - targetTop - measuredLastHeight)
        var heroTimerEnabled by remember(home?.scope) { mutableStateOf(true) }
        LaunchedEffect(browsing) {
            if (browsing) heroTimerEnabled = false
            else { delay(450); heroTimerEnabled = true }
        }
        val carousel = rememberHeroCarousel(featured, !browsing && heroTimerEnabled, home?.scope)
        val portraitSelection = { activeRow >= 0 && visibleRows.getOrNull(activeRow)?.id != "continue" }
        val selection = rememberTvBillboardSelection(focused, { browsing }, portraitSelection, quietForMillis)
        val settled = selection?.card
        val original = settled ?: carousel.card
        val card = original?.let { summaryCards[it.identity.id] ?: it }
        val arrival = rememberTvContentArrival(home != null)
        val empty = home?.complete == true && visibleRows.isEmpty() && featured.isEmpty()
        val catalogsFailed = home?.complete == true && featured.isEmpty() &&
            home.rows.any { it.state == RowState.FAILED } &&
            home.rows.all { it.cards.isEmpty() && it.state != RowState.PENDING }
        if (home == null || empty || catalogsFailed) {
            if (home == null && busy && error == null) TvHomeLoading(scale)
            else HomeStatus(home, error ?: "Your catalogs could not load. Try refreshing Home.".takeIf { catalogsFailed }, busy, retry, navigate,
                Modifier.padding(start = 96.dp * scale, end = 96.dp * scale, top = 300.dp * scale), television = true, scale = scale)
        } else {
            Box(Modifier.fillMaxSize().then(arrival).onPreviewKeyEvent { event ->
                if (event.key.isDirectional()) lastNavigation.value = TimeSource.Monotonic.markNow()
                false
            }) {
                TvHomeBackdrop(card, browsing, portrait = selection?.portrait ?: false, hero = carousel.card, heroReady = carousel.artworkReady)
                if (card != null) {
                    TvHomeVisibility(browsing && settled != null, scale, 24, 450, sequentialFade = true) {
                        org.viptv.app.components.FadeThrough(card) {
                            TvBrowseSummary(it, scale)
                        }
                    }
                    TvHomeVisibility(!browsing, scale, -80, 450, retain = true, focusEnabled = { !browsing }, sequentialFade = true) {
                        carousel.card?.let { hero ->
                            TvHero(hero, { if (!browsing) open(it) }, { if (!browsing) play(it) }, playFocus, if (visibleRows.firstOrNull()?.cards?.isNotEmpty() == true) firstCard else null, carousel.count, selected = carousel.index, progress = carousel.progress, focusEpoch = heroFocusEpoch, requestFocus = !browsing && error == null, save = { if (!browsing) save(it) }, isSaved = saved)
                        }
                    }
                }
                TvFocusScroll(manual = true) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = targetTop).graphicsLayer {
                        translationY = (top.value - targetTop).toPx()
                    }, state = rows, verticalArrangement = Arrangement.spacedBy(40.dp * scale), contentPadding = PaddingValues(bottom = bottomSpace)) {
                        itemsIndexed(visibleRows, key = { _, row -> row.id }, contentType = { _, _ -> "row" }) { index, row ->
                            // A row first composed above the active one starts faded,
                            // exactly like animateFloatAsState's initial value.
                            val alpha = rowAlphas.getOrPut(row.id) {
                                androidx.compose.animation.core.Animatable(if (browsing && index < activeRow) 0f else 1f)
                            }
                            TvHomeShelf(row, scale, if (index == firstReadyRow) firstCard else null, showFocus = true,
                                up = ::returnHero,
                                focus = { focused.value = it; browsing = true
                                    if (activeRow != index) {
                                        activeRow = index; rowMotion?.cancel()
                                        rowMotion = scope.launch {
                                            val current = rowOffset(rows.firstVisibleItemIndex, scale) + rows.firstVisibleItemScrollOffset
                                            rows.animateScrollBy(rowOffset(index, scale) - current, tween(350, easing = easing))
                                        }
                                    }
                                }, open, retry, viewport = viewport,
                                modifier = Modifier.graphicsLayer { this.alpha = alpha?.value ?: 1f }
                                    .then(if (index == visibleRows.lastIndex) Modifier.onSizeChanged { lastRowHeight = it.height } else Modifier),
                                firstPlaced = { firstReadyAttached = true })
                        }
                    }
                }
            }
        }
        if (error != null && home != null && !empty && !catalogsFailed) {
            HomeStatus(home, error, busy, retry, navigate,
                Modifier.padding(start = 96.dp * scale, end = 96.dp * scale, top = 168.dp * scale), television = true, scale = scale, inline = true)
        }
        if (busy && home != null) androidx.compose.material3.LinearProgressIndicator(
            Modifier.fillMaxWidth().padding(horizontal = 96.dp * scale).padding(top = 146.dp * scale), color = org.viptv.app.theme.Palette.accent)
        TvHomeVisibility(!browsing, scale, 0, 200) {
            DisposableEffect(Unit) { onDispose { navigationAttached = false } }
            Box(Modifier.focusRequester(navigationFocus).onGloballyPositioned { navigationAttached = true }) {
                TopNavigation(Screen.Home, true, navigate, profile)
            }
        }
    }
}

private fun Key.isDirectional(): Boolean =
    this == Key.DirectionLeft || this == Key.DirectionRight || this == Key.DirectionUp || this == Key.DirectionDown
