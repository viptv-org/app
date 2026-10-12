package org.viptv.app.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection

@Composable
fun SearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean, error: String?, desktop: Boolean, query: (String) -> Unit, open: (Card) -> Unit, television: Boolean = false) {
    if (television) {
        TvSearchScreen(term, results, addons, busy, error, query, open)
        return
    }
    var filter by remember { mutableStateOf("All") }
    val unique = results.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
    val visible = when (filter) {
        "Movies" -> unique.filter { it.identity.kind == "movie" }
        "Series" -> unique.filter { it.identity.kind == "series" }
        "Channels" -> unique.filter { it.identity.kind in listOf("channel", "tv") }
        else -> unique
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val phone = maxWidth < 640.dp
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)) {
        if (!phone) Text(if (desktop && term.isNotBlank()) "Results for “$term”" else "Search",
            style = if (desktop) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        if (!desktop) SearchField(term, query)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Movies", "Series", "Channels").forEach { tab ->
                Text(tab, Modifier.background(if (filter == tab) Palette.text else Color(0xFF202022), RoundedCornerShape(18.dp))
                    .clickable { filter = tab }.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (filter == tab) Palette.background else Palette.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        error?.let { Text(it, color = Palette.danger) }
        if (!busy && term.isNotBlank() && visible.isEmpty() && error == null) Text("No matches. Try another search.", color = Palette.secondary)
        if (phone && term.isNotBlank() && visible.isNotEmpty()) Text("Top results", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val groups = visible.groupBy { it.provider_id }
        if (groups.size > 1) {
            val labels=addons.associate { it.id to it.name }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                groups.forEach { (provider, cards) ->
                    item(key = "group:$provider") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(labels[provider] ?: "Results", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                rowItems(cards, key = { it.identity.kind + ":" + it.identity.id }) { MediaCard(it, width = 160, portrait = true, open = open) }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(if (phone) GridCells.Fixed(3) else GridCells.Adaptive(172.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 24.dp)) {
                items(visible, key = { it.provider_id + ":" + it.identity.kind + ":" + it.identity.id }) { MediaCard(it, width = if (phone) 112 else 172, portrait = true, showLabels = !phone, open = open) }
            }
        }
    }
}
}
