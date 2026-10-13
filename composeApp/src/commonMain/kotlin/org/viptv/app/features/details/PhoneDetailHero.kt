package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.MediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

/** M3Title: full-width artwork, overlapping logo, then a full-width playback action. */
@Composable
internal fun PhoneDetailHero(card: Card, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false) {
    val hasArtwork = card.landscape != null || card.poster != null
    Column(verticalArrangement = Arrangement.spacedBy(if (hasArtwork) (-60).dp else 0.dp)) {
        if (hasArtwork) Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(
                0f to Palette.background.copy(alpha = .55f), .3f to Color.Transparent,
                .55f to Color.Transparent, 1f to Palette.background)))
        } else Spacer(Modifier.height(64.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(240.dp).height(76.dp)
                ,
                alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
            else Text(card.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            MediaMetadata(card, fontSize = 13f, lineHeight = 19f)
            Button(onClick = { play(card) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            FavoriteAction(card, saved, save)
            card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = Palette.body,
                maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
    }
}
