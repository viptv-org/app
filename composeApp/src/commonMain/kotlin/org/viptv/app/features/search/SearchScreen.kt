package org.viptv.app.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection

@Composable
fun SearchScreen(results: List<Card>, addons: List<IntegrationConnection>, busy: Boolean, error: String?, query: (String) -> Unit, open: (Card) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(text, { text = it; query(it) }, Modifier.fillMaxWidth(), placeholder = { Text("Search movies, series and channels") }, singleLine = true)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        error?.let { Text(it, color = Palette.danger) }
        if (!busy && text.isNotBlank() && results.isEmpty() && error == null) Text("No matches. Try another search.", color = Palette.secondary)
        val groups = results.groupBy { it.provider_id }
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
            LazyVerticalGrid(GridCells.Adaptive(240.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(results, key = { it.provider_id + ":" + it.identity.id }) { MediaCard(it, portrait = true, open = open) }
            }
        }
    }
}
