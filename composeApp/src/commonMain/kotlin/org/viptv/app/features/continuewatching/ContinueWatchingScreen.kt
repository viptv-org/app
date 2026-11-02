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
import org.viptv.app.components.IconButton
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.app.components.LocalListRemoval
import org.viptv.app.components.PageRestore
import org.viptv.app.components.TextButton
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.home.MediaCard
import org.viptv.app.state.ContinueUndo
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
    remove: ((Card) -> Unit)? = null, undo: ContinueUndo? = null, undoRemove: () -> Unit = {}) {
    val grid = rememberLazyGridState()
    val restore = rememberPageRestore("continue")
    val cards = remember(page.cards) { page.cards.distinctBy(::cardLookupKey) }
    val anchored = restore.anchor(cards.size) { cardLookupKey(cards[it]) }
    PageRestore(grid, restore, target = {
        if (page.loaded) restore.anchor(cards.size) { cardLookupKey(cards[it]) }?.plus(CARDS_AFTER) else null
    })
    var confirming by remember { mutableStateOf<Card?>(null) }
    val removal = remember(remove) { remove?.let { ListRemoval(REMOVE_CONTINUE_LABEL, continueWatching = true) { card -> confirming = card } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val gutter = if (phone) 16.dp else 40.dp
        val gap = if (phone) 10.dp else 14.dp
        val available = maxWidth - gutter * 2
        val columns = if (phone) 2 else ((available + gap) / (MIN_CARD + gap)).toInt().coerceAtLeast(2)
        val cardWidth = ((available - gap * (columns - 1)) / columns).value.toInt()
        AutomaticPage(cards.size, page.busy, more) {
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.filter { it.contentType == CARD_TYPE }.maxOfOrNull { it.index }
                ?.minus(layout.totalItemsCount - cards.size) ?: -1
        }
        CompositionLocalProvider(LocalListRemoval provides removal) {
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = grid,
                contentPadding = PaddingValues(start = gutter, end = gutter, top = if (phone) 8.dp else 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item(TITLE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = TITLE_KEY) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (phone) IconButton(onClick = back, modifier = Modifier.size(32.dp)) {
                                Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp))
                            }
                            Text("Continue Watching", Modifier.weight(1f), fontSize = if (phone) 16.sp else 28.sp,
                                letterSpacing = if (phone) 0.sp else (-0.56).sp, fontWeight = FontWeight.Bold,
                                textAlign = if (phone) TextAlign.Center else TextAlign.Start)
                            if (phone) Spacer(Modifier.size(32.dp))
                        }
                        if (page.busy && page.loaded) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
                        page.error?.let { Text(it, color = Palette.danger) }
                        if (undo != null) RemovedNotice(undo, undoRemove)
                    }
                }
                when {
                    page.loading -> items(columns * 3, contentType = { "continue:skeleton" }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp)).background(Palette.surface))
                    }
                    !page.loaded && page.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        PageMessage("Continue Watching didn’t load", "Check the connection and try again.", "Try again", retry, phone)
                    }
                    page.empty -> item(span = { GridItemSpan(maxLineSpan) }) {
                        PageMessage("Nothing in progress", "Titles you start watching appear here.", "Browse Home", home, phone)
                    }
                    else -> itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> CARD_TYPE }) { index, card ->
                        val key = cardLookupKey(card)
                        val activate = continueWatchingActivation(play, open)
                        MediaCard(card, cardWidth, portrait = false,
                            open = { restore.mark(key, index); open(it) },
                            select = { restore.mark(key, index); activate(it) },
                            modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index),
                            showFocusOutline = !phone)
                    }
                }
            }
        }
    }
    confirming?.let { card ->
        ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = { confirming = null; remove?.invoke(card) }, dismiss = { confirming = null })
    }
}

@Composable
internal fun RemovedNotice(undo: ContinueUndo, undoRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.surface)
        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Removed “${undo.card.title}” from Continue Watching.", Modifier.weight(1f), fontSize = 13.sp,
            lineHeight = 18.sp, color = Palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = undoRemove) { Text("Undo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PageMessage(title: String, message: String, action: String, activate: () -> Unit, phone: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (phone) 64.dp else 96.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Palette.raised) {
            Icon(Lucide.Play, null, Modifier.padding(20.dp).size(32.dp), tint = Palette.secondary)
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, fontSize = 14.sp, color = Palette.secondary, textAlign = TextAlign.Center)
        Button(onClick = activate, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text(action) }
    }
}
