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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Check
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.app.components.MediaMetadata
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending

@Composable
internal fun PhoneHero(card: Card, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean, carousel: HeroCarousel? = null) {
    Box(Modifier.fillMaxWidth().height(470.dp).clip(RoundedCornerShape(16.dp)).background(Palette.surface)) {
        // The artwork and scrim are static while the list scrolls: composite them once and translate the cached layer.
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val art = card.landscape ?: card.poster
            if (art == null) HeroArtworkFallback(Modifier.fillMaxSize())
            else BufferedArtwork(art, Modifier.fillMaxSize(), onReady = { carousel?.artworkReady(it) })
            Box(Modifier.fillMaxSize().background(Scrim.phoneHero))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroCopyFade(card, Modifier.fillMaxWidth().weight(1f), Alignment.BottomCenter) { shown ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (shown.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(shown.logo), shown.title, Modifier.width(180.dp).heightIn(min = 100.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter)
                    else Text(shown.title, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                    MediaMetadata(shown, fontSize = 13f, lineHeight = 19f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { play(shown) }, modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                            if (shown.isPlayPending()) PendingActionSpinner(Modifier.size(16.dp), size = 16.dp, strokeWidth = 2.dp, color = Palette.background)
                            else Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        val mutations = LocalPendingMutation.current
                        val target = saveTarget(shown)
                        val pending = mutations.isPending(target)
                        Button(onClick = { save(shown) }, modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                            if (pending) MutationSpinner(18.dp) else Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (pending) "Saving…" else "My List", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
        }
    }
}
