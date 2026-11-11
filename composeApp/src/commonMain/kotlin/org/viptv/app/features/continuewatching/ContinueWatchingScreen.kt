package org.viptv.app.features.continuewatching

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.Button
import org.viptv.app.components.BackControl
import org.viptv.app.components.IconButton
import org.viptv.app.components.ListPageState
import org.viptv.app.components.ListSkeletonTile
import org.viptv.app.components.pageStateLayout
import org.viptv.app.components.LocalListRemoval
import org.viptv.app.components.PageRestore
import org.viptv.app.components.TextButton
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val TITLE_KEY = "continue:title"
private const val CARD_TYPE = "continue:card"
private const val CARDS_AFTER = 1
private val MIN_CARD = 260.dp
internal const val REMOVE_CONTINUE_LABEL = "Remove from Continue Watching"

@Composable
internal fun ContinueWatchingScreen(page: ContinueWatchingPage, play: (Card) -> Unit, open: (Card) -> Unit,
    more: (() -> Unit)?, retry: () -> Unit, back: () -> Unit, home: () -> Unit,
    remove: ((Card) -> Unit)? = null) {
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("continue")
    val cards = remember(page.cards) { page.cards.distinctBy(::cardLookupKey) }
    val anchored = restore.anchor(cards.size) { cardLookupKey(cards[it]) }
    PageRestore(grid, restore, target = {
        if (page.loaded) restore.anchor(cards.size) { cardLookupKey(cards[it]) }?.plus(CARDS_AFTER) else null
    })
    val removal = remember(remove) { ContinueWatchingRemoval(remove) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val gutter = if (phone) 16.dp else 40.dp
        val gap = if (phone) 10.dp else 14.dp
        val available = maxWidth - gutter * 2
        val columns = if (phone) 2 else ((available + gap) / (MIN_CARD + gap)).toInt().coerceAtLeast(2)
        val cardWidth = ((available - gap * (columns - 1)) / columns).value.toInt()
        val pageStateHeight = (maxHeight - (if (phone) 0.dp else 24.dp) - 56.dp - 18.dp - 24.dp).coerceAtLeast(200.dp)
        AutomaticPage(cards.size, page.busy, more) {
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.filter { it.contentType == CARD_TYPE }.maxOfOrNull { it.index }
                ?.minus(layout.totalItemsCount - cards.size) ?: -1
        }
        CompositionLocalProvider(LocalListRemoval provides removal.offer) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = grid,
                contentPadding = PaddingValues(start = gutter, end = gutter, top = if (phone) 0.dp else 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item(TITLE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = TITLE_KEY) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (phone) Box(Modifier.fillMaxWidth().height(56.dp)) {
                            BackControl(back, Modifier.align(Alignment.CenterStart))
                            Text("Continue Watching", Modifier.align(Alignment.Center), fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        } else Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Continue Watching", Modifier.weight(1f), fontSize = 28.sp,
                                letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start)
                        }
                    }
                }
                when {
                    page.state == ListPageState.Loading -> items(columns * 3, contentType = { "continue:skeleton" }) {
                        ListSkeletonTile(Modifier.fillMaxWidth().aspectRatio(16f / 9))
                    }
                    page.state == ListPageState.Failed || page.state == ListPageState.Empty -> item(span = { GridItemSpan(maxLineSpan) }, key = "state") {
                        Box(Modifier.fillMaxWidth().height(pageStateHeight)) {
                            ContinueWatchingState(page.state, page.error, retry, home,
                                layout = pageStateLayout(television = false, compact = phone))
                        }
                    }
                    else -> {
                        itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> CARD_TYPE }) { index, card ->
                            val key = cardLookupKey(card)
                            val activate = continueWatchingActivation(play, open)
                            MediaCard(card, cardWidth, portrait = false,
                                open = { restore.mark(key, index); open(it) },
                                select = { restore.mark(key, index); activate(it) },
                                modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index),
                                showFocusOutline = !phone)
                        }
                        if (page.loaded && !page.busy && page.pageError != null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(failed = true, retry = more ?: retry, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth())
                        } else if (page.loaded && page.busy) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth())
                        } else if (page.loaded && !page.busy && page.pageError == null && more == null) item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    removal.confirmation()
}

