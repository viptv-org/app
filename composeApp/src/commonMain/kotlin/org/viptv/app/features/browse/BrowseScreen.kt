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
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.state.Screen
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.HoverFill
import org.viptv.app.components.pointerHovered

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
            onQuery = { next ->
                // One batched request for the whole draft (Show results).
                if (next.type != query.type) filterChanged("type", next.type) else applyQuery(next)
            },
            more = more, error = error, retry = { applyQuery(query) })
        return
    }
    val grid = rememberLazyGridState()
    AutomaticPage(cards.size, busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val compact = width < 640.dp
        val gutter = if (compact) 16.dp else 40.dp
        val catalogOpen = remember { mutableStateOf(false) }
        val facets = remember(controls, query, screen) { browseFacets(controls, query, screen == Screen.Discover) }
        val applied = remember(facets, query) { appliedFacets(facets, query).size }
        val catalog = controls?.selectedCatalog(query)
        val loading = busy || (loadedCards == null && home == null)
        // Server-supplied facets already include Sort; a second local sort would contradict them.
        val serverSort = controls?.selectedCatalog(query)?.controls?.any { it.id == "sort" } == true ||
            controls?.controls?.any { it.id == "sort" } == true
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(screen.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            // One filter bar (app#117): the catalog and every facet are dropdown chips.
            if (loading && controls == null) BrowseHeaderSkeleton()
            else BrowseFilterBar(controls, query, screen == Screen.Discover, filterChanged, multipleChanged, applyQuery,
                catalogOpen, gutter = gutter) {
                if (!serverSort) item(key = "local:sort") {
                    SearchableSelect("Sort", LocalSorts, listOf(sort), { sort = it.firstOrNull() ?: sort }, anyLabel = null)
                }
            }
            if (error != null && cards.isNotEmpty()) Text(error, color = Palette.danger, fontSize = 12.sp, lineHeight = 16.sp)
            // Column count from the boards: M3Browse is 3 across on a phone,
            // D3Browse is 6 across at desktop width and 4 in its narrower
            // variant. Cards fill their cell (minmax(0,1fr)), never a fixed px.
            val columns = when { compact -> 3; width >= 1200.dp -> 6; else -> 4 }
            val chooseCatalog = if (controls?.catalogs?.any { it.type == query.type } == true) ({ catalogOpen.value = true }) else null
            if (error != null && cards.isEmpty()) BrowseErrorState(catalog, error, Modifier.weight(1f), { applyQuery(query) }, chooseCatalog)
            else if (loading && cards.isEmpty()) BrowseSkeletonGrid(GridCells.Fixed(columns), Modifier.weight(1f))
            else if (cards.isEmpty()) BrowseEmptyState(catalog, applied, Modifier.weight(1f)) { applyQuery(clearedFilters(query)) }
            else LazyVerticalGrid(GridCells.Fixed(columns),
                state = grid, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
                contentPadding = PaddingValues(bottom = if (compact) 88.dp else 24.dp)) {
                items(cards, key = ::cardLookupKey) {
                    MediaCard(it, width = 0, portrait = true, showLabels = true, open = open,
                        modifier = Modifier.fillMaxWidth(), showFocusOutline = !compact || !browser)
                }
            }
        }
    }
}

/** The local order when the provider offers no Sort of its own. */
private val LocalSorts = listOf("Popular", "A–Z", "Z–A").map { SelectOption(it, it) }

/** `M3BrowseLoading`: the filter bar's chips before the discovery controls have arrived. */
@Composable
private fun BrowseHeaderSkeleton() {
    val shimmer = rememberTvLoadingShimmer(1f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(170, 120, 110).forEach { Box(Modifier.width(it.dp).height(40.dp).clip(RoundedCornerShape(20.dp)).then(shimmer)) }
    }
}

/** `M3BrowseLoading`: twelve 2:3 placeholders in the same grid the results use. */
@Composable
private fun BrowseSkeletonGrid(columns: GridCells, modifier: Modifier) {
    val shimmer = rememberTvLoadingShimmer(1f)
    LazyVerticalGrid(columns, modifier.fillMaxWidth(), userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(12) { Box(Modifier.fillMaxWidth().aspectRatio(2f / 3).clip(RoundedCornerShape(8.dp)).then(shimmer)) }
    }
}

/** `M3BrowseEmpty`: 30dp icon, 22sp title, one paragraph, then the recovery action. */
@Composable
private fun BrowseEmptyState(catalog: DiscoveryCatalog?, filters: Int, modifier: Modifier, clearAll: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 90.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.Search, null, Modifier.size(30.dp))
        Text("Nothing matches", fontSize = 22.sp, lineHeight = 25.sp, letterSpacing = (-0.44).sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(emptyBody(catalog, filters), color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = TextAlign.Center)
        BoardAction("Clear all filters", primary = true, run = clearAll)
    }
}

private fun emptyBody(catalog: DiscoveryCatalog?, filters: Int): String {
    val source = catalog?.let { it.name + (it.connection_name?.takeIf(String::isNotBlank)?.let { name -> " from $name" } ?: "") }
    return if (source != null && filters > 0) "$source has no titles with all $filters filters. Removing the newest one usually helps."
    else "No titles match these filters. Removing the newest one usually helps."
}

/** `M3BrowseError`: Try again re-runs the same query; the catalog list is the escape hatch. */
@Composable
private fun BrowseErrorState(catalog: DiscoveryCatalog?, error: String, modifier: Modifier,
    retry: () -> Unit, chooseCatalog: (() -> Unit)?) {
    Column(modifier.fillMaxWidth().padding(top = 90.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.CloudOff, null, Modifier.size(30.dp))
        Text("Couldn’t load ${catalog?.name ?: "this catalog"}", fontSize = 22.sp, lineHeight = 25.sp,
            letterSpacing = (-0.44).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${catalog?.connection_name?.takeIf(String::isNotBlank) ?: "Your source"} didn’t answer in time. Your filters are kept.",
            color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        BoardAction("Try again", primary = true, run = retry, icon = Lucide.RotateCw)
        chooseCatalog?.let { BoardAction("Choose another catalog", primary = false, run = it) }
        Text(error, color = Palette.tertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    }
}

/** Board button geometry: 44dp tall, 12dp radius, 18dp side padding (`M3BrowseEmpty`/`M3BrowseError`). */
@Composable
private fun BoardAction(label: String, primary: Boolean, run: () -> Unit, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val rest = if (primary) Palette.text else Color.White.copy(alpha = .12f)
    Row(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (interaction.pointerHovered()) HoverFill.container(rest) else rest)
        .pointerClickable(interactionSource = interaction, onClick = run, shape = RoundedCornerShape(12.dp), hover = null)
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = if (primary) Palette.background else Palette.text) }
        Text(label, color = if (primary) Palette.background else Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
