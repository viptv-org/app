package org.viptv.app.features.browse

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.rememberTvLoadingShimmer
import org.viptv.app.features.home.MediaCard
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.catalogSource
import org.viptv.app.connection.catalogTitle
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.state.Screen
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.Motion
import org.viptv.app.components.hoverFill
import org.viptv.app.components.pressScale

@Composable
fun BrowseScreen(screen: Screen, home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, loadedCards: List<Card>? = null, controls: org.viptv.app.connection.DiscoveryControls? = null,
    query: org.viptv.app.connection.BrowseQuery = org.viptv.app.connection.BrowseQuery("movie"), busy: Boolean = false, error: String? = null,
    filterChanged: (String, String) -> Unit = { _, _ -> }, more: (() -> Unit)? = null,
    multipleChanged: (String, List<String>) -> Unit = { _, _ -> }, browser: Boolean = false,
    applyQuery: (BrowseQuery) -> Unit = {}) {
    var sort by remember(screen) { mutableStateOf("Popular") }
    val cards = remember(home, loadedCards, screen, sort) {
        val loaded = loadedCards ?: home?.rows.orEmpty().flatMap { it.cards }
            .distinctBy(::cardLookupKey)
            .filter { when (screen) {
                Screen.Movies -> it.identity.kind == "movie"
                Screen.Series -> it.identity.kind == "series"
                Screen.Anime -> it.category == "anime" || it.identity.kind == "anime"
                else -> true
            } }
        when (sort) { "A–Z" -> loaded.sortedBy { it.title.lowercase() }; "Z–A" -> loaded.sortedByDescending { it.title.lowercase() }; else -> loaded }
    }
    if (television) {
        TvBrowseScreen(cards, busy || (loadedCards == null && home == null), open, controls = controls, query = query,
            filterChanged = filterChanged, multipleChanged = multipleChanged, applyQuery = applyQuery,
            more = more, error = error, retry = { applyQuery(query) })
        return
    }
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("browse")
    val anchored = restore.anchor(cards.size) { cardLookupKey(cards[it]) }
    PageRestore(grid, restore, target = { restore.anchor(cards.size) { cardLookupKey(cards[it]) } })
    AutomaticPage(cards.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val compact = width < 640.dp
        val gutter = if (compact) 16.dp else 40.dp
        val catalogOpen = remember { mutableStateOf(false) }
        val facets = remember(controls, query, screen) { browseFacets(controls, query, screen == Screen.Discover) }
        val applied = remember(facets, query) { appliedFacets(facets, query).size }
        val catalog = controls?.selectedCatalog(query)
        val peers = controls?.catalogs.orEmpty()
        val label = catalog?.catalogTitle(peers)
        val source = catalog?.catalogSource(peers)
        val loading = busy || (loadedCards == null && home == null)
        val serverSort = controls?.selectedCatalog(query)?.controls?.any { it.id == "sort" } == true ||
            controls?.controls?.any { it.id == "sort" } == true
        Column(Modifier.fillMaxSize().padding(start = gutter, end = gutter, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(screen.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (loading && controls == null) BrowseHeaderSkeleton()
            else BrowseFilterBar(controls, query, screen == Screen.Discover, filterChanged, multipleChanged, applyQuery,
                catalogOpen, gutter = gutter) {
                if (!serverSort) item(key = "local:sort") {
                    SearchableSelect("Sort", LocalSorts, listOf(sort), { sort = it.firstOrNull() ?: sort }, anyLabel = null)
                }
            }
            if (error != null && cards.isNotEmpty()) Text(error, color = Palette.danger, fontSize = 12.sp, lineHeight = 16.sp)
            val columns = when { compact -> 3; width >= 1200.dp -> 6; else -> 4 }
            val chooseCatalog = if (controls?.catalogs?.any { it.type == query.type } == true) ({ catalogOpen.value = true }) else null
            if (error != null && cards.isEmpty()) BrowseErrorState(label, catalog?.connection_name, error, Modifier.weight(1f), { applyQuery(query) }, chooseCatalog)
            else if (loading && cards.isEmpty()) BrowseSkeletonGrid(GridCells.Fixed(columns), Modifier.weight(1f))
            else if (cards.isEmpty()) BrowseEmptyState(source, applied, Modifier.weight(1f)) { applyQuery(clearedFilters(query)) }
            else LazyVerticalGrid(GridCells.Fixed(columns),
                state = grid, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
                contentPadding = PaddingValues(bottom = if (compact) 88.dp else 24.dp)) {
                itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }) { index, card ->
                    val key = cardLookupKey(card)
                    MediaCard(card, width = 0, portrait = true, showLabels = true,
                        open = { restore.mark(key, index); open(it) },
                        modifier = Modifier.fillMaxWidth().pageAnchor(restore, key, index, target = anchored == index),
                        showFocusOutline = !compact || !browser)
                }
            }
        }
    }
}

private val LocalSorts = listOf("Popular", "A–Z", "Z–A").map { SelectOption(it, it) }

@Composable
private fun BrowseHeaderSkeleton() {
    val shimmer = rememberTvLoadingShimmer(1f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(170, 120, 110).forEach { Box(Modifier.width(it.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).then(shimmer)) }
    }
}

@Composable
private fun BrowseSkeletonGrid(columns: GridCells, modifier: Modifier) {
    val shimmer = rememberTvLoadingShimmer(1f)
    LazyVerticalGrid(columns, modifier.fillMaxWidth(), userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(12) { Box(Modifier.fillMaxWidth().aspectRatio(2f / 3).clip(RoundedCornerShape(8.dp)).then(shimmer)) }
    }
}

@Composable
private fun BrowseEmptyState(source: String?, filters: Int, modifier: Modifier, clearAll: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 90.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.Search, null, Modifier.size(30.dp))
        Text("Nothing matches", fontSize = 22.sp, lineHeight = 25.sp, letterSpacing = (-0.44).sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(emptyBody(source, filters), color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = TextAlign.Center)
        BoardAction("Clear all filters", primary = true, run = clearAll)
    }
}

private fun emptyBody(source: String?, filters: Int): String =
    if (source != null && filters > 0) "$source has no titles with all $filters filters. Removing the newest one usually helps."
    else "No titles match these filters. Removing the newest one usually helps."

@Composable
private fun BrowseErrorState(title: String?, source: String?, error: String, modifier: Modifier,
    retry: () -> Unit, chooseCatalog: (() -> Unit)?) {
    Column(modifier.fillMaxWidth().padding(top = 90.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.CloudOff, null, Modifier.size(30.dp))
        Text("Couldn’t load ${title ?: "this catalog"}", fontSize = 22.sp, lineHeight = 25.sp,
            letterSpacing = (-0.44).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${source?.takeIf(String::isNotBlank) ?: "Your source"} didn’t answer in time. Your filters are kept.",
            color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        BoardAction("Try again", primary = true, run = retry, icon = Lucide.RotateCw)
        chooseCatalog?.let { BoardAction("Choose another catalog", primary = false, run = it) }
        Text(error, color = Palette.tertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BoardAction(label: String, primary: Boolean, run: () -> Unit, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val rest = if (primary) Palette.text else Color.White.copy(alpha = .12f)
    Row(Modifier.height(44.dp).pressScale(interaction, Motion.button).clip(RoundedCornerShape(12.dp))
        .background(hoverFill(interaction, rest))
        .pointerClickable(interactionSource = interaction, onClick = run, shape = RoundedCornerShape(12.dp), hover = null)
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = if (primary) Palette.background else Palette.text) }
        Text(label, color = if (primary) Palette.background else Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
