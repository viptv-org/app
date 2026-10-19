package org.viptv.app.features.home

import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberTvShelfScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

@Composable
internal fun TvHomeShelf(row: HomeRow, scale: Float, first: FocusRequester?, showFocus: Boolean = true, up: () -> Unit,
    focus: (Card) -> Unit, open: (Card) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier,
    viewport: Dp, firstPlaced: () -> Unit = {}) {
    val portrait = row.id != "continue"
    // Keep one card composed and measured on each side of the viewport: a card
    // that scrolls in is already composed, so the frame that reveals it does not
    // pay composition and text layout.
    val state = rememberLazyListState()
    val arrival = rememberTvContentArrival(row.state == RowState.READY)
    val cards = remember(row.cards) { row.cards.distinctBy(::cardLookupKey) }
    val scroll = rememberTvShelfScroll(state, (if (portrait) 248 else 404).dp * scale, viewport, cards.size, 96.dp * scale)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(row.title, Modifier.padding(start = 96.dp * scale).height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll(manual = true) {
            LazyRow(state = state, modifier = arrival.focusRestorer().focusGroup(), contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy((if (portrait) 16 else 20).dp * scale)) {
                when (row.state) {
                    RowState.READY -> itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> if (portrait) "portrait" else "wide" }) { index, card ->
                        TvCatalogCard(card, scale, portrait, Modifier.then(if (index == 0 && first != null) Modifier.focusRequester(first).onGloballyPositioned { firstPlaced() } else Modifier),
                            { scroll(index); focus(it) }, open, showFocus, focusedCaptions = portrait,
                            onUp = if (index < 7) up else null)
                    }
                    // The board shows one full row of six skeletons while a row loads.
                    RowState.PENDING -> items(6, contentType = { "skeleton" }) { TvHomeSkeletonCard(scale, portrait) }
                    RowState.EMPTY -> item(contentType = "empty") { Text("Nothing here yet.", color = Palette.secondary, fontSize = (24 * scale).sp) }
                    RowState.FAILED -> item(contentType = "failed") {
                        // Board Tv3RowStates: a message card, not a bare button.
                        Column(Modifier.width(940.dp * scale).height(169.dp * scale)
                            .background(Palette.text.copy(alpha = .05f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp * scale))
                            .border(1.dp * scale, Palette.text.copy(alpha = .07f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp * scale))
                            .padding(horizontal = 28.dp * scale, vertical = 24.dp * scale),
                            verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                            Text("Couldn’t load this row", fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                            Text(row.message ?: "The provider didn’t answer in time.", color = Palette.secondary,
                                fontSize = (19 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            TvAction(Modifier.height(60.dp * scale).width(180.dp * scale), scale, primary = true,
                                activate = retry) { tint -> Text("Try again", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}