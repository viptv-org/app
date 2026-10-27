package org.viptv.app.features.player

import org.viptv.app.components.FocusLazyRow as LazyRow
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
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.engine.wire.Card

@Composable
internal fun TvPlayerEpisodePicker(episodes: List<Card>, scale: Float, current: Card?, choose: (Card) -> Unit, close: () -> Unit) {
    val initial = remember { FocusRequester() }
    // Land on the playing episode: it is focused, marked, and scrolled into view.
    val currentIndex = episodes.indexOfFirst { current != null && it.provider_id == current.provider_id && it.identity.id == current.identity.id }
    val list = rememberLazyListState()
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            // The board's 540 px sheet, taller only when a playing card's extra line needs it.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 540.dp * scale).background(Color(0xFF191818))
                .padding(top = 48.dp * scale, bottom = 40.dp * scale),
                verticalArrangement = Arrangement.spacedBy(30.dp * scale)) {
                Text("Episodes", Modifier.padding(start = 96.dp * scale), fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                LazyRow(state = list, contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    itemsIndexed(episodes, key = { _, card -> card.provider_id + ":" + card.identity.id }) { index, card ->
                        // The Details episode card: still, Watched pill, progress bar, plus the Now playing marker.
                        TvEpisodeCard(card, scale, play = { choose(card) }, onFocus = {},
                            requester = if (index == currentIndex.coerceAtLeast(0)) initial else null,
                            up = FocusRequester.Default, playing = index == currentIndex)
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
