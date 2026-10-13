package org.viptv.app.features.browse

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
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeSnapshot

@Composable
fun BrowseScreen(screen: Screen, home: HomeSnapshot?, television: Boolean, open: (Card) -> Unit, loadedCards: List<Card>? = null, controls: org.viptv.app.connection.DiscoveryControls? = null,
    query: org.viptv.app.connection.BrowseQuery = org.viptv.app.connection.BrowseQuery("movie"), busy: Boolean = false, error: String? = null,
    filterChanged: (String, String) -> Unit = { _, _ -> }, more: (() -> Unit)? = null) {
    var sort by remember(screen) { mutableStateOf("Popular") }
    var menu by remember { mutableStateOf(false) }
    val cards = remember(home, loadedCards, screen, sort) {
        val loaded = loadedCards ?: home?.rows.orEmpty().flatMap { it.cards }
            .distinctBy { it.identity.kind to it.identity.id }
            .filter { when (screen) {
                Screen.Movies -> it.identity.kind == "movie"
                Screen.Series -> it.identity.kind == "series"
                else -> true
            } }
        when (sort) { "A–Z" -> loaded.sortedBy { it.title.lowercase() }; "Z–A" -> loaded.sortedByDescending { it.title.lowercase() }; else -> loaded }
    }
    if (television) {
        TvBrowseScreen(cards, busy || (loadedCards == null && home == null), open, more = more, error = error, filters = controls?.let { { first -> DiscoveryFilters(it, query, screen == Screen.Discover, filterChanged, first) } })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp && !television
        val gutter = if (television) 70.dp else if (compact) 16.dp else 40.dp
        Column(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = if (television) 32.dp else 24.dp)) {
            Text(screen.name, fontSize = if (television) 36.sp else 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (controls != null) DiscoveryFilters(controls, query, screen == Screen.Discover, filterChanged)
                else Text("All genres", Modifier.background(Palette.text, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp), color = Palette.background,
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    Row(Modifier.background(Color(0xFF222224), RoundedCornerShape(8.dp))
                        .clickable { menu = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.SlidersHorizontal, null, Modifier.size(16.dp))
                        Text(sort, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Lucide.ChevronDown, null, Modifier.size(16.dp))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("Popular", "A–Z", "Z–A").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; menu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            error?.let { Text(it, color = Palette.danger) }
            if (busy || (loadedCards == null && home == null)) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            else if (cards.isEmpty()) Text("No ${if (screen == Screen.Series) "series" else if (screen == Screen.Movies) "movies" else "titles"} are available for these filters.", color = Palette.secondary)
            LazyVerticalGrid(GridCells.Adaptive(if (television) 220.dp else if (compact) 155.dp else 172.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = if (compact) 88.dp else 24.dp)) {
                items(cards, key = { it.provider_id + ":" + it.identity.kind + ":" + it.identity.id }) {
                    MediaCard(it, width = if (television) 220 else if (compact) 155 else 172, portrait = true, showLabels = compact || television, open = open)
                }
                more?.let { next -> item(span = { GridItemSpan(maxLineSpan) }) { TextButton(next, enabled = !busy) { Text("Load more") } } }
            }
        }
    }
}
