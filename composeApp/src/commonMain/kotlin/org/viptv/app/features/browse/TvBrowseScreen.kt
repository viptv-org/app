package org.viptv.app.features.browse

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.components.tvGridItem
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.TriangleAlert
import org.viptv.app.components.PageStateLayout
import org.viptv.app.components.TvAction
import org.viptv.app.components.AddonFailureNotice
import org.viptv.app.components.ListPageState
import org.viptv.app.components.listPageState
import org.viptv.app.components.LocalTvScale
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.AddonFailure
import org.viptv.engine.wire.Card

@Composable
internal fun TvBrowseScreen(cards: List<Card>, busy: Boolean, open: (Card) -> Unit,
    controls: DiscoveryControls? = null, query: BrowseQuery = BrowseQuery("movie"),
    filterChanged: (String, String) -> Unit = { _, _ -> }, multipleChanged: (String, List<String>) -> Unit = { _, _ -> },
    more: (() -> Unit)? = null, error: String? = null, applyQuery: (BrowseQuery) -> Unit = {},
    retry: (() -> Unit)? = null, pageError: String? = null,
    failures: List<AddonFailure> = emptyList(), manageAddons: () -> Unit = {}) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val selected = remember(cards) { cards.distinctBy(::cardLookupKey) }
    val pageState = listPageState(selected.size, busy, error)
    val facets = remember(controls, query) { browseFacets(controls, query, showType = false) }
    val applied = remember(facets, query) { appliedChips(facets, query) }
    val catalog = controls?.selectedCatalog(query)
    val peers = controls?.catalogs.orEmpty()
    val label = catalog?.catalogTitle(peers)
    val source = catalog?.catalogSource(peers)
    val restore = rememberPageRestore("browse")
    val anchored = restore.landing(selected.size) { cardLookupKey(selected[it]) }
    val chromeFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val noticeFocus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    PageRestore(grid, restore, claimOnEntry = true, target = { restore.landing(selected.size) { cardLookupKey(selected[it]) } })
    AutomaticPage(selected.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        CompositionLocalProvider(LocalTvScale provides scale) {
            Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, top = 8.dp * scale)) {
                if (controls != null) {
                    val catalogOpen = remember { mutableStateOf(false) }
                    Box(Modifier.focusGroup().focusRequester(chromeFocus).focusProperties { onEnter = { chromeFocus.requestFocus() } }) {
                        BrowseFilterBar(controls, query, showType = false, changed = filterChanged, multipleChanged = multipleChanged,
                            applyQuery = applyQuery, catalogOpen = catalogOpen,
                            down = if (selected.isNotEmpty() && failures.isNotEmpty() && !busy) noticeFocus else gridFocus)
                    }
                }
                Spacer(Modifier.height(40.dp * scale))
                if (pageState == ListPageState.Failed || pageState == ListPageState.Empty) {
                    CatalogPageState(
                        if (pageState == ListPageState.Failed) catalogFailureState(label, error, otherCatalog = false)
                        else catalogEmptyState(label, source, catalogNoun(query.type),
                            applied, otherCatalog = false),
                        activate = { action ->
                            when (action.kind) {
                                CatalogActionKind.Retry -> (retry ?: { applyQuery(query) }).invoke()
                                CatalogActionKind.ClearAll -> applyQuery(clearedFilters(query))
                                CatalogActionKind.Remove -> action.chip?.let { chip ->
                                    if (chip.multiple) multipleChanged(chip.id, query.selections(chip.id) - chip.value)
                                    else filterChanged(chip.id, "")
                                }
                                CatalogActionKind.ChooseCatalog -> Unit
                            }
                        }, Modifier.weight(1f), layout = PageStateLayout.Tv, scale = scale, focus = chromeFocus)
                } else {
                    if (pageState == ListPageState.Loading) {
                        TvBrowseLoading(scale)
                    } else {
                        if (error != null) Text(error, color = Palette.danger, fontSize = (22 * scale).sp,
                            modifier = Modifier.padding(bottom = 12.dp * scale))
                        if (selected.isNotEmpty() && failures.isNotEmpty()) {
                            AddonFailureNotice(failures, manageAddons, Modifier.padding(bottom = 20.dp * scale),
                                television = true, scale = scale, focus = noticeFocus)
                        }

                        LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f), state = grid,
                            horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                            verticalArrangement = Arrangement.spacedBy(36.dp * scale),
                            contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                            itemsIndexed(selected, key = { _, card -> cardLookupKey(card) }) { index, card ->
                                val key = cardLookupKey(card)
                                TvCatalogCard(card, scale, true,
                                    Modifier.tvGridItem(index)
                                        .then(if (index == 0) Modifier.focusRequester(gridFocus) else Modifier)
                                        .pageAnchor(restore, key, index, target = anchored == index)
                                        .focusProperties {
                                            if (index < 7) up = if (selected.isNotEmpty() && failures.isNotEmpty() && !busy) noticeFocus else chromeFocus
                                            if (index % 7 == 6) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                        },
                                    {}, { restore.mark(key, index); open(it) }, focusedCaptions = true)
                            }
                            if (selected.isNotEmpty() && !busy && pageError != null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                                ListEndFooter(failed = true, retry = more, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                            } else if (selected.isNotEmpty() && busy) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                                ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                            } else if (selected.isNotEmpty() && !busy && pageError == null && more == null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                                ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = true, scale = scale)
                            }
                        }
                    }
                }
            }
        }
    }
}

