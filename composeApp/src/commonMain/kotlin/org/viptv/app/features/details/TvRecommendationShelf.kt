package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.features.home.MediaCard
import org.viptv.app.components.TvFocusScroll
import org.viptv.engine.wire.Card

@Composable
internal fun TvRecommendationShelf(cards: List<Card>, scale: Float, open: (Card) -> Unit, focused: () -> Unit) {
    Column(Modifier.padding(top = 20.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        Text("More Like This", Modifier.padding(start = 96.dp * scale), fontSize = (30 * scale).sp,
            lineHeight = (36 * scale).sp, letterSpacing = (-0.3f * scale).sp, fontWeight = FontWeight.SemiBold)
        TvFocusScroll {
        LazyRow(contentPadding = PaddingValues(horizontal = 96.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            items(cards.distinctBy { Triple(it.provider_id, it.identity.kind, it.identity.id) }, key = { it.provider_id + ":" + it.identity.kind + ":" + it.identity.id }) {
                MediaCard(it, (232 * scale).toInt(), portrait = true, showLabels = false, open = open, onFocus = { focused() },
                    cornerRadius = 12.dp * scale, focusWidth = 3.dp * scale)
            }
        }
        }
    }
}
