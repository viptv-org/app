package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.features.home.MediaCard
import org.viptv.engine.wire.Card

@Composable
internal fun RelatedTitles(cards: List<Card>, open: (Card) -> Unit, modifier: Modifier = Modifier, showHeading: Boolean = true,
    /** The page gutter: the heading sits inside it, the cards scroll out to the edges (app#63). */
    gutter: Dp = 0.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showHeading) Text("More like this", Modifier.padding(horizontal = gutter), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cards.distinctBy { it.identity.kind to it.identity.id }, key = { it.identity.kind + ":" + it.identity.id }) {
                MediaCard(it, width = 160, portrait = true, open = open)
            }
        }
    }
}
