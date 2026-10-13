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
        Box(Modifier.fillMaxWidth().height(if (portrait) 600.dp else 480.dp)) {
            BufferedArtwork(card.landscape ?: card.poster, Modifier.fillMaxSize(), onReady = { carousel?.artworkReady(it) })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.65f to Color.Black.copy(alpha = 0.15f), 1f to Palette.background)))
            Box(Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 16.dp)) {
                HomeFilters(Screen.Home, navigate) { notice = "Genre filtering" }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, end = 32.dp, bottom = 24.dp).widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(180.dp).height(128.dp),
                    alignment = Alignment.BottomStart, contentScale = ContentScale.Fit)
                else Text(card.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                MediaMetadata(card)
                card.description?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body, maxLines = 2) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { play(card) }, modifier = Modifier.size(160.dp, 48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                        Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Play")
                    }
                    FilledIconButton(onClick = { save(card) }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                        Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp))
                    }
                    FilledIconButton(onClick = { open(card) }, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                        Icon(Lucide.Info, "More info", Modifier.size(22.dp))
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
