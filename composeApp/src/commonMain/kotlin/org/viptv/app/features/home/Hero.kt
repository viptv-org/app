package org.viptv.app.features.home

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
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
fun Hero(card: Card, compact: Boolean, television: Boolean, open: (Card) -> Unit, play: (Card) -> Unit = open) {
    if (compact) {
        PhoneHero(card, play)
    } else if (television) {
        Box(Modifier.fillMaxWidth().height(if (television) 580.dp else 490.dp).clip(RoundedCornerShape(if (compact) 12.dp else 0.dp))) {
            AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
            Column(Modifier.align(Alignment.BottomStart).padding(if (television) 70.dp else 24.dp).widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroText(card)
                Button(onClick = { open(card) }, shape = RoundedCornerShape(12.dp)) { Text("More info") }
            }
        }
    } else {
        DesktopHero(card, open)
    }
}

@Composable
private fun HeroText(card: Card) {
    if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.height(120.dp).widthIn(max = 320.dp), contentScale = ContentScale.Fit)
    else Text(card.title, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
    card.subtitle?.let { Text(it, fontSize = 13.sp, color = Palette.secondary) }
    card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 23.sp, color = Palette.secondary, maxLines = 4) }
}
