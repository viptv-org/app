package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.HomeFilters
import org.viptv.app.components.MediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TabletHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, navigate: (Screen) -> Unit, save: (Card) -> Unit, saved: Boolean, carousel: HeroCarousel? = null) {
    var notice by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val portrait = maxWidth < 900.dp
        Box(Modifier.fillMaxWidth().height(if (portrait) 640.dp else 520.dp)) {
            val art = card.landscape ?: card.poster
            if (art == null) HeroArtworkFallback(Modifier.fillMaxSize())
            else BufferedArtwork(art, Modifier.fillMaxSize(), onReady = { carousel?.artworkReady(it) })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.background.copy(alpha = 0.85f),
                0.45f to Palette.background.copy(alpha = 0.4f), 0.7f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Palette.background)))
            Box(Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 40.dp)) {
                HomeFilters(Screen.Home, navigate) { notice = "Genre filtering" }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, end = 32.dp, bottom = 40.dp).widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(300.dp).height(130.dp),
                    alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
                else Text(card.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                MediaMetadata(card)
                card.description?.let { Text(it, fontSize = 16.sp, lineHeight = 23.sp, color = Palette.body, maxLines = 2) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { play(card) }, modifier = Modifier.size(160.dp, 48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                        Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledIconButton(onClick = { save(card) }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Palette.text)) {
                        Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(24.dp))
                    }
                    FilledIconButton(onClick = { open(card) }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Palette.text)) {
                        Icon(Lucide.Info, "More info", Modifier.size(24.dp))
                    }
                }
                carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
            }
        }
    }
    notice?.let { label -> AlertDialog(onDismissRequest = { notice = null }, title = { Text(label) },
        text = { Text("$label is not available in this experiment yet.") },
        confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
}
