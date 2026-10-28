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
import org.viptv.app.components.TvAction
import org.viptv.app.components.LocalTvScale
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.components.AutomaticPage
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

/**
 * TV Movies / Series / Anime: the same filter bar as every other layout (app#121) — the
 * catalog chip first, then one dropdown chip per facet, each opening its list as the right
 * panel — above a 7-column grid that appends pages. The top navigation owns the page heading,
 * so there is no in-page title.
 */
@Composable
internal fun TvBrowseScreen(cards: List<Card>, busy: Boolean, open: (Card) -> Unit,
    controls: DiscoveryControls? = null, query: BrowseQuery = BrowseQuery("movie"),
    filterChanged: (String, String) -> Unit = { _, _ -> }, multipleChanged: (String, List<String>) -> Unit = { _, _ -> },
    more: (() -> Unit)? = null, error: String? = null, applyQuery: (BrowseQuery) -> Unit = {},
    retry: (() -> Unit)? = null, initialCard: Int = 0) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val selected = remember(cards) { cards.distinctBy(::cardLookupKey) }
    val targetIndex = initialCard.coerceIn(0, selected.lastIndex.coerceAtLeast(0))
    val first = remember { FocusRequester() }
    val chromeFocus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) {
        if (selected.isNotEmpty()) {
            grid.scrollToItem(targetIndex)
            withFrameNanos { }
            first.requestFocus()
        } else chromeFocus.requestFocus()
    }
    AutomaticPage(selected.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        // The shared chips size and open their right panel with the board's scale (app#121).
        CompositionLocalProvider(LocalTvScale provides scale) {
            Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, top = 8.dp * scale)) {
                if (controls != null) {
                    val catalogOpen = remember { mutableStateOf(false) }
                    // One focus group, so grid Up and the page's onEnter land on the first chip.
                    Box(Modifier.focusGroup().focusRequester(chromeFocus).focusProperties { onEnter = { chromeFocus.requestFocus() } }) {
                        BrowseFilterBar(controls, query, showType = false, changed = filterChanged, multipleChanged = multipleChanged,
                            applyQuery = applyQuery, catalogOpen = catalogOpen)
                    }
                }
                Spacer(Modifier.height(40.dp * scale))
                if (error != null && selected.isEmpty()) {
                    TvBrowseStatus(scale, Lucide.TriangleAlert, "Couldn’t load this catalog", error,
                        retry?.let { "Try again" to it }, chromeFocus)
                } else if (!busy && selected.isEmpty()) {
                    TvBrowseStatus(scale, Lucide.SearchX, "Nothing matches these filters",
                        "Clear a filter or choose another catalog to see more titles.",
                        "Clear all filters" to { applyQuery(clearedFilters(query)) }, chromeFocus)
                } else {
                    if (busy && selected.isEmpty()) {
                        TvBrowseLoading(scale)
                    } else {
                        if (error != null) Text(error, color = Palette.danger, fontSize = (22 * scale).sp,
                            modifier = Modifier.padding(bottom = 12.dp * scale))

                        LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f), state = grid,
                            horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                            verticalArrangement = Arrangement.spacedBy(36.dp * scale),
                            contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                            itemsIndexed(selected, key = { _, card -> cardLookupKey(card) }) { index, card ->
                                TvCatalogCard(card, scale, true,
                                    Modifier.tvGridItem(index).then(if (index == targetIndex) Modifier.focusRequester(first) else Modifier)
                                        .focusProperties {
                                            if (index < 7) up = chromeFocus
                                            // Stop at the trailing row edge instead of jumping to the avatar.
                                            if (index % 7 == 6) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                        },
                                    {}, open, focusedCaptions = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvBrowseStatus(scale: Float, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    message: String, action: Pair<String, () -> Unit>?, focus: FocusRequester) {
    var attached by remember { mutableStateOf(false) }
    // The action lambda is a fresh instance per recomposition; keying the claim
    // on it re-stole focus on every reload. The label is the stable identity.
    TvRequestedFocus(focus, action?.first, enabled = true)
    Column(Modifier.fillMaxWidth().padding(top = 96.dp * scale), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        Icon(icon, null, Modifier.size(56.dp * scale), tint = Palette.secondary)
        Text(title, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp * scale))
        action?.let { (label, run) ->
            TvAction(Modifier.height(72.dp * scale).width(320.dp * scale).then(if (!attached) Modifier.focusRequester(focus) else Modifier),
                scale, primary = true, activate = run, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                Text(label, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
            }
            LaunchedEffect(Unit) { attached = true }
        }
    }
}
