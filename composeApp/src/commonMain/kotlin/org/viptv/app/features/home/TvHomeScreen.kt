package org.viptv.app.features.home

import org.viptv.app.state.owner
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
    navigate: (Screen) -> Unit, profile: Profile?, summaryCards: Map<String, Card> = emptyMap(), slides: List<Card> = emptyList(), save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }, backChanged: ((() -> Unit)?) -> Unit = {}, busy: Boolean = home == null, error: String? = null,
    moreRow: ((String) -> Unit)? = null, rowBusy: Set<String> = emptySet(), rowErrors: Map<String, String> = emptyMap(),
    enrichSummary: (Card) -> Unit = {}) {
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
    // Down from the billboard resumes the shelf at the card the viewer left, not
    // at card 0 (which flashed the old card and then scrolled back to the start).
    val resumeShelf = remember { FocusRequester() }
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
        // The shelf claim is a fallback for a Home that has rows but no hero.
        // A still-loading snapshot (rows first, hero later) must not steal the
        // opening focus to Continue Watching.
        if (!initialShelfFocusClaimed && featured.isEmpty() && home?.complete == true && error == null && firstReadyRow >= 0) {
            rows.scrollToItem(firstReadyRow)
            initialShelfFocusClaimed = focusWhenPlaced(firstCard) { firstReadyAttached }
        }
    }
    // If the shelf claimed focus only because the hero had not arrived yet, and
    // the viewer has not pressed anything, the hero takes its focus back.
    LaunchedEffect(featured.isNotEmpty()) {
        if (featured.isNotEmpty() && initialShelfFocusClaimed && browsing && lastNavigation.value == null) {
            browsing = false; focused.value = null; activeRow = -1; heroFocusEpoch++
        }
    }
    // While Home has no content at all there is no hero or shelf to focus, but
    // the top navigation is already on screen. Focus it so the remote is never
    // dead during the first load or a failed catalog refresh.
    LaunchedEffect(home == null && error == null) {
        if (home == null && error == null) { withFrameNanos { }; navigationFocusEpoch++ }
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
        // The rows are laid out at the browsing top and only translated: a list
        // whose top padding jumped to the billboard position lost 312 dp of
        // height in one frame and clipped the sliding rows.
        val listTop = 512.dp * scale
        val top = animateDpAsState(targetTop, tween(450, easing = easing), label = "Billboard to rows", finishedListener = {})
        val measuredLastHeight = with(LocalDensity.current) { lastRowHeight.toDp() }
        val bottomSpace = maxOf(96.dp * scale, maxHeight - listTop - measuredLastHeight)
        var heroTimerEnabled by remember(home?.scope?.owner()) { mutableStateOf(true) }
        LaunchedEffect(browsing) {
            if (browsing) heroTimerEnabled = false
            else { delay(450); heroTimerEnabled = true }
        }
        val carousel = rememberHeroCarousel(featured, !browsing && heroTimerEnabled, home?.scope?.owner())
        val portraitSelection = { activeRow >= 0 && visibleRows.getOrNull(activeRow)?.id != "continue" }
        // The exact request the browse plane's SoftArtwork makes, so the settle
        // swap finds it warm instead of popping in after the sharp art.
        val softPlane = remember(viewport, scale, density) {
            val wPx = with(density) { viewport.toPx().toInt() }
            val hPx = with(density) { (620.dp * scale).toPx().toInt() }
            val sigma = with(density) { (70.dp * scale).toPx() / 4f }.toInt().coerceIn(1, 200)
            val build = { c: Card -> c.poster?.let { org.viptv.app.images.sizedArtwork(it, wPx, hPx, blur = sigma) } }
            build
        }
        val selection = rememberTvBillboardSelection(focused, { browsing }, portraitSelection, quietForMillis, softArtwork = softPlane,
            motionBusy = { rows.isScrollInProgress })
        val settled = selection?.card
        // A sparse row card (Continue Watching) gets its billboard fields from
        // one detail fetch, cached in the controller's summaryCards.
        LaunchedEffect(settled?.identity?.id) {
            val c = settled ?: return@LaunchedEffect
            if (c.logo == null || c.description.isNullOrBlank()) enrichSummary(c)
        }
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
                            TvHero(hero, { if (!browsing) open(it) }, { if (!browsing) play(it) }, playFocus, if (visibleRows.firstOrNull()?.cards?.isNotEmpty() == true) resumeShelf else null, carousel.count, selected = carousel.index, progress = carousel.progress, focusEpoch = heroFocusEpoch, requestFocus = !browsing && error == null, save = { if (!browsing) save(it) }, isSaved = saved)
                        }
                    }
                }
                TvFocusScroll(manual = true) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = listTop).graphicsLayer {
                        translationY = (top.value - listTop).toPx()
                    }, state = rows, verticalArrangement = Arrangement.spacedBy(40.dp * scale), contentPadding = PaddingValues(bottom = bottomSpace)) {
                        itemsIndexed(visibleRows, key = { _, row -> row.id }, contentType = { _, _ -> "row" }) { index, row ->
                            // A row first composed above the active one starts faded,
                            // exactly like animateFloatAsState's initial value. Stable
                            // instances only: a focus move must not re-hand every shelf
                            // a fresh modifier chain (the fade reads in the draw phase).
                            val alpha = rowAlphas.getOrPut(row.id) {
                                androidx.compose.animation.core.Animatable(if (browsing && index < activeRow) 0f else 1f)
                            }
                            TvHomeShelf(row, scale, if (index == firstReadyRow) firstCard else null, showFocus = true,
                                // Only the first row returns to the billboard; Up from any
                                // lower row moves to the row above.
                                up = if (index == 0) ::returnHero else null,
                                focus = { focused.value = it; browsing = true
                                    if (activeRow != index) {
                                        activeRow = index; rowMotion?.cancel()
                                        rowMotion = scope.launch {
                                            val current = rowOffset(rows.firstVisibleItemIndex, scale) + rows.firstVisibleItemScrollOffset
                                            rows.animateScrollBy(rowOffset(index, scale) - current, tween(350, easing = easing))
                                        }
                                    }
                                }, open, retry, viewport = viewport,
                                more = moreRow, appending = row.id in rowBusy, appendError = rowErrors[row.id],
                                firstPlaced = { firstReadyAttached = true },
                                resume = if (visibleRows.getOrNull(firstReadyRow)?.id == row.id) resumeShelf else null,
                                rowAlpha = alpha,
                                onHeightChange = if (index == visibleRows.lastIndex) ({ height -> lastRowHeight = height }) else null,
                                play = play)
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

