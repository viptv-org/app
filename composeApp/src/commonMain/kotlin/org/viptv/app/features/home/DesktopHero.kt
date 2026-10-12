package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun DesktopHero(card: Card, open: (Card) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 48.dp)) {
        if (maxWidth < 900.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                HeroArtwork(card, Modifier.fillMaxWidth())
                FeaturedSummary(card, open, Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                FeaturedSummary(card, open, Modifier.weight(0.44f))
                HeroArtwork(card, Modifier.weight(0.56f))
            }
        }
    }
}

@Composable
private fun HeroArtwork(card: Card, modifier: Modifier) {
    AsyncImage(card.landscape ?: card.poster, card.title,
        modifier.aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)).background(Palette.surface),
        contentScale = ContentScale.Crop)
}

@Composable
private fun FeaturedSummary(card: Card, open: (Card) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("FEATURED", fontSize = 13.sp, letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold, color = Palette.secondary)
        if (card.logo != null) {
            AsyncImage(card.logo, card.title, Modifier.width(220.dp).height(120.dp),
                alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        } else {
            Text(card.title, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            card.subtitle?.let { Text(it, fontSize = 14.sp, color = Palette.secondary) }
            if (card.badges.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.badges.forEach { badge ->
                    Text(badge, Modifier.border(1.dp, Palette.secondary.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, color = Palette.secondary)
                }
            }
        }
        card.description?.let {
            Text(it, fontSize = 14.sp, lineHeight = 23.sp, color = Palette.secondary, maxLines = 3)
        }
        Button(onClick = { open(card) }, modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
            Icon(Lucide.Info, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("More info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
