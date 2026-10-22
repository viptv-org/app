package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.features.home.MediaCard
import org.viptv.engine.wire.Card

@Composable
internal fun TvPlayerEpisodePicker(episodes: List<Card>, scale: Float, current: Card?, choose: (Card) -> Unit, close: () -> Unit) {
    val initial = remember { FocusRequester() }
    // Land on the playing episode: it is focused, marked, and scrolled into view.
    val currentIndex = episodes.indexOfFirst { current != null && it.provider_id == current.provider_id && it.identity.id == current.identity.id }
    val list = rememberLazyListState()
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(540.dp * scale).background(Color(0xFF191818)).padding(top = 48.dp * scale),
                verticalArrangement = Arrangement.spacedBy(30.dp * scale)) {
                Text("Episodes", Modifier.padding(start = 96.dp * scale), fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                TvFocusScroll {
                    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                        itemsIndexed(episodes, key = { _, card -> card.provider_id + ":" + card.identity.id }) { index, card ->
                            val playing = index == currentIndex
                            val titled = card.copy(title = (card.identity.episode?.let { "${it.episode}. " } ?: "") + card.title)
                            MediaCard(if (playing) titled.copy(title = "\u25B6 " + titled.title) else titled,
                                (448 * scale).toInt(), labelScale = scale * 24 / 14, cornerRadius = 12.dp * scale, open = { choose(card) },
                                modifier = if (index == currentIndex.coerceAtLeast(0)) Modifier.focusRequester(initial) else Modifier)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            if (episodes.isEmpty()) return@LaunchedEffect
            if (currentIndex > 0) list.scrollToItem(currentIndex)
            initial.requestFocus()
        }
    }
}
