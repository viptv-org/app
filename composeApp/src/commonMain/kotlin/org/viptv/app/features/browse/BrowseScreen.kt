package org.viptv.app.features.browse

import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.features.home.MediaCard
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.state.Screen
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeSnapshot

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
        val compact = maxWidth < 640.dp && !television
        val gutter = if (television) 70.dp else if (compact) 16.dp else 40.dp
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = if (television) 32.dp else 24.dp)) {
            Text(screen.name, fontSize = if (television) 36.sp else 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            if (compact) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (controls != null) DiscoveryFilters(controls, query, screen == Screen.Discover, filterChanged, multipleChanged = multipleChanged)
                    else Text("All genres", Modifier.background(Palette.text, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp), color = Palette.background,
                        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                    SortMenu(sort, menu, { sort = it; menu = false }, { menu = !menu })
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (controls != null) DiscoveryFilters(controls, query, screen == Screen.Discover, filterChanged, multipleChanged = multipleChanged)
                else Text("All genres", Modifier.background(Palette.text, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp), color = Palette.background,
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                SortMenu(sort, menu, { sort = it; menu = false }, { menu = !menu })
            }
            Spacer(Modifier.height(18.dp))
            error?.let { Text(it, color = Palette.danger) }
            if (busy || (loadedCards == null && home == null)) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            else if (cards.isEmpty()) Text("No ${if (screen == Screen.Series) "series" else if (screen == Screen.Movies) "movies" else if (screen == Screen.Anime) "anime" else "titles"} are available for these filters.", color = Palette.secondary)
            LazyVerticalGrid(GridCells.Adaptive(if (television) 220.dp else if (compact) 155.dp else 172.dp),
                state = grid, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = if (compact) 88.dp else 24.dp)) {
                items(cards, key = ::cardLookupKey) {
                    MediaCard(it, width = if (television) 220 else if (compact) 155 else 172, portrait = true, showLabels = compact || television, open = open, showFocusOutline = television || !compact || !browser)
                }
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
