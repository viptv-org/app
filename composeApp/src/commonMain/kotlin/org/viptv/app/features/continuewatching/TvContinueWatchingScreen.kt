package org.viptv.app.features.continuewatching

import org.viptv.app.components.FocusLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AutomaticPage
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvGridItem
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.features.home.TvHomeSkeletonCard
import org.viptv.app.state.ContinueUndo
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val COLUMNS = 4
private const val GAP = 24f
private const val CARD_WIDTH = (1728f - GAP * (COLUMNS - 1)) / COLUMNS

@Composable
internal fun TvContinueWatchingScreen(page: ContinueWatchingPage, play: (Card) -> Unit, open: (Card) -> Unit,
    more: (() -> Unit)?, retry: () -> Unit, home: () -> Unit,
    remove: ((Card) -> Unit)? = null, undo: ContinueUndo? = null, undoRemove: () -> Unit = {}) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val grid = rememberLazyGridState()
    val cards = remember(page.cards) { page.cards.distinctBy(::cardLookupKey) }
    var lastKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    // The bar below the grid acts on the card the viewer last stood on, so it survives the move onto the bar.
    var lastFocused by remember { mutableStateOf<Card?>(null) }
    var confirming by remember { mutableStateOf<Card?>(null) }
    val target = lastFocused?.takeIf { card -> cards.any { cardLookupKey(it) == cardLookupKey(card) } } ?: cards.firstOrNull()
    val restore = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    val removal = remember { FocusRequester() }
    val undoFocus = remember { FocusRequester() }
    val restoreIndex = cards.indexOfFirst { cardLookupKey(it) == lastKey }.takeIf { it >= 0 }
        ?: lastIndex.coerceIn(0, cards.lastIndex.coerceAtLeast(0))
    suspend fun claim(requester: FocusRequester) {
        repeat(30) { if (runCatching { requester.requestFocus() }.getOrDefault(false)) return; withFrameNanos { } }
    }
    LaunchedEffect(cards.isEmpty(), page.loaded, page.error != null) {
        if (cards.isEmpty()) { if (!page.loading) claim(action); return@LaunchedEffect }
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == restoreIndex }) grid.scrollToItem(restoreIndex)
        claim(restore)
    }
    LaunchedEffect(cards) {
        val lost = focusedKey ?: return@LaunchedEffect
        if (cards.isEmpty() || cards.any { cardLookupKey(it) == lost }) return@LaunchedEffect
        focusedKey = null
        claim(restore)
    }
    val latestCards = rememberUpdatedState(cards)
    AutomaticPage(cards.size, page.busy, more) { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = 72.dp * scale)) {
            Text("Continue Watching", fontSize = (40 * scale).sp, lineHeight = (48 * scale).sp,
                letterSpacing = (-.8f * scale).sp, fontWeight = FontWeight.Bold)
            Text("Everything you’re in the middle of, most recent first.", color = Palette.secondary,
                fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
            Spacer(Modifier.height(32.dp * scale))
            if (page.busy && page.loaded) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            page.error?.takeIf { page.loaded }?.let { Text(it, color = Palette.danger, fontSize = (24 * scale).sp) }
            when {
                page.loading -> Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp * scale)) {
                            repeat(COLUMNS) { Box(Modifier.width(CARD_WIDTH.dp * scale)) { TvHomeSkeletonCard(scale, portrait = false) } }
                        }
                    }
                }
                !page.loaded && page.error != null -> TvPageMessage(scale, "Continue Watching didn’t load", page.error, "Try again", retry, action)
                page.empty -> TvPageMessage(scale, "Nothing in progress", "Titles you start watching appear here.", "Browse Home", home, action)
                else -> {
                    LazyVerticalGrid(GridCells.Fixed(COLUMNS), Modifier.fillMaxWidth().weight(1f).focusGroup()
                        .onFocusChanged {
                            if (!it.hasFocus && latestCards.value.any { card -> cardLookupKey(card) == focusedKey }) focusedKey = null
                        }, state = grid,
                        horizontalArrangement = Arrangement.spacedBy(GAP.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale),
                        contentPadding = PaddingValues(top = 8.dp * scale, bottom = 8.dp * scale)) {
                        itemsIndexed(cards, key = { _, card -> cardLookupKey(card) }, contentType = { _, _ -> "wide" }) { index, card ->
                            TvCatalogCard(card, scale, false, Modifier.tvGridItem(index)
                                .then(if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier)
                                .focusProperties {
                                    if (index < COLUMNS) up = FocusRequester.Cancel
                                    if (index % COLUMNS == COLUMNS - 1) { if (rtl) left = FocusRequester.Cancel else right = FocusRequester.Cancel }
                                },
                                { lastKey = cardLookupKey(it); lastIndex = index; focusedKey = lastKey; lastFocused = it },
                                continueWatchingActivation(play, open),
                                artworkWidth = CARD_WIDTH.dp * scale)
                        }
                    }
                    if (remove != null) TvRemovalBar(scale, target, undo, undoFocus, removal, request = { confirming = it }, undoRemove = undoRemove)
                }
            }
        }
    }
    confirming?.let { card ->
        ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = { confirming = null; remove?.invoke(card) }, dismiss = { confirming = null })
    }
}

@Composable
private fun TvRemovalBar(scale: Float, target: Card?, undo: ContinueUndo?, undoFocus: FocusRequester,
    requester: FocusRequester, request: (Card) -> Unit, undoRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp * scale, bottom = 96.dp * scale), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        if (undo != null) {
            Text("Removed “${undo.card.title}” from Continue Watching.", Modifier.weight(1f), color = Palette.secondary,
                fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TvAction(Modifier.height(64.dp * scale).width(200.dp * scale), scale, requester = undoFocus,
                activate = undoRemove, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                Text("Undo", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(target?.let { "“${it.title}”" }.orEmpty(), Modifier.weight(1f), color = Palette.secondary,
                fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TvAction(Modifier.height(64.dp * scale).width(560.dp * scale), scale, requester = requester, enabled = target != null,
            activate = { target?.let(request) }, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(REMOVE_CONTINUE_LABEL, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvPageMessage(scale: Float, title: String, message: String, action: String, activate: () -> Unit, requester: FocusRequester) {
    Column(Modifier.padding(top = 96.dp * scale), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        Text(title, fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
        Text(message, color = Palette.secondary, fontSize = (24 * scale).sp)
        TvAction(Modifier.height(64.dp * scale).width(280.dp * scale), scale, primary = true, requester = requester,
            activate = activate, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(action, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}
