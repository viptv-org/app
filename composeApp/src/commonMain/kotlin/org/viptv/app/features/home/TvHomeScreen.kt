@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.state.owner
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import org.viptv.app.components.TopNavigation
import org.viptv.app.components.motionSpec
import org.viptv.app.connection.Profile
import org.viptv.app.state.RowSeeAll
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

@Composable
internal fun TvHomeScreen(home: HomeSnapshot?, open: (Card) -> Unit, play: (Card) -> Unit, retry: () -> Unit,
    navigate: (Screen) -> Unit, profile: Profile?, summaryCards: Map<String, Card> = emptyMap(), slides: List<Card> = emptyList(), save: (Card) -> Unit = {}, saved: (Card) -> Boolean = { false }, backChanged: ((() -> Unit)?) -> Unit = {}, busy: Boolean = home == null, error: String? = null,
    moreRow: ((String) -> Unit)? = null, rowBusy: Set<String> = emptySet(), rowErrors: Map<String, String> = emptyMap(),
    enrichSummary: (Card) -> Unit = {},
    seeAllFor: (HomeRow) -> RowSeeAll? = { null }, openSeeAll: (RowSeeAll) -> Unit = {}) {
    var browsing by rememberSaveable(home?.scope?.profile_id) { mutableStateOf(false) }
    val lastNavigation = remember { mutableStateOf<TimeMark?>(null) }
    val rapidNavigation = remember { mutableStateOf(false) }
    val quietForMillis = { lastNavigation.value?.elapsedNow()?.inWholeMilliseconds }
    val focused = remember(home?.scope?.profile_id) { mutableStateOf<Card?>(null) }
    var activeRow by rememberSaveable(home?.scope?.profile_id) { mutableIntStateOf(-1) }
    var activeRowId by rememberSaveable(home?.scope?.profile_id) { mutableStateOf<String?>(null) }
    var heroFocusEpoch by remember(home?.scope?.profile_id) { mutableIntStateOf(0) }
    val firstCard = remember { FocusRequester() }; val playFocus = remember { FocusRequester() }
    val resumeShelf = remember { FocusRequester() }
    val restoreBrowsing = remember(home?.scope?.profile_id) { browsing }
    var measuredShelfHeight by remember(home?.scope?.profile_id) { mutableIntStateOf(0) }
    var firstShelfHeight by remember(home?.scope?.profile_id, home?.rows?.firstOrNull()?.id) { mutableIntStateOf(0) }
    val navigationFocus = remember { FocusRequester() }
    var navigationFocusEpoch by remember(home?.scope?.profile_id) { mutableIntStateOf(0) }
    var initialShelfFocusClaimed by remember(home?.scope?.profile_id) { mutableStateOf(false) }
    var navigationAttached by remember { mutableStateOf(false) }
    val input = LocalInputModeManager.current
    val rows = rememberLazyListState(cacheWindow = androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 1f))
    val shelfStates = remember(home?.scope?.profile_id) { mutableMapOf<String, LazyListState>() }
    val sectionScroll = org.viptv.app.components.rememberTvSectionScroll(rows)
    val visibleRows = remember(home?.rows) {
        home?.rows.orEmpty().filter { it.state == RowState.PENDING || (it.state == RowState.READY && it.cards.isNotEmpty()) }
    }
    // Row fades read browsing/activeRow here: inside the LazyColumn item scope they recompose every visible shelf per focus move.
    val rowAlphas = remember(visibleRows) { mutableMapOf<String, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    val rowFade = motionSpec<Float>(tween(200))
    LaunchedEffect(browsing, activeRow, rowAlphas) {
        if (rowAlphas.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            visibleRows.forEachIndexed { index, row ->
                val alpha = rowAlphas[row.id] ?: return@forEachIndexed
                val target = if (browsing && index < activeRow) 0f else 1f
                launch { alpha.animateTo(target, rowFade) }
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
    LaunchedEffect(restoreBrowsing) {
        if (restoreBrowsing && visibleRows.isNotEmpty()) {
            val target = activeRowId?.let { id -> visibleRows.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 } ?: activeRow.coerceIn(visibleRows.indices)
            activeRow = target
            activeRowId = visibleRows[target].id
            rows.scrollToItem(target)
            focusWhenPlaced(resumeShelf) { rows.layoutInfo.visibleItemsInfo.any { it.index == target } }
        }
    }
    fun enterRow(index: Int) {
        browsing = true
        activeRow = index
        activeRowId = visibleRows.getOrNull(index)?.id
        sectionScroll.focus(index)
    }
    LaunchedEffect(home?.scope?.profile_id, visibleRows.getOrNull(firstReadyRow)?.id, featured.isEmpty(), error) {
        if (!initialShelfFocusClaimed && featured.isEmpty() && home?.complete == true && error == null && firstReadyRow >= 0) {
            rows.scrollToItem(firstReadyRow)
            initialShelfFocusClaimed = focusWhenPlaced(firstCard) { firstReadyAttached }
        }
    }
    LaunchedEffect(featured.isNotEmpty()) {
        if (featured.isNotEmpty() && initialShelfFocusClaimed && browsing && lastNavigation.value == null) {
            browsing = false; focused.value = null; activeRow = -1; activeRowId = null; heroFocusEpoch++
        }
    }
    LaunchedEffect(home == null && error == null) {
        if (home == null && error == null) { withFrameNanos { }; navigationFocusEpoch++ }
    }
    LaunchedEffect(navigationFocusEpoch) {
        if (navigationFocusEpoch != 0) {
            focusWhenPlaced(navigationFocus) { navigationAttached }
        }
    }
    fun returnHero() {
        browsing = false; focused.value = null; activeRow = -1; activeRowId = null; heroFocusEpoch++
        if (featured.isNotEmpty()) playFocus.requestFocus() else navigationFocusEpoch++
        sectionScroll.focus(0, force = true)
    }
    LaunchedEffect(visibleRows.map { it.id }) {
        if (!browsing) return@LaunchedEffect
        val selected = activeRowId ?: return@LaunchedEffect
        val index = visibleRows.indexOfFirst { it.id == selected }
        if (index >= 0) { activeRow = index; return@LaunchedEffect }
        focused.value = null
        if (visibleRows.isEmpty()) {
            browsing = false; activeRow = -1; activeRowId = null
            if (featured.isNotEmpty()) heroFocusEpoch++
        } else {
            val fallback = activeRow.coerceIn(visibleRows.indices)
            activeRow = fallback
            activeRowId = visibleRows[fallback].id
            rows.scrollToItem(fallback)
            // Let the removed shelf leave composition before focusing its replacement: the shared resume requester can still point at the old node.
            withFrameNanos { }
            focusWhenPlaced(resumeShelf) { rows.layoutInfo.visibleItemsInfo.any { it.index == fallback } }
        }
    }
    DisposableEffect(browsing) { backChanged(if (browsing) ::returnHero else null); onDispose { backChanged(null) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val viewport = maxWidth
        val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val shelfSlot = maxOf((40 + 12 + 255.2f * 1.5f + 76).dp * scale, with(density) { measuredShelfHeight.toDp() })
        val listTop = (maxHeight - 24.dp * scale - shelfSlot).coerceAtLeast(0.dp)
        val firstShelf = visibleRows.firstOrNull()
        val firstArtHeight = if (firstShelf?.id == "continue") 422.4f * 9f / 16f else 255.2f * 1.5f
        val firstHeight = if (firstShelfHeight > 0) with(density) { firstShelfHeight.toDp() }
            else (52f + firstArtHeight + if (firstShelf?.state == RowState.READY) 76f else 0f).dp * scale
        val targetTop = if (browsing) listTop else 824.dp * scale - (shelfSlot - firstHeight)
        val top = animateDpAsState(targetTop, motionSpec(tween(450, easing = easing)), label = "Billboard to rows")
        var heroTimerEnabled by remember(home?.scope?.owner()) { mutableStateOf(true) }
        LaunchedEffect(browsing) {
            if (browsing) heroTimerEnabled = false
            else { delay(450); heroTimerEnabled = true }
        }
        val carousel = rememberHeroCarousel(featured, !browsing && heroTimerEnabled, home?.scope?.owner())
        val portraitSelection = { activeRow >= 0 && visibleRows.getOrNull(activeRow)?.id != "continue" }
        // The exact request the browse plane's SoftArtwork makes, so the settle swap finds it warm.
        val softPlane = remember(viewport, scale, density) {
            val wPx = with(density) { viewport.toPx().toInt() }
            val hPx = with(density) { (620.dp * scale).toPx().toInt() }
            val sigma = with(density) { (70.dp * scale).toPx() / 4f }.toInt().coerceIn(1, 200)
            val build = { c: Card -> c.poster?.let { org.viptv.app.images.blurredArtwork(it, wPx, hPx, blur = sigma) ?: it } }
            build
        }
        val selection = rememberTvBillboardSelection(focused, { browsing }, portraitSelection, quietForMillis, softArtwork = softPlane,
            motionBusy = { rows.isScrollInProgress || shelfStates.values.any { it.isScrollInProgress } },
            resolve = { summaryCards[it.identity.id] ?: it }, enrich = enrichSummary,
            settleMillis = { if (rapidNavigation.value) 350L else 120L })
        val settled = selection?.card
        val original = settled ?: carousel.card
        val card = original
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
                if (event.type == KeyEventType.KeyDown && event.key.isDirectional()) {
                    rapidNavigation.value = lastNavigation.value?.elapsedNow()?.inWholeMilliseconds?.let { it < 400 } == true
                    lastNavigation.value = TimeSource.Monotonic.markNow()
                }
                false
            }) {
                TvHomeBackdrop(card, browsing, portrait = selection?.portrait ?: false, hero = carousel.card, heroReady = carousel.artworkReady)
                if (card != null) {
                    TvHomeVisibility(browsing && settled != null, scale, 0, 220) {
                        org.viptv.app.components.FadeThrough(card, contentKey = { it.identity }) {
                            TvBrowseSummary(it, scale)
                        }
                    }
                    TvHomeVisibility(!browsing, scale, -12, 220, retain = true, focusEnabled = { !browsing }) {
                        carousel.card?.let { hero ->
                            TvHero(hero, { if (!browsing) open(it) }, { if (!browsing) play(it) }, playFocus, if (visibleRows.firstOrNull()?.cards?.isNotEmpty() == true) resumeShelf else null, carousel.count, selected = carousel.index, progress = carousel.progress, focusEpoch = heroFocusEpoch, requestFocus = !browsing && error == null, save = { if (!browsing) save(it) }, isSaved = saved)
                        }
                    }
                }

                LazyColumn(Modifier.fillMaxWidth().offset(y = listTop).height(shelfSlot).graphicsLayer {
                    translationY = (top.value - listTop).toPx()
                }, state = rows, sectionControlled = true, verticalArrangement = Arrangement.spacedBy(40.dp * scale)) {
                    itemsIndexed(visibleRows, key = { _, row -> row.id }, contentType = { _, _ -> "row" }) { index, row ->
                        val shelfState = rememberLazyListState()
                        DisposableEffect(row.id, shelfState) {
                            shelfStates[row.id] = shelfState
                            onDispose { shelfStates.remove(row.id) }
                        }
                        val alpha = rowAlphas.getOrPut(row.id) {
                            androidx.compose.animation.core.Animatable(if (browsing && index < activeRow) 0f else 1f)
                        }
                        Box(Modifier.fillMaxWidth().height(shelfSlot).onFocusChanged { if (it.hasFocus) enterRow(index) }.focusGroup(), contentAlignment = androidx.compose.ui.Alignment.BottomStart) {
                            TvHomeShelf(row, scale, if (index == firstReadyRow) firstCard else null, showFocus = true,
                                up = if (index == 0) ::returnHero else null,
                                focus = { focused.value = it; enterRow(index) }, open, more = moreRow, appending = row.id in rowBusy, appendError = rowErrors[row.id],
                                firstPlaced = { firstReadyAttached = true },
                                resume = if (index == (if (browsing) activeRow else firstReadyRow)) resumeShelf else null,
                                rowAlpha = alpha,
                                onHeightChange = { if (it > measuredShelfHeight) measuredShelfHeight = it; if (index == 0) firstShelfHeight = it },
                                play = play, state = shelfState,
                                seeAllFor = seeAllFor, openSeeAll = openSeeAll)
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

