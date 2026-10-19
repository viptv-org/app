package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Check
import org.viptv.app.theme.Palette
import org.viptv.app.components.MediaMetadata
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.engine.wire.Card

@Composable
internal fun PhoneHero(card: Card, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean, carousel: HeroCarousel? = null) {
    Box(Modifier.fillMaxWidth().height(470.dp).clip(RoundedCornerShape(16.dp)).background(Palette.surface)) {
        val art = card.landscape ?: card.poster
        if (art == null) HeroArtworkFallback(Modifier.fillMaxSize())
        else BufferedArtwork(art, Modifier.fillMaxSize(), onReady = { carousel?.artworkReady(it) })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, 0.48f to Color.Transparent,
            0.8f to Color.Black.copy(alpha = 0.65f), 1f to Palette.background.copy(alpha = 0.96f))))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(180.dp).height(100.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
            else Text(card.title, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            MediaMetadata(card, fontSize = 13f, lineHeight = 19f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { play(card) }, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = { save(card) }, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                    Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("My List", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
        }
    }
}
