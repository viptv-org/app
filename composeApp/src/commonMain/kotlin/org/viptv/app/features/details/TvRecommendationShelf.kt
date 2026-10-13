package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.rememberTvShelfScroll
import org.viptv.engine.wire.Card

@Composable
internal fun TvRecommendationShelf(cards: List<Card>, scale: Float, open: (Card) -> Unit, focused: () -> Unit) {
    val visible = cards.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }
    val rows = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val scroll = rememberTvShelfScroll(rows, 248.dp * scale, maxWidth, visible.size, 96.dp * scale)
    Column(Modifier.padding(top = 20.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        Text("More Like This", Modifier.padding(start = 96.dp * scale), fontSize = (30 * scale).sp,
            lineHeight = (36 * scale).sp, letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll(manual = true) {
        LazyRow(state = rows, modifier = Modifier.focusRestorer().focusGroup(), contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            itemsIndexed(visible, key = { _, card -> card.provider_id + ":" + card.identity.kind + ":" + card.identity.id }) { index, card ->
                MediaCard(card, (232 * scale).toInt(), portrait = true, showLabels = false, open = open, onFocus = { scroll(index); focused() },
                    cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale)
            }
        }
        }
    }
    }
}
