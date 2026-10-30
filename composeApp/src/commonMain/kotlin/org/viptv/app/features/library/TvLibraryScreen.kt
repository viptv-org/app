package org.viptv.app.features.library

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.components.tvGridItem
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.LocalTvScale
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.ServerMessages
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvLibraryScreen(cards: List<Card>, available: Boolean, open: (Card) -> Unit, home: () -> Unit,
    lastWatched: Map<String, Long> = emptyMap(), snapshot: org.viptv.engine.wire.LibrarySnapshot? = null,
    query: org.viptv.engine.wire.LibraryQuery = org.viptv.app.connection.defaultLibraryQuery(),
    changeQuery: (org.viptv.engine.wire.LibraryQuery) -> Unit = {}, more: (() -> Unit)? = null, busy: Boolean = false, error: String? = null) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(if (lastWatched.isEmpty()) "Date added" else "Last watched") }
    val selectedFilter = remember { FocusRequester() }
    val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
    val visible = if (snapshot != null) cards else when (sort) {
        "A–Z" -> filtered.sortedBy { it.title.lowercase() }
        "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
        "Last watched" -> filtered.sortedByDescending { lastWatched[it.identity.id] ?: Long.MIN_VALUE }
        else -> filtered
    }.distinctBy(::cardLookupKey)
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("library")
    val anchored = restore.landing(visible.size) { cardLookupKey(visible[it]) }
    val empty = libraryEmptyState(snapshot, query, available)
    PageRestore(grid, restore, claimOnEntry = true, target = { restore.landing(visible.size) { cardLookupKey(visible[it]) } })
    AutomaticPage(visible.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        CompositionLocalProvider(LocalTvScale provides scale) {
            Column(Modifier.fillMaxSize().focusGroup()
                .padding(horizontal = 96.dp * scale).padding(top = 8.dp * scale)) {
                Box(Modifier.focusGroup().focusRequester(selectedFilter)) {
                    if (snapshot != null) LibraryFilterBar(snapshot, query, changeQuery)
                    else LibraryControls(filter, { filter = it }, sort, { sort = it }, watchedAvailable = lastWatched.isNotEmpty())
                }
                Spacer(Modifier.height(32.dp * scale))
                if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
                (error ?: snapshot?.reason?.let { ServerMessages.human(it, ServerMessages.LIST_REASON) })?.let {
                    Text(it, color = if (error != null) Palette.danger else Palette.secondary, fontSize = (24 * scale).sp)
                }
                if (visible.isEmpty()) Column(Modifier.padding(top = 120.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    Text(empty.title, fontSize = (32 * scale).sp)
                    Text(empty.message, fontSize = (24 * scale).sp, color = Palette.secondary)
                    TvAction(Modifier.height(64.dp * scale), scale, primary = true,
                        activate = { if (empty.clearFilters) changeQuery(org.viptv.app.connection.defaultLibraryQuery().copy(source = query.source)) else home() },
                        contentPadding = PaddingValues(horizontal = 28.dp * scale)) { Text(if (empty.clearFilters) "Clear filters" else "Browse Home", color = it, fontSize = (24 * scale).sp) }
                } else
                    LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f), state = grid,
                        horizontalArrangement = Arrangement.spacedBy(16.dp * scale), verticalArrangement = Arrangement.spacedBy(22.dp * scale),
                        contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                        itemsIndexed(visible, key = { _, card -> cardLookupKey(card) }) { index, card ->
                            val key = cardLookupKey(card)
                            TvCatalogCard(card, scale, true, Modifier.tvGridItem(index)
                                .pageAnchor(restore, key, index, target = anchored == index)
                                .focusProperties {
                                    if (index < 7) up = selectedFilter
                                    if (index % 7 == 6) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                }, {}, { restore.mark(key, index); open(it) }, focusedCaptions = true)
                        }
                    }
            }
        }
    }
}
