package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyRow as LazyRow
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.continuewatching.continueWatchingActivation
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import kotlinx.coroutines.launch
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*
import org.viptv.app.components.pointerClickable
import org.viptv.app.state.RowSeeAll

internal const val CONTINUE_ROW = "continue"
internal const val MY_LIST_ROW = "favorites"

@Composable
internal fun HomeCatalogRow(row: HomeRow, compact: Boolean, television: Boolean, modifier: Modifier,
    focus: (Card) -> Unit, open: (Card) -> Unit, scale: Float = 1f, tablet: Boolean = false, browser: Boolean = false,
    play: (Card) -> Unit = open,
    gutter: Dp = 0.dp,
    seeAllFor: (HomeRow) -> RowSeeAll? = { null }, openSeeAll: (RowSeeAll) -> Unit = {},
    hover: (Card, Boolean) -> Unit = { _, _ -> }) {
    if (row.state == RowState.EMPTY || row.state == RowState.FAILED) return
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val portrait = row.id != "continue"
    val select = if (row.id == CONTINUE_ROW) continueWatchingActivation(play, open) else null
    val showFocusOutline = television || !compact && !browser
    val width = if (portrait) { if (compact) 112 else if (television) (240 * scale).toInt() else if (tablet) 164 else 176 }
        else { if (compact) 240 else if (television) (384 * scale).toInt() else if (tablet) 300 else if (browser) 212 else 280 }
    val cards = row.cards.distinctBy(::cardLookupKey)
        .distinctBy { it.title.trim().lowercase() to it.metadata?.year }
    val endCard = seeAllFor(row)
    var rowFocused by remember { mutableStateOf(false) }
    FollowNewFirstCard(list, cards.firstOrNull()?.let(::cardLookupKey), rowFocused)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (television) 16.dp * scale else 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, Modifier.weight(1f), fontSize = (if (television) 30 * scale else if (tablet) 20f else 18f).sp, lineHeight = (if (television) 36 * scale else if (tablet) 26f else 24f).sp, letterSpacing = (if (television) -0.3f * scale else if (tablet) -0.2f else -0.18f).sp, fontWeight = FontWeight.SemiBold)
            if (endCard != null && !television) {
                Text("See all", Modifier.clip(RoundedCornerShape(6.dp))
                    .pointerClickable(role = androidx.compose.ui.semantics.Role.Button, shape = RoundedCornerShape(6.dp), onClick = { openSeeAll(endCard) })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                    color = Palette.secondary, fontSize = (if (tablet) 15f else 14f).sp, fontWeight = FontWeight.Medium)
                if (!compact && cards.isNotEmpty()) Spacer(Modifier.width(12.dp))
            }
            if (!compact && !television && cards.isNotEmpty()) {
                CarouselArrow(false, list.canScrollBackward) {
                    scope.launch { list.animateScrollToItem(carouselPageTarget(list.layoutInfo, forward = false)) }
                }
                Spacer(Modifier.width(8.dp))
                CarouselArrow(true, list.canScrollForward) {
                    scope.launch { list.animateScrollToItem(carouselPageTarget(list.layoutInfo, forward = true)) }
                }
            }
        }
        when (row.state) {
            RowState.EMPTY, RowState.FAILED -> Unit
            RowState.READY -> LazyRow(Modifier.onFocusChanged { rowFocused = it.hasFocus }, state = list,
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp * scale else if (tablet) 14.dp else 12.dp)) {
                items(cards, key = ::cardLookupKey) { card ->
                    // Every platform scrolls a focused card into view: on web the browser focus can land outside the shelf viewport.
                    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    Box(Modifier.bringIntoViewRequester(bringIntoView)) {
                        MediaCard(card, width, portrait = portrait,
                            onFocus = {
                                if (!television) scope.launch { bringIntoView.bringIntoView() }
                                focus(card)
                            },
                            onHover = { if (row.id == CONTINUE_ROW) hover(card, it) },
                            open = open, select = select, labelScale = if (television) scale * 22f / 14f else if (browser) 12f / 14f else 1f,
                            cornerRadius = if (television) 12.dp * scale else 8.dp,
                            focusWidth = if (television) 4.dp * scale else 3.dp,
                            showFocusOutline = showFocusOutline,
                            watchedBadgeSize = if (television) 34.dp * scale else null)
                    }
                }
                if (endCard != null) item(key = seeAllKey(row.id)) {
                    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    SeeAllCard(endCard, width, { openSeeAll(endCard) }, portrait = portrait,
                        labelScale = if (television) scale * 22f / 14f else if (browser) 12f / 14f else 1f,
                        cornerRadius = if (television) 12.dp * scale else 8.dp, focusWidth = if (television) 4.dp * scale else 3.dp,
                        showFocusOutline = showFocusOutline,
                        modifier = Modifier.bringIntoViewRequester(bringIntoView).onFocusChanged {
                            if (it.isFocused && !television) scope.launch { bringIntoView.bringIntoView() }
                        })
                }
            }
            RowState.PENDING -> LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp * scale else if (tablet) 14.dp else 12.dp)) {
                items(4) { Box(Modifier.width(width.dp).aspectRatio(if (portrait) 2f / 3 else 16f / 9)
                    .clip(RoundedCornerShape(9.dp)).background(Palette.surface)) }
            }
        }
    }
}

internal fun carouselPageTarget(info: LazyListLayoutInfo, forward: Boolean): Int {
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return 0
    val whole = visible.filter { it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
    val first = (whole.firstOrNull() ?: visible.first()).index
    val page = whole.size.coerceAtLeast(1)
    return if (forward) (first + page).coerceAtMost(info.totalItemsCount - 1) else (first - page).coerceAtLeast(0)
}

@Composable
private fun CarouselArrow(forward: Boolean, enabled: Boolean, move: () -> Unit) {
    Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, Palette.raised, CircleShape)
        .pointerClickable(enabled = enabled, onClick = move, shape = CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (forward) Lucide.ChevronRight else Lucide.ChevronLeft,
            if (forward) "Next items" else "Previous items", Modifier.size(16.dp),
            tint = Palette.text.copy(alpha = if (enabled) 1f else 0.4f))
    }
}
