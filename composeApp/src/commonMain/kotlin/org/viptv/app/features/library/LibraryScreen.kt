package org.viptv.app.features.library

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.features.continuewatching.continueWatchingActivation
import org.viptv.app.features.home.MediaCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.app.components.LocalListRemoval
import org.viptv.app.components.LocalBarsFadeContent
import org.viptv.app.components.PageRestore
import org.viptv.app.components.ScrollEdge
import org.viptv.app.components.ScrollEdgeFade
import org.viptv.app.components.SegmentedControl
import org.viptv.app.components.ServerMessages
import org.viptv.app.components.overlaysContent
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.Surface
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.ListPageState
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.components.PosterSkeleton
import org.viptv.app.components.ListSkeletonTile
import org.viptv.app.components.listPageState
import org.viptv.app.components.listItemEntrance
import org.viptv.app.components.rememberListEntrance

private const val LIBRARY_TITLE_KEY = "library:title"
private const val LIBRARY_CONTROLS_KEY = "library:controls"
private const val LIBRARY_HISTORY_KEY = "library:history"
private const val LIBRARY_CARD_TYPE = "library:card"
private const val CONTINUE_CARD_TYPE = "continue:card"
internal const val REMOVE_LIBRARY_LABEL = "Remove from My List"
internal const val REMOVE_CONTINUE_LABEL = "Remove from Continue Watching"
private val CONTINUE_MIN_CARD = 260.dp
private const val CONTINUE_CARDS_AFTER = 1

@Composable
internal fun LibraryScreen(cards: List<Card>, available: Boolean, television: Boolean, open: (Card) -> Unit,
    history: () -> Unit, home: () -> Unit, snapshot: org.viptv.engine.wire.LibrarySnapshot? = null,
    query: org.viptv.engine.wire.LibraryQuery = org.viptv.app.connection.defaultLibraryQuery(),
    changeQuery: (org.viptv.engine.wire.LibraryQuery) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false, error: String? = null,
    remove: ((Card) -> Unit)? = null, retry: () -> Unit = {}, pageError: String? = null,
    segment: MyListSegment = MyListSegment.Saved, selectSegment: (MyListSegment) -> Unit = {},
    continueWatching: ContinueWatchingSection? = null) {
    if (television) {
        TvLibraryScreen(cards, available, open, home, snapshot = snapshot, query = query, changeQuery = changeQuery, more = more,
            busy = busy, error = error, remove = remove, retry = retry, pageError = pageError, segment = segment,
            selectSegment = selectSegment, continueWatching = continueWatching)
        return
    }
    // Each segment is its own visit: switching resets that segment's scroll and focus to its first row.
    key(segment) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val phone = maxWidth < 640.dp
            val gutter = if (phone) 16.dp else 40.dp
            val saved = segment == MyListSegment.Saved
            val section = continueWatching
            var filter by remember { mutableStateOf("All") }
            var sort by remember { mutableStateOf("Date added") }
            var confirming by remember { mutableStateOf<Card?>(null) }
            var continueConfirming by remember { mutableStateOf<Card?>(null) }
            val removal = remember(remove) { remove?.let { ListRemoval(REMOVE_LIBRARY_LABEL, replacesSave = true) { card -> confirming = card } } }
            val continueRemoval = remember(section?.remove) {
                section?.remove?.let { ListRemoval(REMOVE_CONTINUE_LABEL, continueWatching = true) { card -> continueConfirming = card } }
            }
            val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
            val visible = when {
                !saved -> emptyList()
                snapshot != null -> cards
                else -> when (sort) { "Date added" -> filtered.sortedByDescending { it.added_at_millis ?: Long.MIN_VALUE }
                    "A–Z" -> filtered.sortedBy { it.title.lowercase() }
                    "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
                    else -> filtered }
            }
            val gridCards = visible.distinctBy(::cardLookupKey)
            val continueCards = remember(section?.page?.cards) { section?.page?.cards.orEmpty().distinctBy(::cardLookupKey) }
            val shown = if (saved) gridCards else continueCards
            val cardsAfter = if (saved) { if (phone) 2 else 3 } else CONTINUE_CARDS_AFTER
            val grid = rememberLazyGridState()
            val entrance = rememberListEntrance(gridCards.isNotEmpty())
            val restore = rememberPageRestore(if (saved) "library" else "continue")
            val anchored = restore.anchor(shown.size) { cardLookupKey(shown[it]) }
            val scrolled = remember(grid) { derivedStateOf { grid.canScrollBackward } }.value
            val empty = libraryEmptyState(snapshot, query, available)
            val pageState = if (saved) listPageState(visible.size, busy, error,
                answered = snapshot != null && (!busy || snapshot.query.sameList(query))) else section?.page?.state ?: ListPageState.Empty
            var pinned by remember { mutableIntStateOf(0) }
            val availableWidth = maxWidth - gutter * 2
            val continueGap = if (phone) 10.dp else 14.dp
            val continueColumns = if (phone) 2 else ((availableWidth + continueGap) / (CONTINUE_MIN_CARD + continueGap)).toInt().coerceAtLeast(2)
            val continueCardWidth = ((availableWidth - continueGap * (continueColumns - 1)) / continueColumns).value.toInt()
            val cells = when {
                saved -> if (phone) GridCells.Fixed(3) else GridCells.Adaptive(176.dp)
                phone -> GridCells.Fixed(2)
                else -> GridCells.Fixed(continueColumns)
            }
            PageRestore(grid, restore, target = {
                if (!saved && section?.page?.loaded == false) null
                else restore.anchor(shown.size) { cardLookupKey(shown[it]) }?.plus(cardsAfter)
            })
            AutomaticPage(shown.size, if (saved) busy else section?.page?.busy ?: false, if (saved) more else section?.more) {
                val layout = grid.layoutInfo
                layout.visibleItemsInfo.filter { it.contentType == if (saved) LIBRARY_CARD_TYPE else CONTINUE_CARD_TYPE }
                    .maxOfOrNull { it.index }?.minus(layout.totalItemsCount - shown.size) ?: -1
            }
            CompositionLocalProvider(LocalListRemoval provides if (saved) removal else continueRemoval) {
                Column(Modifier.fillMaxSize().padding(start = gutter, end = gutter, top = if (phone) 8.dp else 24.dp)) {
                    LazyVerticalGrid(cells, state = grid,
                        modifier = if (shown.isEmpty()) Modifier.fillMaxWidth() else Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = if (shown.isEmpty()) 0.dp else 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (saved) { if (phone) 10.dp else 12.dp } else continueGap),
                        verticalArrangement = Arrangement.spacedBy(if (saved) 10.dp else 18.dp),
                        topEdgeFade = false) {
                        if (!phone) item(LIBRARY_TITLE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = LIBRARY_TITLE_KEY) {
                            Text("My List", Modifier.padding(bottom = 8.dp).fadeAsCollapsed(grid, LIBRARY_TITLE_KEY) { 0 },
                                fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
                        }
                        stickyHeader(LIBRARY_CONTROLS_KEY, contentType = LIBRARY_CONTROLS_KEY) {
                            Column(Modifier.fillMaxWidth().semantics { overlaysContent = Unit }) {
                                Column(Modifier.fillMaxWidth().onSizeChanged { pinned = it.height }.blockTouchesBelow().background(Palette.background).padding(bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    SegmentedControl(MyListSegmentLabels, segmentIndex(segment), { index -> selectSegment(MyListSegment.entries[index]) })
                                    if (saved) {
                                        if (snapshot != null) LibraryFilterBar(snapshot, query, changeQuery)
                                        else LibraryControls(filter, { filter = it }, sort, { sort = it })
                                        if (pageState != ListPageState.Failed) (error ?: snapshot?.reason?.let { ServerMessages.human(it, ServerMessages.LIST_REASON) })?.let { Text(it, color = if (error != null) Palette.danger else Palette.secondary) }
                                    } else {
                                        val continuePage = section?.page
                                        if (continuePage != null && continuePage.loaded) continuePage.error?.let { Text(it, color = Palette.danger) }
                                    }
                                }
                                if (LocalBarsFadeContent.current && scrolled) ScrollEdgeFade(ScrollEdge.Top)
                            }
                        }
                        if (saved) item(LIBRARY_HISTORY_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = LIBRARY_HISTORY_KEY) {
                            Surface(onClick = history, modifier = Modifier.padding(bottom = 8.dp).fadeAsCollapsed(grid, LIBRARY_HISTORY_KEY) { pinned },
                                shape = RoundedCornerShape(16.dp), color = Palette.surface) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Lucide.History, null, Modifier.size(22.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("History", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Everything you've played", fontSize = 12.sp, color = Palette.secondary)
                                    }
                                    Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Palette.secondary)
                                }
                            }
                        }
                        if (saved) itemsIndexed(gridCards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> LIBRARY_CARD_TYPE }) { index, card ->
                            val key = cardLookupKey(card)
                            MediaCard(card, if (phone) 112 else 176, portrait = true, showLabels = !phone,
                                open = { restore.mark(key, index); open(it) },
                                modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index).listItemEntrance(index, entrance.animate))
                        } else {
                            val activate = continueWatchingActivation(section?.play ?: {}, open)
                            itemsIndexed(continueCards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> CONTINUE_CARD_TYPE }) { index, card ->
                                val key = cardLookupKey(card)
                                MediaCard(card, continueCardWidth, portrait = false,
                                    open = { restore.mark(key, index); open(it) },
                                    select = { restore.mark(key, index); activate(it) },
                                    modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index),
                                    showFocusOutline = !phone)
                            }
                        }
                        val footerMore = if (saved) more else section?.more
                        val footerBusy = if (saved) busy else section?.page?.busy ?: false
                        val footerError = if (saved) pageError else section?.page?.pageError
                        val loaded = if (saved) true else section?.page?.loaded == true
                        if (shown.isNotEmpty() && !footerBusy && footerError != null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(failed = true, retry = footerMore ?: { if (saved) retry() else section?.retry?.invoke(); Unit }, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                        } else if (shown.isNotEmpty() && footerBusy) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                        } else if (shown.isNotEmpty() && !footerBusy && footerError == null && footerMore == null && loaded) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                        }
                    }
                    if (pageState == ListPageState.Loading) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
                        if (saved) PosterSkeleton(phone, gap = if (phone) 10.dp else 12.dp)
                        else ContinueWatchingSkeleton(continueColumns, continueGap)
                    } else if (pageState == ListPageState.Failed) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        if (saved) LibraryFailure(error ?: "", retry)
                        else ContinueWatchingMessage(CONTINUE_FAILED_TITLE, CONTINUE_FAILED_BODY, "Try again", { section?.retry?.invoke() })
                    } else if (pageState == ListPageState.Empty) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        if (saved) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(shape = RoundedCornerShape(20.dp), color = Palette.raised) {
                                Icon(Lucide.Bookmark, null, Modifier.padding(20.dp).size(32.dp), tint = Palette.secondary)
                            }
                            Text(empty.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(empty.message, fontSize = 14.sp, color = Palette.secondary)
                            Button(onClick = { if (empty.clearFilters) changeQuery(org.viptv.app.connection.defaultLibraryQuery().copy(source = query.source)) else home() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text(if (empty.clearFilters) "Clear filters" else "Browse Home") }
                        } else ContinueWatchingMessage(CONTINUE_EMPTY_TITLE, CONTINUE_EMPTY_BODY, "Browse Home", { section?.home?.invoke() ?: home() })
                    }
                }
            }
            if (saved) confirming?.let { card ->
                ListRemovalConfirmation(card, REMOVE_LIBRARY_LABEL, confirm = { confirming = null; remove?.invoke(card) }, dismiss = { confirming = null })
            } else continueConfirming?.let { card ->
                ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = { continueConfirming = null; continueWatching?.remove?.invoke(card) }, dismiss = { continueConfirming = null })
            }
        }
    }
}

internal const val CONTINUE_FAILED_TITLE = "Continue Watching didn’t load"
private const val CONTINUE_FAILED_BODY = "Check the connection and try again."
internal const val CONTINUE_EMPTY_TITLE = "Nothing in progress"
internal const val CONTINUE_EMPTY_BODY = "Titles you start watching appear here."

@Composable
private fun ContinueWatchingSkeleton(columns: Int, gap: androidx.compose.ui.unit.Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(columns) { ListSkeletonTile(Modifier.weight(1f).aspectRatio(16f / 9)) }
            }
        }
    }
}

@Composable
private fun ContinueWatchingMessage(title: String, message: String, action: String, activate: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Palette.raised) {
            Icon(Lucide.Play, null, Modifier.padding(20.dp).size(32.dp), tint = Palette.secondary)
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, fontSize = 14.sp, color = Palette.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = activate, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text(action) }
    }
}

@Composable
private fun LibraryFailure(reason: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(MY_LIST_FAILED_TITLE, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(reason, color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = retry, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text("Try again") }
    }
}

private fun Modifier.fadeAsCollapsed(grid: LazyGridState, key: Any, covered: () -> Int) = graphicsLayer {
    val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    alpha = if (item == null || item.size.height == 0) 1f
        else ((item.offset.y + item.size.height - covered()).toFloat() / item.size.height).coerceIn(0f, 1f)
}

private fun Modifier.blockTouchesBelow() = pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }