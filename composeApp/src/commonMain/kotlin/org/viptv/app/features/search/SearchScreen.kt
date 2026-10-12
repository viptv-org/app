package org.viptv.app.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
fun SearchScreen(results: List<Card>, busy: Boolean, error: String?, query: (String) -> Unit, open: (Card) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(text, { text = it; query(it) }, Modifier.fillMaxWidth(), placeholder = { Text("Search movies, series and channels") }, singleLine = true)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        error?.let { Text(it, color = Palette.danger) }
        if (!busy && text.isNotBlank() && results.isEmpty() && error == null) Text("No matches. Try another search.", color = Palette.secondary)
        LazyVerticalGrid(GridCells.Adaptive(240.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(results, key = { it.provider_id + ":" + it.identity.id }) { MediaCard(it, portrait = true, open = open) }
        }
    }
}
