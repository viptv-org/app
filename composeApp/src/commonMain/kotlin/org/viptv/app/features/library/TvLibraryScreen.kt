package org.viptv.app.features.library

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.components.tvGridItem
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.app.components.LocalTvScale
import org.viptv.app.components.PageRestore
import org.viptv.app.components.SegmentedControl
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.ServerMessages
import org.viptv.app.features.continuewatching.continueWatchingActivation
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.features.home.TvHomeSkeletonCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.ListPageState
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.components.listPageState
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val LIBRARY_COLUMNS = 7
private const val CONTINUE_COLUMNS = 4
private const val CONTINUE_GAP = 24f
private const val CONTINUE_CARD_WIDTH = (1728f - CONTINUE_GAP * (CONTINUE_COLUMNS - 1)) / CONTINUE_COLUMNS

@Composable
internal fun TvLibraryScreen(cards: List<Card>, available: Boolean, open: (Card) -> Unit, home: () -> Unit,
    lastWatched: Map<String, Long> = emptyMap(), snapshot: org.viptv.engine.wire.LibrarySnapshot? = null,
    query: org.viptv.engine.wire.LibraryQuery = org.viptv.app.connection.defaultLibraryQuery(),
    changeQuery: (org.viptv.engine.wire.LibraryQuery) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false, error: String? = null,
    remove: ((Card) -> Unit)? = null, retry: () -> Unit = {}, pageError: String? = null,
    segment: MyListSegment = MyListSegment.Saved, selectSegment: (MyListSegment) -> Unit = {},
    continueWatching: ContinueWatchingSection? = null) {
    // Each segment is its own visit: switching reclaims the new segment's first card at the first row.
    key(segment) {
        TvLibraryBody(cards, available, open, home, lastWatched, snapshot, query, changeQuery, more, busy, error, remove, retry,
            pageError, segment, selectSegment, continueWatching)
    }
}

@Composable
private fun TvLibraryBody(cards: List<Card>, available: Boolean, open: (Card) -> Unit, home: () -> Unit,
    lastWatched: Map<String, Long>, snapshot: org.viptv.engine.wire.LibrarySnapshot?,
    query: org.viptv.engine.wire.LibraryQuery, changeQuery: (org.viptv.engine.wire.LibraryQuery) -> Unit,
    more: (() -> Unit)?, busy: Boolean, error: String?, remove: ((Card) -> Unit)?, retry: () -> Unit, pageError: String?,
    segment: MyListSegment, selectSegment: (MyListSegment) -> Unit, continueWatching: ContinueWatchingSection?) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val saved = segment == MyListSegment.Saved
    val section = continueWatching
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(if (lastWatched.isEmpty()) "Date added" else "Last watched") }
    val segmentEntry = remember { FocusRequester() }
    val selectedFilter = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    // The bar below the grid acts on the card the viewer last stood on, so it survives the move onto the bar.
    var lastFocused by remember { mutableStateOf<Card?>(null) }
    var confirming by remember { mutableStateOf<Card?>(null) }
    val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
    val visible = when {
        !saved -> emptyList()
        snapshot != null -> cards
        else -> when (sort) {
            "A–Z" -> filtered.sortedBy { it.title.lowercase() }
            "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
            "Last watched" -> filtered.sortedByDescending { lastWatched[it.identity.id] ?: Long.MIN_VALUE }
            else -> filtered
        }
    }.distinctBy(::cardLookupKey)
    val continueCards = remember(section?.page?.cards) { section?.page?.cards.orEmpty().distinctBy(::cardLookupKey) }
    val shown = if (saved) visible else continueCards
    val columns = if (saved) LIBRARY_COLUMNS else CONTINUE_COLUMNS
    val target = lastFocused?.takeIf { card -> shown.any { cardLookupKey(it) == cardLookupKey(card) } } ?: shown.firstOrNull()
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore(if (saved) "library" else "continue")
    val anchored = restore.landing(shown.size) { cardLookupKey(shown[it]) }
    val empty = libraryEmptyState(snapshot, query, available)
    val pageState = if (saved) listPageState(visible.size, busy, error,
        answered = snapshot != null && (!busy || snapshot.query.sameList(query))) else section?.page?.state ?: ListPageState.Empty
    val moreAction = if (saved) more else section?.more
    val barBusy = if (saved) busy else section?.page?.busy ?: false
    val removeLabel = if (saved) REMOVE_LIBRARY_LABEL else REMOVE_CONTINUE_LABEL
    suspend fun claim(requester: FocusRequester) {
        repeat(30) { if (runCatching { requester.requestFocus() }.getOrDefault(false)) return; withFrameNanos { } }
    }
    PageRestore(grid, restore, claimOnEntry = true, target = {
        if (!saved && section?.page?.loaded == false) null else restore.landing(shown.size) { cardLookupKey(shown[it]) }
    })
    if (!saved) LaunchedEffect(continueCards.isEmpty(), pageState) {
        if (continueCards.isEmpty() && pageState != ListPageState.Loading) claim(action)
    } else LaunchedEffect(shown.isEmpty(), pageState) {
        // An emptied My List leaves the remote on the segment that owns it.
        if (shown.isEmpty() && pageState != ListPageState.Loading) claim(segmentEntry)
    }
    LaunchedEffect(shown) {
        val lost = lastFocused ?: return@LaunchedEffect
        if (shown.isEmpty() || shown.any { cardLookupKey(it) == cardLookupKey(lost) }) return@LaunchedEffect
        lastFocused = null
        claim(restore.focus)
    }
    AutomaticPage(shown.size, barBusy, moreAction) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        CompositionLocalProvider(LocalTvScale provides scale) {
            Column(Modifier.fillMaxSize().focusGroup()
                .padding(horizontal = 96.dp * scale).padding(top = 8.dp * scale)) {
                SegmentedControl(MyListSegmentLabels, segmentIndex(segment), { index -> selectSegment(MyListSegment.entries[index]) },
                    down = gridFocus, up = if (saved) selectedFilter else null, entry = segmentEntry)
                Spacer(Modifier.height(24.dp * scale))
                if (saved) {
                    Box(Modifier.focusGroup().focusRequester(selectedFilter)) {
                        if (snapshot != null) LibraryFilterBar(snapshot, query, changeQuery, down = gridFocus)
                        else LibraryControls(filter, { filter = it }, sort, { sort = it }, watchedAvailable = lastWatched.isNotEmpty(), down = gridFocus)
                    }
                    Spacer(Modifier.height(32.dp * scale))
                    if (busy && visible.isEmpty()) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
                    if (pageState != ListPageState.Failed) (error ?: snapshot?.reason?.let { ServerMessages.human(it, ServerMessages.LIST_REASON) })?.let {
                        Text(it, color = if (error != null) Palette.danger else Palette.secondary, fontSize = (24 * scale).sp)
                    }
                } else {
                    section?.page?.let { page -> page.error?.takeIf { page.loaded }?.let { Text(it, color = Palette.danger, fontSize = (24 * scale).sp) } }
                }
                if (pageState == ListPageState.Failed) {
                    if (saved) TvLibraryFailure(error ?: "", retry, scale)
                    else TvPageMessage(scale, CONTINUE_FAILED_TITLE, section?.page?.error.orEmpty(), "Try again", { section?.retry?.invoke() }, action)
                } else if (pageState == ListPageState.Empty) {
                    if (saved) Column(Modifier.padding(top = 120.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                        Text(empty.title, fontSize = (32 * scale).sp)
                        Text(empty.message, fontSize = (24 * scale).sp, color = Palette.secondary)
                        TvAction(Modifier.heightIn(min = 64.dp * scale), scale, primary = true,
                            activate = { if (empty.clearFilters) changeQuery(org.viptv.app.connection.defaultLibraryQuery().copy(source = query.source)) else home() },
                            contentPadding = PaddingValues(horizontal = 28.dp * scale)) { Text(if (empty.clearFilters) "Clear filters" else "Browse Home", color = it, fontSize = (24 * scale).sp) }
                    } else TvPageMessage(scale, CONTINUE_EMPTY_TITLE, CONTINUE_EMPTY_BODY, "Browse Home", { section?.home?.invoke() ?: home() }, action)
                } else {
                    LazyVerticalGrid(GridCells.Fixed(columns), (if (saved) Modifier.width(1720.dp * scale) else Modifier.fillMaxWidth())
                        .weight(1f).focusGroup(),
                        state = grid,
                        horizontalArrangement = Arrangement.spacedBy((if (saved) 16f else CONTINUE_GAP).dp * scale),
                        verticalArrangement = Arrangement.spacedBy((if (saved) 22f else 16f).dp * scale),
                        contentPadding = PaddingValues(top = if (saved) 0.dp else 8.dp * scale, bottom = 8.dp * scale)) {
                        if (saved) itemsIndexed(visible, key = { _, card -> cardLookupKey(card) }) { index, card ->
                            val key = cardLookupKey(card)
                            TvCatalogCard(card, scale, true, Modifier.tvGridItem(index)
                                .then(if (index == 0) Modifier.focusRequester(gridFocus) else Modifier)
                                .pageAnchor(restore, key, index, target = anchored == index)
                                .focusProperties {
                                    if (index < LIBRARY_COLUMNS) up = segmentEntry
                                    if (index % LIBRARY_COLUMNS == LIBRARY_COLUMNS - 1) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                }, { lastFocused = it }, { restore.mark(key, index); open(it) }, focusedCaptions = true)
                        } else {
                            val activate = continueWatchingActivation(section?.play ?: {}, open)
                            itemsIndexed(continueCards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> "wide" }) { index, card ->
                                TvCatalogCard(card, scale, false, Modifier.tvGridItem(index)
                                    .then(if (index == 0) Modifier.focusRequester(gridFocus) else Modifier)
                                    .pageAnchor(restore, cardLookupKey(card), index, target = anchored == index)
                                    .focusProperties {
                                        if (index < CONTINUE_COLUMNS) up = segmentEntry
                                        if (index % CONTINUE_COLUMNS == CONTINUE_COLUMNS - 1) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                    },
                                    { lastFocused = it }, { restore.mark(cardLookupKey(card), index); activate(card) },
                                    artworkWidth = CONTINUE_CARD_WIDTH.dp * scale,
                                    info = open)
                            }
                        }
                        val footerError = if (saved) pageError else section?.page?.pageError
                        if (shown.isNotEmpty() && !barBusy && footerError != null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(failed = true, retry = moreAction ?: retry, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        } else if (shown.isNotEmpty() && barBusy) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        } else if (shown.isNotEmpty() && !barBusy && footerError == null && moreAction == null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                        }
                    }
                    if (remove != null || section?.remove != null) TvRemovalBar(scale, target, removeLabel, removeFocus, { confirming = it })
                }
            }
        }
    }
    confirming?.let { card ->
        ListRemovalConfirmation(card, removeLabel,
            confirm = { confirming = null; if (saved) remove?.invoke(card) else section?.remove?.invoke(card) }, dismiss = { confirming = null })
    }
}

@Composable
private fun TvRemovalBar(scale: Float, target: Card?, label: String, requester: FocusRequester, request: (Card) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp * scale, bottom = 96.dp * scale), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Text(target?.let { "“${it.title}”" }.orEmpty(), Modifier.weight(1f), color = Palette.secondary,
            fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TvAction(Modifier.heightIn(min = 64.dp * scale).width(560.dp * scale), scale, requester = requester, enabled = target != null,
            activate = { target?.let(request) }, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(label, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvPageMessage(scale: Float, title: String, message: String, action: String, activate: () -> Unit, requester: FocusRequester) {
    Column(Modifier.padding(top = 96.dp * scale), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        Text(title, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
        Text(message, color = Palette.secondary, fontSize = (24 * scale).sp)
        TvAction(Modifier.heightIn(min = 64.dp * scale).width(280.dp * scale), scale, primary = true, requester = requester,
            activate = activate, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(action, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvLibraryFailure(reason: String, retry: () -> Unit, scale: Float) {
    Column(Modifier.padding(top = 120.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Text(MY_LIST_FAILED_TITLE, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
        Text(reason, color = Palette.secondary, fontSize = (24 * scale).sp)
        TvAction(Modifier.heightIn(min = 64.dp * scale).width(280.dp * scale), scale, primary = true, activate = retry,
            contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text("Try again", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}