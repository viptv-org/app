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
import org.viptv.app.components.ServerMessages
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
import kotlinx.coroutines.flow.first
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

/** §A2 / Tv3RowStates: six skeletons append while a further page is still coming. */
private const val HOME_ROW_TRAILING = 6

/** Board Tv3RowStates message card (940×169 at 96/840): a failed row and a failed page share it. */
@Composable
private fun TvRowMessageCard(scale: Float, title: String, message: String?, retry: () -> Unit) {
    Column(Modifier.width(940.dp * scale).height(169.dp * scale)
        .background(Palette.text.copy(alpha = .05f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp * scale))
        .border(1.dp * scale, Palette.text.copy(alpha = .07f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp * scale))
        .padding(horizontal = 28.dp * scale, vertical = 24.dp * scale),
        verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        Text(title, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
        Text(ServerMessages.human(message, ServerMessages.ROW_FAILURE), color = Palette.secondary,
            fontSize = (19 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        TvAction(Modifier.height(60.dp * scale).width(180.dp * scale), scale, primary = true,
            activate = retry) { tint -> Text("Try again", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun TvHomeShelf(row: HomeRow, scale: Float, first: FocusRequester?, showFocus: Boolean = true, up: () -> Unit,
    focus: (Card) -> Unit, open: (Card) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier,
    viewport: Dp, firstPlaced: () -> Unit = {}, more: ((String) -> Unit)? = null,
    appending: Boolean = false, appendError: String? = null) {
    val portrait = row.id != "continue"
    // Keep one card composed and measured on each side of the viewport: a card
    // that scrolls in is already composed, so the frame that reveals it does not
    // pay composition and text layout.
    val state = rememberLazyListState()
    val arrival = rememberTvContentArrival(row.state == RowState.READY)
    val cards = remember(row.cards) { row.cards.distinctBy(::cardLookupKey) }
    // Dictionary §A2: with next_cursor, skeletons append as focus or scroll nears
    // the end. There is never a Load more button.
    val trailing = if (appendError == null && more != null && (row.next_cursor != null || appending)) HOME_ROW_TRAILING else 0
    val scroll = rememberTvShelfScroll(state, (if (portrait) 248 else 404).dp * scale, viewport, cards.size + trailing, 96.dp * scale)
    val loadPage = rememberUpdatedState(more)
    LaunchedEffect(row.id, row.next_cursor, appending, appendError, cards.size) {
        val request = loadPage.value ?: return@LaunchedEffect
        if (row.next_cursor == null || appending || appendError != null || cards.isEmpty()) return@LaunchedEffect
        // One page per cursor: a landed page changes cards.size/next_cursor and re-arms this.
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .first { index -> index > cards.lastIndex }
        request(row.id)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(row.title, Modifier.padding(start = 96.dp * scale).height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll(manual = true) {
            LazyRow(state = state, modifier = arrival.focusRestorer().focusGroup(), contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy((if (portrait) 16 else 20).dp * scale)) {
                when (row.state) {
                    RowState.READY -> {
                        itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> if (portrait) "portrait" else "wide" }) { index, card ->
                            TvCatalogCard(card, scale, portrait, Modifier.then(if (index == 0 && first != null) Modifier.focusRequester(first).onGloballyPositioned { firstPlaced() } else Modifier),
                                { scroll(index); focus(it) }, open, showFocus, focusedCaptions = portrait,
                                onUp = if (index < 7) up else null)
                        }
                        if (appendError != null) item(key = "row-append", contentType = "failed") {
                            TvRowMessageCard(scale, "Couldn’t load more titles", appendError, { loadPage.value?.invoke(row.id) })
                        } else items(trailing, key = { "append-$it" }, contentType = { "skeleton" }) { TvHomeSkeletonCard(scale, portrait) }
                    }
                    // The board shows one full row of six skeletons while a row loads.
                    RowState.PENDING -> items(6, contentType = { "skeleton" }) { TvHomeSkeletonCard(scale, portrait) }
                    RowState.EMPTY -> item(contentType = "empty") { Text("Nothing here yet.", color = Palette.secondary, fontSize = (24 * scale).sp) }
                    RowState.FAILED -> item(contentType = "failed") {
                        // Board Tv3RowStates: a message card, not a bare button.
                        TvRowMessageCard(scale, "Couldn’t load this row", row.message, retry)
                    }
                }
            }
        }
    }
}