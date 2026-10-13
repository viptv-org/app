package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.features.home.MediaCard
import org.viptv.engine.wire.Card

@Composable
internal fun RelatedTitles(cards: List<Card>, open: (Card) -> Unit, modifier: Modifier = Modifier, showHeading: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showHeading) Text("More like this", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cards.distinctBy { it.identity.kind to it.identity.id }, key = { it.identity.kind + ":" + it.identity.id }) {
                MediaCard(it, width = 160, portrait = true, open = open)
            }
        }
    }
}
