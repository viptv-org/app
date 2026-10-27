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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.features.home.MediaCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ServerMessages
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.Surface

private const val LIBRARY_TITLE_KEY = "library:title"
private const val LIBRARY_CONTROLS_KEY = "library:controls"
private const val LIBRARY_HISTORY_KEY = "library:history"
private const val LIBRARY_CARD_TYPE = "library:card"

/**
 * Phone, tablet, desktop and browser My List. One grid scrolls everything: the large title and the
 * History card scroll away with the cards (fading as they go), while the list picker, type chips,
 * Filters and the busy/error line stay pinned as a sticky header (app#58). TV has its own screen.
 */
@Composable
fun LibraryScreen(cards: List<Card>, available: Boolean, television: Boolean, open: (Card) -> Unit,
    history: () -> Unit, home: () -> Unit, snapshot: org.viptv.engine.wire.LibrarySnapshot? = null,
    query: org.viptv.engine.wire.LibraryQuery = org.viptv.app.connection.defaultLibraryQuery(),
    changeQuery: (org.viptv.engine.wire.LibraryQuery) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false, error: String? = null) {
    if (television) { TvLibraryScreen(cards, available, open, home, snapshot = snapshot, query = query, changeQuery = changeQuery, more = more, busy = busy, error = error); return }
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Date added") }
    val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
    val visible = if (snapshot != null) cards else when (sort) { "Date added" -> filtered.sortedByDescending { it.added_at_millis ?: Long.MIN_VALUE }; "A–Z" -> filtered.sortedBy { it.title.lowercase() }; "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }; else -> filtered }
    val gridCards = visible.distinctBy(::cardLookupKey)
    val grid = rememberLazyGridState()
    val empty = libraryEmptyState(snapshot, query, available)
    // Height of the pinned controls: History fades out as it slides under them.
    var pinned by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        AutomaticPage(visible.size, busy, more) {
            // Card position, not grid index: the header items come first.
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.filter { it.contentType == LIBRARY_CARD_TYPE }.maxOfOrNull { it.index }
                ?.minus(layout.totalItemsCount - gridCards.size) ?: -1
        }
        Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 8.dp else 24.dp)) {
            // The grid stays the same composition when the list empties, so an open Filters sheet survives.
            // Empty, it only holds the header and the empty state takes the rest of the page.
            LazyVerticalGrid(if (phone) GridCells.Fixed(3) else GridCells.Adaptive(176.dp), state = grid,
                modifier = if (visible.isEmpty()) Modifier.fillMaxWidth() else Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = if (visible.isEmpty()) 0.dp else if (phone) 96.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header items add 8 dp below themselves: with the grid's 10 dp that keeps the 18 dp rhythm.
                if (!phone) item(LIBRARY_TITLE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = LIBRARY_TITLE_KEY) {
                    Text("My List", Modifier.padding(bottom = 8.dp).fadeAsCollapsed(grid, LIBRARY_TITLE_KEY) { 0 },
                        fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
                }
                stickyHeader(LIBRARY_CONTROLS_KEY, contentType = LIBRARY_CONTROLS_KEY) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { pinned = it.height }.blockTouchesBelow().background(Palette.background).padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (snapshot != null) LibraryHeader(snapshot, query, changeQuery, false, 1f, busy, null, null)
                        else LibraryControls(filter, { filter = it }, sort, { sort = it })
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
                        (error ?: snapshot?.reason?.let { ServerMessages.human(it, ServerMessages.LIST_REASON) })?.let { Text(it, color = if (error != null) Palette.danger else Palette.secondary) }
                    }
                }
                item(LIBRARY_HISTORY_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = LIBRARY_HISTORY_KEY) {
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
                items(gridCards, key = ::cardLookupKey, contentType = { LIBRARY_CARD_TYPE }) {
                    MediaCard(it, if (phone) 112 else 176, portrait = true, showLabels = !phone, open = open)
                }
            }
            if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp, bottom = if (phone) 96.dp else 0.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Palette.raised) {
                        Icon(Lucide.Bookmark, null, Modifier.padding(20.dp).size(32.dp), tint = Palette.secondary)
                    }
                    Text(empty.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(empty.message, fontSize = 14.sp, color = Palette.secondary)
                    Button(onClick = { if (empty.clearFilters) changeQuery(org.viptv.app.connection.defaultLibraryQuery().copy(source = query.source)) else home() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text(if (empty.clearFilters) "Clear filters" else "Browse Home") }
                }
            }
        }
    }
}

/**
 * Fades a collapsing header item by the share of it still showing below [covered] pixels from the
 * grid's top. Driven by the scroll position alone, so there is no animation to reduce.
 */
private fun Modifier.fadeAsCollapsed(grid: LazyGridState, key: Any, covered: () -> Int) = graphicsLayer {
    val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    alpha = if (item == null || item.size.height == 0) 1f
        else ((item.offset.y + item.size.height - covered()).toFloat() / item.size.height).coerceIn(0f, 1f)
}

/**
 * The pinned controls float over cards (and the faded History card) that scrolled beneath them: a tap
 * on their empty space must not reach those. Scrolling still works because the grid is an ancestor.
 */
private fun Modifier.blockTouchesBelow() = pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
