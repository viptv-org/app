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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.Button
import org.viptv.app.components.IconButton
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val TITLE_KEY = "continue:title"
private const val CARD_TYPE = "continue:card"
/** The narrowest Continue Watching card a tablet, desktop or browser grid packs columns by. */
private val MIN_CARD = 260.dp

/**
 * Phone, tablet, desktop and browser: Continue Watching as a grid of the Home row's 16:9 cards,
 * with their progress bars and art fallbacks (app#62). It loads more as the viewer nears the
 * end. A card resumes ([play]); a caught-up card, which has nothing to resume, opens Details
 * instead (app#132), as the desktop preview's Info does ([open]).
 */
@Composable
internal fun ContinueWatchingScreen(page: ContinueWatchingPage, play: (Card) -> Unit, open: (Card) -> Unit,
    more: (() -> Unit)?, retry: () -> Unit, back: () -> Unit, home: () -> Unit) {
    val grid = rememberLazyGridState()
    val cards = remember(page.cards) { page.cards.distinctBy(::cardLookupKey) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val gutter = if (phone) 16.dp else 40.dp
        val gap = if (phone) 10.dp else 14.dp
        val available = maxWidth - gutter * 2
        val columns = if (phone) 2 else ((available + gap) / (MIN_CARD + gap)).toInt().coerceAtLeast(2)
        val cardWidth = ((available - gap * (columns - 1)) / columns).value.toInt()
        AutomaticPage(cards.size, page.busy, more) {
            // Card position, not grid index: the heading comes first.
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.filter { it.contentType == CARD_TYPE }.maxOfOrNull { it.index }
                ?.minus(layout.totalItemsCount - cards.size) ?: -1
        }
        LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = grid,
            contentPadding = PaddingValues(start = gutter, end = gutter, top = if (phone) 8.dp else 24.dp, bottom = if (phone) 96.dp else 24.dp),
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
                else -> items(cards, key = ::cardLookupKey, contentType = { CARD_TYPE }) { card ->
                    MediaCard(card, cardWidth, portrait = false, open = open, select = continueWatchingActivation(play, open),
                        showFocusOutline = !phone)
                }
            }
        }
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
