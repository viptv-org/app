package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import org.viptv.app.components.rememberTvShelfScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
internal fun TvHomeShelf(row: HomeRow, scale: Float, first: FocusRequester?, showFocus: Boolean = true, up: () -> Unit,
    focus: (Card) -> Unit, open: (Card) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier) {
    val portrait = row.id != "continue"
    val state = rememberLazyListState()
    val arrival = rememberTvContentArrival(row.state == RowState.READY)
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val scroll = rememberTvShelfScroll(state, (if (portrait) 248 else 404).dp * scale, maxWidth, row.cards.size, 96.dp * scale)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(row.title, Modifier.padding(start = 96.dp * scale).height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll(manual = true) {
            LazyRow(state = state, modifier = arrival.focusRestorer().focusGroup(), contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy((if (portrait) 16 else 20).dp * scale)) {
                when (row.state) {
                    RowState.READY -> itemsIndexed(row.cards.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }, key = { _, card -> card.provider_id + ":" + card.identity.kind + ":" + card.identity.id }) { index, card ->
                        TvCatalogCard(card, scale, portrait, Modifier.then(if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                            .onPreviewKeyEvent { if (first != null && it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && it.key == androidx.compose.ui.input.key.Key.DirectionUp) { up(); true } else false }, { scroll(index); focus(it) }, open, showFocus, focusedCaptions = portrait)
                    }
                    RowState.PENDING -> items(5) { TvHomeSkeletonCard(scale, portrait) }
                    RowState.EMPTY -> item { Text("Nothing here yet.", color = Palette.secondary, fontSize = (24 * scale).sp) }
                    RowState.FAILED -> item { TextButton(retry) { Text(row.message ?: "Try again", fontSize = (24 * scale).sp) } }
                }
            }
        }
    }
}
}
