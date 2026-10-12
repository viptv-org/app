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
import org.viptv.app.components.MediaMetadata
import org.viptv.engine.wire.Card

@Composable
internal fun DesktopHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, browser: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = if (browser) 57.dp else 28.dp, bottom = if (browser) 88.dp else 80.dp)) {
        if (maxWidth < 900.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                HeroArtwork(card, Modifier.fillMaxWidth())
                FeaturedSummary(card, open, play, Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                FeaturedSummary(card, open, play, Modifier.weight(0.45f))
                HeroArtwork(card, Modifier.weight(0.55f))
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
private fun FeaturedSummary(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("FEATURED", fontSize = 13.sp, letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold, color = Palette.secondary)
        if (card.logo != null) {
            AsyncImage(card.logo, card.title, Modifier.width(220.dp).height(120.dp),
                alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        } else {
            Text(card.title, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        }
        MediaMetadata(card)
        card.description?.let {
            Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body, maxLines = 3)
        }
        DesktopHeroActions({ play(card) }, { open(card) })
    }
}
