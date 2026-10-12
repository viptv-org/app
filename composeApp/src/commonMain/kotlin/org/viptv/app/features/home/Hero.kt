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
fun Hero(card: Card, compact: Boolean, television: Boolean, open: (Card) -> Unit) {
    if (compact || television) {
        Box(Modifier.fillMaxWidth().height(if (television) 580.dp else 490.dp).clip(RoundedCornerShape(if (compact) 12.dp else 0.dp))) {
            AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
            Column(Modifier.align(Alignment.BottomStart).padding(if (television) 70.dp else 24.dp).widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroText(card)
                Button(onClick = { open(card) }, shape = RoundedCornerShape(12.dp)) { Text("More info") }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("FEATURED", fontSize = 10.sp, letterSpacing = 2.sp, color = Palette.secondary)
                HeroText(card)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { open(card) }, shape = RoundedCornerShape(12.dp)) { Text("More info") }
                }
            }
            AsyncImage(card.landscape, null, Modifier.weight(0.62f).widthIn(max = 774.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun HeroText(card: Card) {
    if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.height(120.dp).widthIn(max = 320.dp), contentScale = ContentScale.Fit)
    else Text(card.title, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
    card.subtitle?.let { Text(it, fontSize = 13.sp, color = Palette.secondary) }
    card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 23.sp, color = Palette.secondary, maxLines = 4) }
}
