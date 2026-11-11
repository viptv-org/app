package org.viptv.app.features.browse

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AddonFailureNotice
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListPageState
import org.viptv.app.components.PosterSkeleton
import org.viptv.app.components.listPageState
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
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
import org.viptv.app.state.openAddonsSettings
import org.viptv.app.state.retryBrowse
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.AddonFailure
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.QueryControl
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pageGutter
import org.viptv.app.components.pageStateLayout
import org.viptv.app.components.Motion
import org.viptv.app.components.hoverFill
import org.viptv.app.components.pressScale
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
        multipleChanged = controller::browseSelections, applyQuery = controller::applyBrowse, retry = controller::retryBrowse,
        pageError = state.browsePageError, failures = state.browseFailures, manageAddons = controller::openAddonsSettings)
}

@Composable
internal fun BrowserBrowseScreen(screen: Screen, cards: List<Card>, controls: DiscoveryControls?, query: BrowseQuery,
    busy: Boolean, error: String?, more: (() -> Unit)?, open: (Card) -> Unit,
    changed: (String, String) -> Unit, multipleChanged: (String, List<String>) -> Unit,
    applyQuery: (BrowseQuery) -> Unit, retry: () -> Unit = {}, pageError: String? = null,
    failures: List<AddonFailure> = emptyList(), manageAddons: () -> Unit = {}) {
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("browse")
    val anchored = restore.anchor(cards.size) { cardLookupKey(cards[it]) }
    PageRestore(grid, restore, target = { restore.anchor(cards.size) { cardLookupKey(cards[it]) } })
    AutomaticPage(cards.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    val facets = remember(controls, query, screen) { browseFacets(controls, query, screen == Screen.Discover) }
    val applied = remember(facets, query) { appliedChips(facets, query) }
    val clearAll = { applyQuery(clearedFilters(query)) }
    val catalogOpen = remember { mutableStateOf(false) }
    val catalog = controls?.selectedCatalog(query)
    val peers = controls?.catalogs.orEmpty()
    val label = catalog?.catalogTitle(peers)
    val source = catalog?.catalogSource(peers)
    val chooseCatalog = { catalogOpen.value = true }
    val otherCatalog = peers.count { it.type == query.type } > 1
    val pageState = listPageState(cards.size, busy, error)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        val gutter = pageGutter(television = false, tablet = false, browser = true, maxWidth)
        Column(Modifier.fillMaxSize().padding(start = gutter, end = gutter, top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(screen.name, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-.52).sp, fontWeight = FontWeight.Bold)
            if (controls != null) BrowseFilterBar(controls, query, screen == Screen.Discover, changed, multipleChanged, applyQuery, catalogOpen)
            if (cards.isNotEmpty() && failures.isNotEmpty()) AddonFailureNotice(failures, manageAddons)
            when (pageState) {
                ListPageState.Loading -> PosterSkeleton(phone = false, gap = 14.dp, tile = 172.dp)
                ListPageState.Failed, ListPageState.Empty -> CatalogPageState(
                    if (pageState == ListPageState.Failed) catalogFailureState(label, error, otherCatalog)
                    else catalogEmptyState(label, source, browseNoun(screen), applied, otherCatalog),
                    activate = { action ->
                        when (action.kind) {
                            CatalogActionKind.Retry -> retry()
                            CatalogActionKind.ChooseCatalog -> chooseCatalog()
                            CatalogActionKind.ClearAll -> clearAll()
                            CatalogActionKind.Remove -> action.chip?.let { chip ->
                                if (chip.multiple) multipleChanged(chip.id, query.selections(chip.id) - chip.value)
                                else changed(chip.id, "")
                            }
                        }
                    }, Modifier.weight(1f), layout = pageStateLayout(television = false, compact = compact))
                ListPageState.Content -> LazyVerticalGrid(GridCells.Adaptive(172.dp), state = grid,
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }) { index, card ->
                        val key = cardLookupKey(card)
                        MediaCard(card, width = 172, portrait = true, showLabels = true, showFocusOutline = false,
                            open = { restore.mark(key, index); open(it) },
                            modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index))
                    }
                    if (cards.isNotEmpty() && !busy && pageError != null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                        ListEndFooter(failed = true, retry = more ?: retry, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                    } else if (cards.isNotEmpty() && busy) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                        ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                    } else if (cards.isNotEmpty() && !busy && pageError == null && more == null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                        ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
                    }
                }
            }
        }
    }
}



