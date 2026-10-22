@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.features.home

import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRestorer
import org.viptv.app.components.ServerMessages
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberTvShelfScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
internal fun TvHomeShelf(row: HomeRow, scale: Float, first: FocusRequester?, showFocus: Boolean = true, up: (() -> Unit)?,
    focus: (Card) -> Unit, open: (Card) -> Unit, retry: () -> Unit, modifier: Modifier = Modifier,
    viewport: Dp, firstPlaced: () -> Unit = {}, more: ((String) -> Unit)? = null,
    appending: Boolean = false, appendError: String? = null, resume: FocusRequester? = null,
    rowAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null,
    onHeightChange: ((Int) -> Unit)? = null, play: (Card) -> Unit = open,
    // The screen stores the last selection per row: rows live in a LazyColumn and
    // are disposed when scrolled away, which would forget the card otherwise.
    resumeIndex: Int = 0, onResumeIndex: (Int) -> Unit = {}) {
    val portrait = row.id != "continue"
    // OK on a Continue Watching card goes straight to that episode's sources.
    val activate = if (row.id == CONTINUE_ROW) play else open
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val arrival = rememberTvContentArrival(row.state == RowState.READY)
    val cards = remember(row.cards) { row.cards.distinctBy(::cardLookupKey) }
    // Dictionary §A2: with next_cursor, skeletons append as focus or scroll nears
    // the end. There is never a Load more button.
    val trailing = if (appendError == null && more != null && (row.next_cursor != null || appending)) HOME_ROW_TRAILING else 0
    val scroll = rememberTvShelfScroll(state, (if (portrait) 248 else 404).dp * scale, viewport, cards.size + trailing, 96.dp * scale)
    // Remember the card the viewer last focused in this shelf. Coming back Down
    // from the billboard must resume *there*: focusing card 0 restored the scroll
    // to the start and flashed the previously focused card before snapping away.
    var lastFocusedIndex by remember(row.id) { mutableIntStateOf(resumeIndex.coerceAtLeast(0)) }
    // A background refresh can remove the focused card (finished on another
    // device). Focus moves to the surviving card at the same position instead
    // of falling out of the shelf. Only a shelf that held focus does this.
    var focusedKey by remember(row.id) { mutableStateOf<Any?>(null) }
    val latestCards = rememberUpdatedState(cards)
    // The row container holds platform focus; a removed selected card clamps the
    // selection to the survivor, so there is no focus to recover.
    val recover = remember { FocusRequester() }
    val loadPage = rememberUpdatedState(more)
    LaunchedEffect(row.id, row.next_cursor, appending, appendError, cards.size) {
        val request = loadPage.value ?: return@LaunchedEffect
        if (row.next_cursor == null || appending || appendError != null || cards.isEmpty()) return@LaunchedEffect
        // One page per cursor: a landed page changes cards.size/next_cursor and re-arms this.
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .first { index -> index > cards.lastIndex }
        request(row.id)
    }
    // The screen passes the row fade/height hooks as stable instances so a
    // focus move does not hand every visible shelf a fresh modifier chain.
    Column(modifier
        .then(if (rowAlpha != null) Modifier.graphicsLayer { alpha = rowAlpha.value } else Modifier)
        .then(if (onHeightChange != null) Modifier.onSizeChanged { onHeightChange(it.height) } else Modifier)
        .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(row.title, Modifier.padding(start = 96.dp * scale).height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll(manual = true) {
            // One focusable per shelf: the row owns the D-pad, the highlight pins at
            // the leading slot and the content scrolls behind it (the leanback/Roku
            // model). Selection is virtual — a removed card clamps the index instead
            // of dropping focus out of the shelf.
            val gutterPx = with(LocalDensity.current) { (96.dp * scale).toPx() }
            var rowHasFocus by remember(row.id) { mutableStateOf(false) }
            var selected by remember(row.id) { mutableIntStateOf(lastFocusedIndex) }
            // Selection tracks the card's identity, so an insertion before it keeps
            // the same card selected; a removal clamps to the card now in its place.
            var selectedKey by remember(row.id) { mutableStateOf<String?>(cards.getOrNull(selected)?.let(::cardLookupKey)) }
            fun select(index: Int) {
                if (index !in cards.indices) return
                selected = index; lastFocusedIndex = index; onResumeIndex(index); selectedKey = cardLookupKey(cards[index])
                focusedKey = selectedKey
                // Pinned highlight: the selected card parks at the leading slot and
                // the content scrolls behind it (the leanback/Roku model). Long
                // repeats jump close instantly so a held button never churns an
                // animation; the final delta is measured, not estimated by index.
                scope.launch {
                    val info = state.layoutInfo
                    if (info.visibleItemsInfo.none { it.index == index }) state.scrollToItem(index)
                    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
                    // The leanback/Roku window: the highlight walks the first slots
                    // naturally; from the third card on it pins at the leading slot
                    // and the content scrolls behind it. The list clamps the tail.
                    // LazyList item offsets are content-relative, so offset == the
                    // distance from the pinned slot, and item 0's offset == -scroll.
                    val delta = if (index < 2) {
                        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.toFloat() ?: 0f
                    } else item.offset.toFloat()
                    if (kotlin.math.abs(delta) > 1f) state.animateScrollBy(delta,
                        androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                }
                focus(cards[index])
            }
            LaunchedEffect(cards) {
                if (cards.isEmpty()) return@LaunchedEffect
                val tracked = cards.indexOfFirst { cardLookupKey(it) == selectedKey }
                val target = if (tracked >= 0) tracked else selected.coerceIn(0, cards.lastIndex)
                if (target != selected || cardLookupKey(cards[target]) != selectedKey) {
                    selected = target; lastFocusedIndex = target; selectedKey = cardLookupKey(cards[target])
                    focusedKey = selectedKey
                    if (rowHasFocus) focus(cards[target])
                }
            }
            val container = first ?: resume ?: recover
            // One accessibility node per shelf row: the container announces the row
            // and the selected card; the cards are static content under it.
            val rowDescription = { cards.getOrNull(selected)?.let { "${row.title}, item ${selected + 1} of ${cards.size}: ${it.title}" } ?: row.title }
            LazyRow(state = state, modifier = arrival.focusRestorer()
                .semantics { contentDescription = rowDescription() }
                .then(if (container != null) Modifier.focusRequester(container) else Modifier)
                .onGloballyPositioned { if (first != null) firstPlaced() }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus && !rowHasFocus) {
                        val target = selected.coerceIn(0, cards.lastIndex.coerceAtLeast(0))
                        selected = target; lastFocusedIndex = target; onResumeIndex(target); selectedKey = cards.getOrNull(target)?.let(::cardLookupKey)
                        cards.getOrNull(target)?.let { focusedKey = selectedKey; focus(it) }
                        scope.launch { state.scrollToItem(target) }
                    }
                    // Focus moving elsewhere forgets the card; a removed card keeps it for recovery.
                    if (!focusState.hasFocus && latestCards.value.any { card -> cardLookupKey(card) == focusedKey }) focusedKey = null
                    rowHasFocus = focusState.hasFocus
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> if (selected < cards.lastIndex + trailing) {
                            select(selected + 1)
                            true
                        } else true
                        Key.DirectionLeft -> if (selected > 0) {
                            select(selected - 1)
                            true
                        } else false
                        Key.DirectionUp -> if (up != null) { up(); true } else false
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { cards.getOrNull(selected)?.let(activate); true }
                        else -> false
                    }
                }
                .focusable()
                .focusGroup(),
                contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy((if (portrait) 16 else 20).dp * scale)) {
                when (row.state) {
                    RowState.READY -> {
                        itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> if (portrait) "portrait" else "wide" }) { index, card ->
                            TvCatalogCard(card, scale, portrait, Modifier.focusProperties { canFocus = false },
                                selected = rowHasFocus && index == selected,
                                focused = {}, open = activate, showFocus = showFocus, focusedCaptions = portrait)
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