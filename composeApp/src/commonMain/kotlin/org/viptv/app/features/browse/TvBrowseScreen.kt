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
import androidx.compose.ui.input.key.*
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
    genres: List<String> = emptyList(), membership: Map<String, Set<String>> = emptyMap(), initialCard: Int = 0, filters: (@Composable (FocusRequester) -> Unit)? = null, more: (() -> Unit)? = null, error: String? = null) {
    var genre by remember { mutableStateOf<String?>(null) }
    val activeGenre = genre?.takeIf { it in genres }
    val selected = cards.filter { activeGenre == null || activeGenre in membership[it.identity.id].orEmpty() }
        .distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
    val targetIndex = (if (activeGenre == null) initialCard else 0).coerceIn(0, selected.lastIndex.coerceAtLeast(0))
    val filterFocus = remember { FocusRequester() }; val first = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    var enterResults by remember { mutableStateOf(false) }
    // A refreshed provider may remove a genre. Always retain an attached filter target.
    LaunchedEffect(activeGenre) { if (genre != activeGenre) genre = activeGenre }
    LaunchedEffect(activeGenre, enterResults, selected, targetIndex) {
        if (enterResults) {
            if (selected.isNotEmpty()) {
                grid.scrollToItem(targetIndex)
                withFrameNanos { }
                first.requestFocus()
            } else filterFocus.requestFocus()
            enterResults = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.focusProperties { onEnter = { filterFocus.requestFocus() } }.focusGroup()
            .padding(start = 96.dp * scale, top = 8.dp * scale)) {
            if (filters != null) filters(filterFocus) else TvFocusScroll {
                LazyRow(modifier = Modifier.focusProperties { onEnter = { filterFocus.requestFocus() } }
                    .onPreviewKeyEvent {
                        if (it.key == Key.DirectionDown && selected.isNotEmpty()) {
                            if (it.type == KeyEventType.KeyDown) enterResults = true
                            true
                        } else false
                    },
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale), contentPadding = PaddingValues(end = 96.dp * scale)) {
                    items(listOf<String?>(null) + genres.distinct()) { option ->
                        TvAction(Modifier.height(56.dp * scale), scale, requester = if (option == activeGenre) filterFocus else null,
                            activate = { genre = option; enterResults = true }, cornerRadius = 28.dp * scale, primary = option == activeGenre,
                            contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                            Text(option ?: "All genres", color = it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp * scale))
            error?.let { Text(it, color = Palette.danger) }
            if (busy && selected.isEmpty()) TvBrowseLoading(scale)
            if (!busy && selected.isEmpty()) Text("No titles are available in this catalog.", color = Palette.secondary, fontSize = (24 * scale).sp)
            TvFocusScroll {
                LazyVerticalGrid(GridCells.Fixed(7), Modifier.width(1720.dp * scale).weight(1f), state = grid, horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(36.dp * scale), contentPadding = PaddingValues(bottom = 96.dp * scale)) {
                    itemsIndexed(selected, key = { _, card -> card.provider_id + ":" + card.identity.kind + ":" + card.identity.id }) { index, card ->
                        TvCatalogCard(card, scale, true, Modifier.then(if (index == targetIndex) Modifier.focusRequester(first) else Modifier)
                            .focusProperties { if (index < 7) up = filterFocus }, {}, open)
                    }
                    more?.let { next -> item(span = { GridItemSpan(maxLineSpan) }) { TextButton(next, enabled = !busy) { Text("Load more") } } }
                }
            }
        }
    }
}
