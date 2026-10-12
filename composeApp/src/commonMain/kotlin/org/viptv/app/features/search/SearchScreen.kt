package org.viptv.app.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
fun SearchScreen(term: String, results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean, error: String?, desktop: Boolean, query: (String) -> Unit, open: (Card) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    val visible = when (filter) {
        "Movies" -> results.filter { it.identity.kind == "movie" }
        "Series" -> results.filter { it.identity.kind == "series" }
        "Channels" -> results.filter { it.identity.kind in listOf("channel", "tv") }
        "People" -> emptyList()
        else -> results
    }
    Column(Modifier.fillMaxSize().padding(if (desktop) 32.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (desktop) 18.dp else 24.dp)) {
        Text(if (desktop && term.isNotBlank()) "Results for “$term”" else "Search",
            style = if (desktop) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        if (!desktop) OutlinedTextField(term, query, Modifier.fillMaxWidth(), placeholder = { Text("Search movies, series and channels") }, singleLine = true)
        if (desktop) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Movies", "Series", "Channels", "People").forEach { tab ->
                Text(tab, Modifier.background(if (filter == tab) Palette.text else Color(0xFF202022), RoundedCornerShape(18.dp))
                    .clickable { filter = tab }.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = if (filter == tab) Palette.background else Palette.text,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        error?.let { Text(it, color = Palette.danger) }
        if (!busy && term.isNotBlank() && visible.isEmpty() && error == null) Text("No matches. Try another search.", color = Palette.secondary)
        val groups = visible.groupBy { it.provider_id }
        if (groups.size > 1) {
            val labels=addons.associate { it.id to it.name }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                groups.forEach { (provider, cards) ->
                    item(key = "group:$provider") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(labels[provider] ?: "Results", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                rowItems(cards, key = { it.identity.id }) { MediaCard(it, width = 160, portrait = true, open = open) }
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(if (desktop) 172.dp else 240.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(visible, key = { it.provider_id + ":" + it.identity.id }) { MediaCard(it, width = if (desktop) 172 else 240, portrait = true, open = open) }
            }
        }
    }
}
