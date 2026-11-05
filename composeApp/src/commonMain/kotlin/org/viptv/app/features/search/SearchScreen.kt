package org.viptv.app.features.search

import org.viptv.app.components.responseSpec
import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.PageRestore
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.components.listItemEntrance
import org.viptv.app.components.rememberListEntrance
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.components.pointerClickable

@Composable
fun SearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean, error: String?, desktop: Boolean,
    query: (String) -> Unit, open: (Card) -> Unit, television: Boolean = false, editQuery: ((String) -> String) -> Unit,
    searchRows: List<SearchResultRow> = emptyList(), filters: Set<String> = emptySet(), filterChanged: (Set<String>) -> Unit = {},
    focusResults: Boolean = false, retry: (() -> Unit)? = null,
    recentSearches: List<String> = emptyList(), trendingSearches: List<String> = emptyList(),
    clearRecents: () -> Unit = {}) {
    if (television) {
        TvSearchScreen(term, results, addons, busy, error, query, open, editQuery, searchRows = searchRows, focusResults = focusResults,
            retry = retry)
        return
    }
    val restore = rememberPageRestore("search")
    val visible = results.distinctBy(::searchCardKey).filter { it.matchesSearchFilter(filters) }
    val groups = if (searchRows.isNotEmpty()) searchResultGroups(searchRows, filters) else searchGroups(visible, addons)
    val hasResults = if (searchRows.isNotEmpty()) groups.any { it.cards.isNotEmpty() } else visible.isNotEmpty()
    val entrance = rememberListEntrance(hasResults)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val phone = maxWidth < 640.dp
    val gutter = if (phone) 16.dp else 40.dp
    Column(Modifier.fillMaxSize().imePadding().padding(vertical = if (phone) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)) {
        if (!phone) Text(if (desktop && term.isNotBlank()) "Results for “$term”" else "Search",
            Modifier.padding(horizontal = gutter),
            style = if (desktop) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        if (!desktop) Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { SearchField(term, query) }
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(searchFilterOptions) { tab ->
                val active = if (tab == "All") filters.isEmpty() else tab in filters
                val interactions = remember { MutableInteractionSource() }
                val hovered by interactions.collectIsHoveredAsState()
                var focused by remember { mutableStateOf(false) }
                val fill by animateColorAsState(if (hovered) (if (active) Color.White else Color(0xFF2A2A2E))
                    else (if (active) Palette.text else Color(0xFF202022)), responseSpec(), label = "Search tab fill")
                Text(tab, Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .tvFocusOutline(focused, 18.dp, darkGap = 2.dp, width = 2.dp)
                    .background(fill, RoundedCornerShape(18.dp))
                    .pointerClickable(interactionSource = interactions, indication = null, onClickLabel = tab, shape = RoundedCornerShape(18.dp), hover = null) {
                        filterChanged(if (tab == "All") emptySet() else if (tab in filters) filters - tab else filters + tab) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (active) Palette.background else Palette.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = gutter), color = Palette.accent)
        if (!hasResults) error?.let { message -> SearchFailureLine(message, retry, Modifier.fillMaxWidth().padding(horizontal = gutter)) }
        if (!busy && term.isNotBlank() && !hasResults && error == null) {
            val copy = searchNoResultsCopy(searchRows)
            if (searchFailed(searchRows)) SearchFailureLine(copy, retry, Modifier.fillMaxWidth().padding(horizontal = gutter))
            else Text(copy, Modifier.padding(horizontal = gutter), color = Palette.secondary)
        }
        if (phone && term.isNotBlank() && hasResults && searchRows.isEmpty()) Text("Top results", Modifier.padding(horizontal = gutter), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val footer: (@Composable (Modifier) -> Unit)? = when {
            !busy && error != null && hasResults -> { { ListEndFooter(failed = true, retry = retry, noun = LIST_TITLES, modifier = it, television = false, scale = 1f) } }
            busy && hasResults -> { { ListEndFooter(loading = true, noun = LIST_TITLES, modifier = it, television = false, scale = 1f) } }
            !busy && error == null && hasResults -> { { ListEndFooter(ended = true, noun = LIST_TITLES, modifier = it, television = false, scale = 1f) } }
            else -> null
        }
        if (term.isBlank() && !busy && error == null) {
            SearchIdle(recentSearches, trendingSearches, gutter, query, clearRecents, Modifier.weight(1f))
        } else if (searchRows.isNotEmpty() || groups.size > 1) {
            val column = rememberLazyListState()
            val anchorGroup = remember(restore.lastKey, groups) {
                groups.indexOfFirst { group -> group.cards.any { searchCardKey(it) == restore.lastKey } }
            }
            PageRestore(column, restore, target = { anchorGroup.takeIf { it >= 0 } })
            LazyColumn(Modifier.weight(1f), state = column, verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                groups.forEach { group ->
                    item(key = group.id) {
                        val anchoredRow = remember(group.cards, restore.lastKey) {
                            group.cards.indexOfFirst { searchCardKey(it) == restore.lastKey }
                        }
                        val row = rememberLazyListState()
                        PageRestore(row, restore, target = { anchoredRow.takeIf { it >= 0 } })
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(group.label, Modifier.padding(horizontal = gutter), style = MaterialTheme.typography.titleMedium)
                            group.message?.let { message ->
                                if (group.failed) SearchFailureLine(message, retry, Modifier.fillMaxWidth().padding(horizontal = gutter))
                                else Text(message, Modifier.padding(horizontal = gutter), color = Palette.secondary)
                            }
                            LazyRow(state = row, contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                rowItemsIndexed(group.cards, key = { _, card -> searchCardKey(card) }) { index, card ->
                                    val key = searchCardKey(card)
                                    MediaCard(card, width = 160, portrait = true, showLabels = true,
                                        searchQuery = term,
                                        open = { restore.mark(key, index); open(it) },
                                        modifier = Modifier.pageAnchor(restore, key, index, target = anchoredRow == index).listItemEntrance(index, entrance.animate))
                                }
                            }
                        }
                    }
                }
                footer?.let { render -> item(key = "footer") { render(Modifier.fillMaxWidth().padding(horizontal = gutter)) } }
            }
        } else {
            val grid = rememberLazyGridState()
            val anchored = restore.anchor(visible.size) { searchCardKey(visible[it]) }
            PageRestore(grid, restore, target = { restore.anchor(visible.size) { searchCardKey(visible[it]) } })
            LazyVerticalGrid(if (phone) GridCells.Fixed(3) else GridCells.Adaptive(172.dp), modifier = Modifier.weight(1f), state = grid, horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 20.dp), contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp)) {
                gridItemsIndexed(visible, key = { _, card -> searchCardKey(card) }) { index, card ->
                    val key = searchCardKey(card)
                    MediaCard(card, width = if (phone) 112 else 172, portrait = true, showLabels = true,
                        searchQuery = term,
                        open = { restore.mark(key, index); open(it) },
                        modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index).listItemEntrance(index, entrance.animate))
                }
                footer?.let { render -> item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { render(Modifier.fillMaxWidth()) } }
            }
        }
    }
}
}

@Composable
private fun SearchFailureLine(message: String, retry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, Modifier.weight(1f, fill = false), color = Palette.danger)
        if (retry != null) Text("Try again", Modifier
            .pointerClickable(onClickLabel = "Try again", shape = RoundedCornerShape(8.dp)) { retry() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchIdle(recent: List<String>, trending: List<String>, gutter: Dp, query: (String) -> Unit,
    clearRecents: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).pageBottomClearance().padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("Clear", Modifier.pointerClickable(onClickLabel = "Clear", shape = RoundedCornerShape(8.dp), hover = null) { clearRecents() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Palette.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            recent.forEach { SearchTermRow(it, query) }
        }
        if (trending.isNotEmpty()) {
            if (recent.isNotEmpty()) Spacer(Modifier.height(16.dp))
            Text("Trending searches", color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            trending.forEach { SearchTermRow(it, query) }
        }
    }
}

@Composable
private fun SearchTermRow(term: String, run: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(term, Modifier.fillMaxWidth()
        .pointerClickable(interactionSource = interaction, onClickLabel = term, shape = RoundedCornerShape(8.dp), hover = null, onClick = { run(term) })
        .padding(vertical = 6.dp),
        color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
