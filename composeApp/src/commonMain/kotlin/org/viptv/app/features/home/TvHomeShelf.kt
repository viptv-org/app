@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyRow as LazyRow
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.continuewatching.continueWatchingActivation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import org.viptv.app.components.ServerMessages
import org.viptv.app.components.TvAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*

private const val HOME_ROW_TRAILING = 6

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
    focus: (Card) -> Unit, open: (Card) -> Unit, modifier: Modifier = Modifier,
    firstPlaced: () -> Unit = {}, more: ((String) -> Unit)? = null,
    appending: Boolean = false, appendError: String? = null, resume: FocusRequester? = null,
    rowAlpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null,
    onHeightChange: ((Int) -> Unit)? = null, play: (Card) -> Unit = open, state: LazyListState = rememberLazyListState(),
    continueMore: org.viptv.app.state.ContinueWatchingMore? = null, seeAll: () -> Unit = {}) {
    if (row.state == RowState.EMPTY || row.state == RowState.FAILED) return
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val portrait = row.id != "continue"
    val activate = if (row.id == CONTINUE_ROW) continueWatchingActivation(play, open) else open
    val arrival = rememberTvContentArrival(row.state == RowState.READY)
    val cards = remember(row.cards) { row.cards.distinctBy(::cardLookupKey) }
    val endCard = continueMore?.takeIf { row.id == CONTINUE_ROW }
    val trailing = if (appendError == null && more != null && (row.next_cursor != null || appending)) HOME_ROW_TRAILING else 0

    var lastFocusedIndex by rememberSaveable(row.id) { mutableIntStateOf(0) }
    var lastFocusedKey by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    val resumeIndex = cards.indexOfFirst { cardLookupKey(it) == lastFocusedKey }.takeIf { it >= 0 }
        ?: lastFocusedIndex.coerceIn(0, cards.lastIndex.coerceAtLeast(0))
    val resumeSeeAll = endCard != null && lastFocusedKey == SEE_ALL_KEY
    var focusedKey by remember(row.id) { mutableStateOf<Any?>(null) }
    FollowNewFirstCard(state, cards.firstOrNull()?.let(::cardLookupKey), focusedKey != null)
    val latestCards = rememberUpdatedState(cards)
    val latestEndCard = rememberUpdatedState(endCard)
    val recover = remember { FocusRequester() }
    LaunchedEffect(cards, endCard != null) {
        val lost = focusedKey ?: return@LaunchedEffect
        if (cards.isEmpty() || cards.any { cardLookupKey(it) == lost } || lost == SEE_ALL_KEY && endCard != null) return@LaunchedEffect
        focusedKey = null
        repeat(30) { withFrameNanos { }; if (runCatching { recover.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    val loadPage = rememberUpdatedState(more)
    LaunchedEffect(row.id, row.next_cursor, appending, appendError, cards.size) {
        val request = loadPage.value ?: return@LaunchedEffect
        if (row.next_cursor == null || appending || appendError != null || cards.isEmpty()) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .first { index -> index > cards.lastIndex }
        request(row.id)
    }
    Column(modifier
        .then(if (rowAlpha != null) Modifier.graphicsLayer { alpha = rowAlpha.value } else Modifier)
        .then(if (onHeightChange != null) Modifier.onSizeChanged { onHeightChange(it.height) } else Modifier)
        .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        Text(row.title, Modifier.padding(start = 96.dp * scale).height(40.dp * scale), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)

        LazyRow(state = state, modifier = arrival.focusRestorer(if (lastFocusedKey != null) recover else FocusRequester.Default).focusGroup().onFocusChanged {
                if (!it.hasFocus && (latestCards.value.any { card -> cardLookupKey(card) == focusedKey } ||
                        focusedKey == SEE_ALL_KEY && latestEndCard.value != null)) focusedKey = null
            }, contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy((if (portrait) 16 else 20).dp * scale)) {
            when (row.state) {
                RowState.READY -> {
                    itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> if (portrait) "portrait" else "wide" }) { index, card ->
                        val entry = Modifier.then(if (index == 0 && first != null) Modifier.focusRequester(first).onGloballyPositioned { firstPlaced() } else Modifier)
                            .then(if (resume != null && index == resumeIndex && !resumeSeeAll) Modifier.focusRequester(resume) else Modifier)
                            .then(if (index == resumeIndex && !resumeSeeAll) Modifier.focusRequester(recover) else Modifier)
                            // The leading card stops at logical start: geometric search would otherwise wander to the profile avatar.
                            .then(if (index == 0) Modifier.focusProperties { if (rtl) right = FocusRequester.Cancel else left = FocusRequester.Cancel } else Modifier)
                        TvCatalogCard(card, scale, portrait, entry,
                            { lastFocusedIndex = index; lastFocusedKey = cardLookupKey(it); focusedKey = lastFocusedKey; focus(it) }, activate, showFocus, focusedCaptions = portrait,
                            onUp = up, artworkWidth = (if (portrait) 255.2f else 422.4f).dp * scale)
                    }
                    if (endCard != null) item(key = SEE_ALL_KEY, contentType = "see-all") {
                        TvSeeAllCard(endCard, scale, seeAll, Modifier
                            .then(if (resume != null && resumeSeeAll) Modifier.focusRequester(resume) else Modifier)
                            .then(if (resumeSeeAll) Modifier.focusRequester(recover) else Modifier),
                            focused = { lastFocusedIndex = cards.size; lastFocusedKey = SEE_ALL_KEY; focusedKey = SEE_ALL_KEY },
                            onUp = up)
                    }
                    if (appendError != null) item(key = "row-append", contentType = "failed") {
                        TvRowMessageCard(scale, "Couldn’t load more titles", appendError, { loadPage.value?.invoke(row.id) })
                    } else items(trailing, key = { "append-$it" }, contentType = { "skeleton" }) { TvHomeSkeletonCard(scale, portrait) }
                }
                RowState.PENDING -> items(6, contentType = { "skeleton" }) { TvHomeSkeletonCard(scale, portrait) }
                RowState.EMPTY, RowState.FAILED -> Unit
            }
        }

    }
}