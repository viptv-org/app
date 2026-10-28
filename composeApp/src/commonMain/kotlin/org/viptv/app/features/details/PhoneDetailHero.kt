package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.MediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.Button

/** M3Title: full-width artwork, overlapping logo, then a full-width playback action. */
@Composable
internal fun PhoneDetailHero(card: Card, play: (Card) -> Unit, save: (Card) -> Unit = {}, saved: Boolean = false,
    resumeEpisode: Card? = null) {
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
            // Board pill: coordinates left-aligned, time left hard right, progress underline inside.
            val resume = resumeEpisode ?: card.takeIf(::resuming)
            val progress = resume?.progress?.takeIf { resuming(resume) }
            val remaining = resume?.takeIf(::resuming)?.let(::remainingMinutesLabel)
            val label = heroPlayLabel(resume)
            Box(Modifier.fillMaxWidth()) {
                Button(onClick = { play(resume ?: card) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 19.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Color(0xFF111113))) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (remaining != null) Arrangement.SpaceBetween else Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        if (remaining != null) {
                            Text(remaining, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111113).copy(alpha = .7f))
                        }
                    }
                }
                progress?.let { fraction ->
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 5.dp)
                        .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x2E111113))) {
                        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(.02f, .98f)).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp)).background(Color(0xFF111113)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(76.dp).pointerClickable { save(card) }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(44.dp).background(Color.White.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (saved) Lucide.Check else Lucide.Plus,
                            if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp), tint = Palette.text)
                    }
                    Text("My List", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.body)
                }
            }
            card.description?.let { Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = Palette.body,
                maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
    }
}
