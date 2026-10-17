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
import org.viptv.app.components.MediaMetadata
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.engine.wire.Card

@Composable
internal fun DetailHero(card: Card, compact: Boolean, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false,
    playFromStart: ((Card) -> Unit)? = null) {
    if (card.landscape == null && card.poster == null) {
        DetailSummary(card, play, Modifier.fillMaxWidth(), save, saved, playFromStart)
    } else if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                DetailArtwork(card, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Palette.background))))
            }
            DetailSummary(card, play, Modifier.fillMaxWidth(), save, saved, playFromStart)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            if (card.poster != null) {
                // Reference layout: portrait poster on the left column.
                DetailArtwork(card, Modifier.weight(0.30f).aspectRatio(2f / 3).clip(RoundedCornerShape(12.dp)), portrait = true)
                DetailSummary(card, play, Modifier.weight(0.58f), save, saved, playFromStart)
            } else {
                DetailArtwork(card, Modifier.weight(0.56f).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)))
                DetailSummary(card, play, Modifier.weight(0.44f), save, saved, playFromStart)
            }
        }
    }
}

@Composable
private fun DetailArtwork(card: Card, modifier: Modifier, portrait: Boolean = false) {
    if (portrait) AsyncImage(card.poster, null, modifier.background(Palette.surface), contentScale = ContentScale.Crop)
    else AsyncImage(card.landscape ?: card.poster, null, modifier.background(Palette.surface), contentScale = ContentScale.Crop)
}

@Composable
private fun DetailSummary(card: Card, play: (Card) -> Unit, modifier: Modifier, save: (Card) -> Unit, saved: Boolean,
    playFromStart: ((Card) -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(220.dp).height(120.dp),
            alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        else Text(card.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        MediaMetadata(card)
        val resuming = card.progress != null && card.progress > 0f
        Button(onClick = { play(card) }, modifier = Modifier.height(48.dp).widthIn(min = 140.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (resuming) "Resume" else "Play", fontWeight = FontWeight.SemiBold)
        }
        if (resuming && playFromStart != null) OutlinedButton(onClick = { playFromStart(card) },
            modifier = Modifier.height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)) {
            Text("Play from start", fontWeight = FontWeight.SemiBold)
        }
        FavoriteAction(card, saved, save)
        card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 22.sp, color = Palette.body,
            maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
    }
}
