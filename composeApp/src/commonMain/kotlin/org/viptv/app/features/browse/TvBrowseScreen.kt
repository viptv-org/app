package org.viptv.app.features.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvBrowseScreen(cards: List<Card>, busy: Boolean, open: (Card) -> Unit,
    genres: List<String> = emptyList(), membership: Map<String, Set<String>> = emptyMap(), initialCard: Int = 0) {
    var genre by remember { mutableStateOf<String?>(null) }
    val selected = cards.filter { genre == null || genre in membership[it.identity.id].orEmpty() }
        .distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
    val filterFocus = remember { FocusRequester() }; val first = remember { FocusRequester() }
    LaunchedEffect(genre) {
        if (genre != null) {
            if (selected.isNotEmpty()) first.requestFocus() else filterFocus.requestFocus()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.focusProperties { onEnter = { filterFocus.requestFocus() } }.focusGroup()
            .padding(start = 96.dp * scale, top = 8.dp * scale)) {
            TvFocusScroll {
                LazyRow(modifier = Modifier.focusProperties { onEnter = { filterFocus.requestFocus() } },
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale), contentPadding = PaddingValues(end = 96.dp * scale)) {
                    items(listOf<String?>(null) + genres) { option ->
                        TvAction(Modifier.height(56.dp * scale).focusProperties { if (selected.isNotEmpty()) down = first }, scale, requester = if (option == genre) filterFocus else null,
                            activate = { genre = option }, cornerRadius = 28.dp * scale, primary = option == genre,
                            contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                            Text(option ?: "All genres", color = it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp * scale))
            if (busy && selected.isEmpty()) TvBrowseLoading(scale)
            if (!busy && selected.isEmpty()) Text("No titles are available in this catalog.", color = Palette.secondary, fontSize = (24 * scale).sp)
            TvFocusScroll {
                LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(36.dp * scale), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                    itemsIndexed(selected, key = { _, card -> card.provider_id + ":" + card.identity.kind + ":" + card.identity.id }) { index, card ->
                        TvCatalogCard(card, scale, true, Modifier.then(if (index == (if (genre == null) initialCard else 0).coerceIn(0, selected.lastIndex)) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { if (index < 7) up = filterFocus }, {}, open)
                    }
                }
            }
        }
    }
}
