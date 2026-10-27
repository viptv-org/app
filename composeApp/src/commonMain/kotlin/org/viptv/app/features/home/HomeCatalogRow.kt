package org.viptv.app.features.home

import org.viptv.app.components.FocusLazyRow as LazyRow
import org.viptv.app.connection.cardLookupKey
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import kotlinx.coroutines.launch
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.*
import org.viptv.app.components.pointerClickable

/** Home's Continue Watching row id. */
internal const val CONTINUE_ROW = "continue"

@Composable
internal fun HomeCatalogRow(row: HomeRow, compact: Boolean, television: Boolean, modifier: Modifier,
    focus: (Card) -> Unit, open: (Card) -> Unit, scale: Float = 1f, tablet: Boolean = false, browser: Boolean = false,
    play: (Card) -> Unit = open) {
    // Home only composes rows with content; this guard keeps a direct caller
    // from drawing a heading with no shelf under it.
    if (row.state == RowState.EMPTY || row.state == RowState.FAILED) return
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val portrait = row.id != "continue"
    // A Continue Watching card resumes: tapping/clicking it opens that exact
    // episode's (or movie's) sources. Info in the desktop preview still opens Details.
    val select = if (row.id == CONTINUE_ROW) play else null
    // Touch platforms (phone web/app) have no focus navigation; the swipe
    // outline a focused card would show must not render during scrolling.
    val showFocusOutline = television || !compact && !browser
    val width = if (portrait) { if (compact) 112 else if (television) (240 * scale).toInt() else if (tablet) 164 else 176 }
        else { if (compact) 240 else if (television) (384 * scale).toInt() else if (tablet) 300 else if (browser) 212 else 280 }
    // NAV-11: providers can return the same title under different ids/origins.
    // Dedupe by identity first, then by title+year so a shelf never shows the
    // same name twice.
    val cards = row.cards.distinctBy(::cardLookupKey)
        .distinctBy { it.title.trim().lowercase() to it.metadata?.year }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (television) 16.dp * scale else 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, Modifier.weight(1f), fontSize = (if (television) 30 * scale else if (tablet) 20f else 18f).sp, lineHeight = (if (television) 36 * scale else if (tablet) 26f else 24f).sp, letterSpacing = (if (television) -0.3f * scale else if (tablet) -0.2f else -0.18f).sp, fontWeight = FontWeight.SemiBold)
            if (!compact && !television && cards.isNotEmpty()) {
                CarouselArrow(false, list.canScrollBackward) {
                    scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex - 1).coerceAtLeast(0)) }
                }
                Spacer(Modifier.width(8.dp))
                CarouselArrow(true, list.canScrollForward) {
                    scope.launch { list.animateScrollToItem((list.firstVisibleItemIndex + 1).coerceAtMost(cards.lastIndex)) }
                }
            }
        }
        when (row.state) {
            // Hidden rows never reach this composable; keep the switch exhaustive.
            RowState.EMPTY, RowState.FAILED -> Unit
            RowState.READY -> LazyRow(state = list,
                contentPadding = PaddingValues(end = if (compact) 24.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp * scale else if (tablet) 14.dp else 12.dp)) {
                items(cards, key = ::cardLookupKey) { card ->
                    // Every platform, not just TV: a card that takes focus must be
                    // scrolled into view. On web the browser focus could land on a
                    // card that was fully outside the shelf viewport ("focus moves
                    // off the screen to the left" after scrolling right and back).
                    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    Box(Modifier.bringIntoViewRequester(bringIntoView)) {
                        MediaCard(card, width, portrait = portrait,
                            onFocus = {
                                if (!television) scope.launch { bringIntoView.bringIntoView() }
                                focus(card)
                            },
                            open = open, select = select, labelScale = if (television) scale * 22f / 14f else if (browser) 12f / 14f else 1f,
                            cornerRadius = if (television) 12.dp * scale else 8.dp,
                            focusWidth = if (television) 4.dp * scale else 3.dp,
                            showFocusOutline = showFocusOutline,
                            watchedBadgeSize = if (television) 34.dp * scale else null)
                    }
                }
            }
            RowState.PENDING -> LazyRow(horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp * scale else if (tablet) 14.dp else 12.dp)) {
                items(4) { Box(Modifier.width(width.dp).aspectRatio(if (portrait) 2f / 3 else 16f / 9)
                    .clip(RoundedCornerShape(9.dp)).background(Palette.surface)) }
            }
        }
    }
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
