package org.viptv.app.features.browse

import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.components.rememberTvLoadingShimmer
import org.viptv.app.features.home.MediaCard
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.displayName
import org.viptv.app.connection.forCatalog
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.connection.selectionKey
import org.viptv.app.connection.selections
import org.viptv.app.state.Screen
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.QueryControl

@Composable
fun BrowseScreen(screen: Screen, home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, loadedCards: List<Card>? = null, controls: org.viptv.app.connection.DiscoveryControls? = null,
    query: org.viptv.app.connection.BrowseQuery = org.viptv.app.connection.BrowseQuery("movie"), busy: Boolean = false, error: String? = null,
    filterChanged: (String, String) -> Unit = { _, _ -> }, more: (() -> Unit)? = null,
    multipleChanged: (String, List<String>) -> Unit = { _, _ -> }, browser: Boolean = false,
    applyQuery: (BrowseQuery) -> Unit = {}) {
    var sort by remember(screen) { mutableStateOf("Popular") }
    var menu by remember { mutableStateOf(false) }
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
        val compact = width < 640.dp && !television
        val gutter = if (television) 70.dp else if (compact) 16.dp else 40.dp
        var catalogs by remember { mutableStateOf(false) }
        var facetsOpen by remember { mutableStateOf(false) }
        val facets = remember(controls, query) { activeFacets(controls, query) }
        val applied = remember(facets) { facets.sumOf { it.second.size } }
        val catalog = controls?.selectedCatalog(query)
        val loading = busy || (loadedCards == null && home == null)
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = if (television) 32.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(screen.name, fontSize = if (television) 36.sp else 28.sp, fontWeight = FontWeight.Bold)
            // The Catalog/Filters bar only exists when the server supplied
            // browsable controls; without them it would be two dead chips.
            if (loading && controls == null) BrowseHeaderSkeleton()
            else if (controls != null) PhoneBrowseBar(catalog, applied, { catalogs = true }) { facetsOpen = !facetsOpen }
            // Server-supplied facets already include Sort; a second local sort
            // control would contradict them (and duplicated on phone).
            val serverSort = controls?.selectedCatalog(query)?.controls?.any { it.id == "sort" } == true ||
                controls?.controls?.any { it.id == "sort" } == true
            if (facets.isNotEmpty() || !serverSort) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhoneFilterChips(facets, Modifier.weight(1f)) { control, value ->
                    val values = facets.firstOrNull { it.first.id == control.id }?.second.orEmpty()
                    if (values.size <= 1) filterChanged(control.id, "") else multipleChanged(control.id, values - value)
                }
                if (!serverSort) SortMenu(sort, menu, { sort = it; menu = false }, { menu = !menu })
            }
            if (facetsOpen && controls != null) DiscoveryFilters(controls, query, screen == Screen.Discover, filterChanged,
                multipleChanged = multipleChanged, catalogPill = true)
            if (error != null && cards.isNotEmpty()) Text(error, color = Palette.danger, fontSize = 12.sp, lineHeight = 16.sp)
            // Column count from the boards: M3Browse is 3 across on a phone,
            // D3Browse is 6 across at desktop width and 4 in its narrower
            // variant. Cards fill their cell (minmax(0,1fr)), never a fixed px.
            val columns = when { compact -> 3; width >= 1200.dp -> 6; else -> 4 }
            if (error != null && cards.isEmpty()) BrowseErrorState(catalog, error, Modifier.weight(1f), { applyQuery(query) }) { if (controls != null) catalogs = true }
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
            if (catalogs && controls != null) PhoneCatalogsSheet(controls, query, { catalogs = false }) { selected ->
                catalogs = false
                applyQuery(query.forCatalog(selected))
            }
        }
    }
}

@Composable
private fun SortMenu(sort: String, menu: Boolean, select: (String) -> Unit, toggle: () -> Unit) {
    Box {
        Row(Modifier.background(Color(0xFF222224), RoundedCornerShape(8.dp))
            .clickable { toggle() }.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(16.dp))
            Text(sort, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            Icon(Lucide.ChevronDown, null, Modifier.size(16.dp))
        }
        DropdownMenu(menu, onDismissRequest = toggle) {
            listOf("Popular", "A–Z", "Z–A").forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { select(option) })
            }
        }
    }
}

/** The facet values the header counts and the chip strip removes (`M3Browse`). */
private fun activeFacets(controls: DiscoveryControls?, query: BrowseQuery): List<Pair<QueryControl, List<String>>> {
    if (controls == null) return emptyList()
    val catalog = controls.selectedCatalog(query)
    val fields = if (catalog == null) controls.controls
    else controls.controls.filter { it.id in listOf("type", "catalog") } + catalog.controls.filter { it.id !in listOf("type", "catalog") }
    return fields.filter { it.id !in listOf("type", "catalog") && it.maximum_selections != 0L }
        .map { control -> control to query.selections(control.id).filter(String::isNotBlank) }
        .filter { it.second.isNotEmpty() }
}

/** "Clear all filters" keeps the chosen catalog and drops every facet (`M3BrowseEmpty`). */
private fun clearedFilters(query: BrowseQuery): BrowseQuery =
    query.copy(skip = 0, genre = null, search = null, extras = emptyMap(), values = emptyList())

/** `M3Browse` header: the catalog pill (`Popular · VIPTV Sources`) and the Filters trigger with its count. */
@Composable
private fun PhoneBrowseBar(catalog: DiscoveryCatalog?, count: Int, openCatalogs: () -> Unit, toggleFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f, fill = false).height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .12f)).clickable(enabled = catalog != null, onClick = openCatalogs)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(catalog?.name ?: "Catalog", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            catalog?.connection_name?.takeIf(String::isNotBlank)?.let { provider ->
                Text("· $provider", color = Palette.secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronDown, null, Modifier.size(16.dp))
        }
        Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .12f))
            .clickable(onClick = toggleFilters).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Lucide.SlidersHorizontal, null, Modifier.size(16.dp))
            Text("Filters", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (count > 0) Box(Modifier.height(20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(10.dp))
                .background(Palette.text).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                Text("$count", color = Palette.background, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Applied-filter strip: tapping a chip drops that one value without opening Filters. */
@Composable
private fun PhoneFilterChips(facets: List<Pair<QueryControl, List<String>>>, modifier: Modifier,
    remove: (QueryControl, String) -> Unit) {
    if (facets.isEmpty()) { Spacer(modifier); return }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        facets.forEach { (control, values) -> values.forEach { value ->
            val label = control.choices.find { it.value == value }?.label ?: value
            Row(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = .10f))
                .clickable { remove(control, value) }.padding(start = 11.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Icon(Lucide.X, "Remove $label", Modifier.size(14.dp))
            }
        } }
    }
}

/** `M3BrowseLoading`: the pill row before the discovery controls have arrived. */
@Composable
private fun BrowseHeaderSkeleton() {
    val shimmer = rememberTvLoadingShimmer(1f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(190.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).then(shimmer))
        Box(Modifier.width(110.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).then(shimmer))
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

/** `M3BrowseError`: Try again re-runs the same query; the catalogs sheet is the escape hatch. */
@Composable
private fun BrowseErrorState(catalog: DiscoveryCatalog?, error: String, modifier: Modifier,
    retry: () -> Unit, chooseCatalog: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 90.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.CloudOff, null, Modifier.size(30.dp))
        Text("Couldn’t load ${catalog?.name ?: "this catalog"}", fontSize = 22.sp, lineHeight = 25.sp,
            letterSpacing = (-0.44).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("${catalog?.connection_name?.takeIf(String::isNotBlank) ?: "Your source"} didn’t answer in time. Your filters are kept.",
            color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        BoardAction("Try again", primary = true, run = retry, icon = Lucide.RotateCw)
        BoardAction("Choose another catalog", primary = false, run = chooseCatalog)
        Text(error, color = Palette.tertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    }
}

/** Board button geometry: 44dp tall, 12dp radius, 18dp side padding (`M3BrowseEmpty`/`M3BrowseError`). */
@Composable
private fun BoardAction(label: String, primary: Boolean, run: () -> Unit, icon: ImageVector? = null) {
    Row(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (primary) Palette.text else Color.White.copy(alpha = .12f)).clickable(onClick = run)
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = if (primary) Palette.background else Palette.text) }
        Text(label, color = if (primary) Palette.background else Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** `M3BrowseCatalogs`: the 560dp catalog sheet, grouped by source/add-on. */
@Composable
private fun PhoneCatalogsSheet(controls: DiscoveryControls, query: BrowseQuery,
    dismiss: () -> Unit, select: (DiscoveryCatalog) -> Unit) {
    val peers = controls.catalogs.filter { it.type == query.type }
    val groups = peers.groupBy { it.connection_name?.takeIf(String::isNotBlank) ?: "VIPTV Sources" }
    val selected = controls.selectedCatalog(query)?.selectionKey()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(560.dp)
                .shadow(24.dp, shape).clip(shape).background(Color(0xFF1A1A1D))) {
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp).align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = .3f)))
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Catalog", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))
                        .clickable(onClick = dismiss), contentAlignment = Alignment.Center) {
                        Icon(Lucide.X, "Close", Modifier.size(16.dp))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    groups.forEach { (group, entries) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(group, Modifier.padding(horizontal = 4.dp), color = Palette.secondary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = .06f))) {
                                entries.forEachIndexed { index, entry ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp)
                                        .clickable(interactionSource = remember { MutableInteractionSource() },
                                            indication = null) { select(entry) }
                                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.displayName(peers), Modifier.weight(1f), fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (selected == entry.selectionKey())
                                            Icon(Lucide.Check, "Selected", Modifier.size(18.dp))
                                    }
                                    if (index != entries.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp)
                                        .background(Color.White.copy(alpha = .08f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
