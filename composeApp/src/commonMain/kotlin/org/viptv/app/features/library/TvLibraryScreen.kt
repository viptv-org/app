package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvLibraryScreen(cards: List<Card>, available: Boolean, open: (Card) -> Unit, home: () -> Unit,
    lastWatched: Map<String, Long> = emptyMap()) {
    var filter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(if (lastWatched.isEmpty()) "Date added" else "Last watched") }
    val first = remember { FocusRequester() }; val selectedFilter = remember { FocusRequester() }
    val filtered = cards.filter { filter == "All" || it.identity.kind == if (filter == "Movies") "movie" else "series" }
    val visible = when (sort) {
        "A–Z" -> filtered.sortedBy { it.title.lowercase() }
        "Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
        "Last watched" -> filtered.sortedByDescending { lastWatched[it.identity.id] ?: Long.MIN_VALUE }
        else -> filtered
    }.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.fillMaxSize().focusProperties { onEnter = { selectedFilter.requestFocus() } }.focusGroup()
            .padding(horizontal = 96.dp * scale).padding(top = 8.dp * scale)) {
            TvLibraryControls(filter, { filter = it }, sort, { sort = it }, scale, selectedFilter,
                if (visible.isNotEmpty()) first else null, lastWatched.isNotEmpty())
            Spacer(Modifier.height(32.dp * scale))
            if (visible.isEmpty()) Column(Modifier.padding(top = 120.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Text(if (available) "Your list is empty." else "My List is not available yet.", fontSize = (32 * scale).sp)
                Text(if (available) "Use + on a movie or series to save it here." else "Saving titles is not available for this connection yet.",
                    fontSize = (24 * scale).sp, color = Palette.secondary)
                TvAction(Modifier.height(64.dp * scale), scale, primary = true, activate = home,
                    contentPadding = PaddingValues(horizontal = 28.dp * scale)) { Text("Browse Home", color = it, fontSize = (24 * scale).sp) }
            } else TvFocusScroll {
                LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp * scale), verticalArrangement = Arrangement.spacedBy(22.dp * scale),
                    contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                    itemsIndexed(visible, key = { _, card -> card.provider_id + ":" + card.identity.kind + ":" + card.identity.id }) { index, card ->
                        TvCatalogCard(card, scale, true, Modifier.then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { if (index < 7) up = selectedFilter }, {}, open, focusedCaptions = true)
                    }
                }
            }
        }
    }
}
