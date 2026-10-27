package org.viptv.app.features.search

import org.viptv.app.components.bleedHorizontally
import org.viptv.app.components.edgeToEdgeScroll
import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.components.pointerClickable

@Composable
fun SearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean, error: String?, desktop: Boolean,
    query: (String) -> Unit, open: (Card) -> Unit, television: Boolean = false, editQuery: ((String) -> String) -> Unit,
    searchRows: List<SearchResultRow> = emptyList()) {
    if (television) {
        TvSearchScreen(term, results, addons, busy, error, query, open, editQuery, searchRows = searchRows)
        return
    }
    var filter by remember { mutableStateOf("All") }
    val visible = results.distinctBy(::searchCardKey).filter { it.matchesSearchFilter(filter) }
    val groups = if (searchRows.isNotEmpty()) searchResultGroups(searchRows, filter) else searchGroups(visible, addons)
    val hasResults = if (searchRows.isNotEmpty()) groups.any { it.cards.isNotEmpty() } else visible.isNotEmpty()
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val phone = maxWidth < 640.dp
    val gutter = if (phone) 16.dp else 40.dp
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = gutter, vertical = if (phone) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)) {
        if (!phone) Text(if (desktop && term.isNotBlank()) "Results for “$term”" else "Search",
            style = if (desktop) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        if (!desktop) SearchField(term, query)
        org.viptv.app.components.TvFocusScroll(horizontal = true) { Row(Modifier.fillMaxWidth().edgeToEdgeScroll(rememberScrollState(), gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Movies", "Series", "Anime", "Channels").forEach { tab ->
                // DUI-06: the chips are the desktop search page's only controls and
                // had neither state. Board `Chip · hover` = white 6%; the ring is
                // the shared 2/2 focus outline.
                val interactions = remember { MutableInteractionSource() }
                val hovered by interactions.collectIsHoveredAsState()
                var focused by remember { mutableStateOf(false) }
                Text(tab, Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .tvFocusOutline(focused, 18.dp, darkGap = 2.dp, width = 2.dp)
                    .background(if (hovered) (if (filter == tab) Color.White else Color(0xFF2A2A2E))
                        else (if (filter == tab) Palette.text else Color(0xFF202022)), RoundedCornerShape(18.dp))
                    .pointerClickable(interactionSource = interactions, indication = null, onClickLabel = tab, shape = RoundedCornerShape(18.dp), hover = null) { filter = tab }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (filter == tab) Palette.background else Palette.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        error?.let { Text(it, color = Palette.danger) }
        if (!busy && term.isNotBlank() && !hasResults && error == null && groups.none { it.message != null }) Text("No matches. Try another search.", color = Palette.secondary)
        if (phone && term.isNotBlank() && hasResults && searchRows.isEmpty()) Text("Top results", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (searchRows.isNotEmpty() || groups.size > 1) {
            // The rows run to the screen edges (app#63); labels and first cards keep the gutter.
            LazyColumn(Modifier.weight(1f).bleedHorizontally(gutter), verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = if (phone) 88.dp else 24.dp)) {
                groups.forEach { group ->
                    item(key = group.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(group.label, Modifier.padding(horizontal = gutter), style = MaterialTheme.typography.titleMedium)
                            (group.message ?: "No matches.".takeIf { group.cards.isEmpty() })?.let { Text(it, Modifier.padding(horizontal = gutter), color = Palette.secondary) }
                            LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                rowItems(group.cards, key = ::searchCardKey) { MediaCard(it, width = 160, portrait = true, showLabels = true, open = open) }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(if (phone) GridCells.Fixed(3) else GridCells.Adaptive(172.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 20.dp), contentPadding = PaddingValues(bottom = if (phone) 88.dp else 24.dp)) {
                items(visible, key = ::searchCardKey) { MediaCard(it, width = if (phone) 112 else 172, portrait = true, showLabels = true, open = open) }
            }
        }
    }
}
}
