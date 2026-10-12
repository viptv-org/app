package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun DetailHero(card: Card, compact: Boolean, play: (Card) -> Unit) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                DetailArtwork(card, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Palette.background))))
            }
            DetailSummary(card, play, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            DetailSummary(card, play, Modifier.weight(0.44f))
            DetailArtwork(card, Modifier.weight(0.56f).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
private fun DetailArtwork(card: Card, modifier: Modifier) {
    AsyncImage(card.landscape ?: card.poster, null, modifier.background(Palette.surface), contentScale = ContentScale.Crop)
}

@Composable
private fun DetailSummary(card: Card, play: (Card) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.width(220.dp).height(120.dp),
            alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        else Text(card.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        card.subtitle?.let { Text(it, fontSize = 14.sp, color = Palette.secondary) }
        if (card.badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            card.badges.forEach { badge ->
                OutlinedCard(shape = RoundedCornerShape(3.dp)) {
                    Text(badge, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp)
                }
            }
        }
        Button(onClick = { play(card) }, modifier = Modifier.height(48.dp).widthIn(min = 140.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Icon(Lucide.Play, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play", fontWeight = FontWeight.SemiBold)
        }
        card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = Palette.secondary) }
    }
}
