package org.viptv.app.features.browse

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.connection.catalogSource
import org.viptv.app.connection.catalogTitle
import org.viptv.app.connection.selections
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.features.home.MediaCard
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.applyBrowse
import org.viptv.app.state.browseFilter
import org.viptv.app.state.browseMore
import org.viptv.app.state.browseSelections
import org.viptv.app.state.open
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.HoverFill
import org.viptv.app.components.pointerHovered
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
internal fun BrowserBrowseRoute(state: ApplicationState, controller: ApplicationController) {
    val cards = remember(state.browseCards, state.screen) {
        state.browseCards.distinctBy(::cardLookupKey).filter { card ->
            when (state.screen) {
                Screen.Movies -> card.identity.kind == "movie"
                Screen.Series -> card.identity.kind == "series"
                Screen.Anime -> card.category == "anime" || card.identity.kind == "anime"
                else -> true
            }
        }
    }
    BrowserBrowseScreen(state.screen, cards, state.browseControls, state.browseQuery, state.busy, state.error,
        more = if (state.browseNext != null) controller::browseMore else null,
        open = controller::open, changed = controller::browseFilter,
        multipleChanged = controller::browseSelections, applyQuery = controller::applyBrowse)
}

@Composable
internal fun BrowserBrowseScreen(screen: Screen, cards: List<Card>, controls: DiscoveryControls?, query: BrowseQuery,
    busy: Boolean, error: String?, more: (() -> Unit)?, open: (Card) -> Unit,
    changed: (String, String) -> Unit, multipleChanged: (String, List<String>) -> Unit,
    applyQuery: (BrowseQuery) -> Unit) {
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("browse")
    val anchored = restore.anchor(cards.size) { cardLookupKey(cards[it]) }
    PageRestore(grid, restore, target = { restore.anchor(cards.size) { cardLookupKey(cards[it]) } })
    AutomaticPage(cards.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    val facets = remember(controls, query, screen) { browseFacets(controls, query, screen == Screen.Discover) }
    val applied = remember(facets, query) { browserApplied(facets, query) }
    val clearAll = { applyQuery(clearedFilters(query)) }
    val catalogOpen = remember { mutableStateOf(false) }
    val catalog = controls?.selectedCatalog(query)
    val peers = controls?.catalogs.orEmpty()
    val label = catalog?.catalogTitle(peers)
    val source = catalog?.catalogSource(peers)
    val chooseCatalog = { catalogOpen.value = true }
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(screen.name, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-.52).sp, fontWeight = FontWeight.Bold)
        if (controls != null) BrowseFilterBar(controls, query, screen == Screen.Discover, changed, multipleChanged, applyQuery, catalogOpen)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        else if (error != null) BrowserBrowseFailure(error, label, catalog?.connection_name, chooseCatalog)
        else if (cards.isEmpty()) BrowserBrowseEmpty(screen, source, applied.size, applied.lastOrNull(), query, clearAll, changed, multipleChanged, chooseCatalog)
        LazyVerticalGrid(GridCells.Adaptive(172.dp), state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }) { index, card ->
                val key = cardLookupKey(card)
                MediaCard(card, width = 172, portrait = true, showLabels = true, showFocusOutline = false,
                    open = { restore.mark(key, index); open(it) },
                    modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index))
            }
        }
    }
}

internal data class BrowserAppliedChip(val id: String, val value: String, val label: String, val multiple: Boolean)

private fun browserApplied(facets: List<QueryControl>, query: BrowseQuery): List<BrowserAppliedChip> = appliedFacets(facets, query).flatMap { control ->
    query.facetValues(control).map { value ->
        BrowserAppliedChip(control.id, value, control.choices.find { it.value == value }?.label ?: value,
            control.kind != ControlKind.SINGLECHOICE)
    }
}

@Composable
private fun BrowserBrowseEmpty(screen: Screen, source: String?, count: Int, newest: BrowserAppliedChip?,
    query: BrowseQuery, clearAll: () -> Unit, changed: (String, String) -> Unit,
    multipleChanged: (String, List<String>) -> Unit, openCatalog: () -> Unit) {
    val noun = when (screen) {
        Screen.Movies -> "movies"
        Screen.Series -> "series"
        Screen.Anime -> "anime"
        else -> "titles"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nothing matches these filters", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(source?.let { "$it has no $noun with all $count filters. Removing the newest one usually helps." }
            ?: "No $noun are available for these filters.", fontSize = 14.sp, color = Palette.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (newest != null) BrowserStateAction(if (newest.label.length > 24) "Remove filter" else "Remove “${newest.label}”", primary = true) {
                if (newest.multiple) multipleChanged(newest.id, query.selections(newest.id) - newest.value) else changed(newest.id, "")
            }
            BrowserStateAction("Clear all filters", primary = false, onClick = clearAll)
            if (source != null) BrowserStateAction("Choose another catalog", primary = false, onClick = openCatalog)
        }
    }
}

@Composable
private fun BrowserBrowseFailure(error: String, title: String?, source: String?, openCatalog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title?.let { "Couldn’t load $it" } ?: "Couldn’t load this page", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${source?.takeIf(String::isNotBlank) ?: "The source"} didn’t answer. Your filters are kept.", fontSize = 14.sp, color = Palette.secondary)
        Text(error, fontSize = 13.sp, color = Palette.danger)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) BrowserStateAction("Choose another catalog", primary = true, onClick = openCatalog)
        }
    }
}

@Composable
private fun BrowserStateAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val rest = if (primary) Palette.text else Color.Transparent
    Row(Modifier.height(38.dp).clip(RoundedCornerShape(12.dp))
        .background(if (interaction.pointerHovered()) HoverFill.container(rest) else rest)
        .then(if (primary) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp)))
        .pointerClickable(interactionSource = interaction, onClick = onClick, shape = RoundedCornerShape(12.dp), hover = null).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (primary) Palette.background else Palette.text)
    }
}
