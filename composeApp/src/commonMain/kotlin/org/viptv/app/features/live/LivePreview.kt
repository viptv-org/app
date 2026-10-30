package org.viptv.app.features.live

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
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.Button

@Composable
internal fun LivePreview(channel: LiveChannel, program: LiveProgram?, now: Int?, phone: Boolean, television: Boolean,
    scale: Float, watch: () -> Unit, favourite: (() -> Unit)?, guide: (() -> Unit)? = null) {
    val art = program?.artwork ?: channel.card.landscape ?: channel.card.poster
    if (phone) {
        Box(Modifier.fillMaxWidth().height(196.dp).pointerClickable(onClick = watch).clip(RoundedCornerShape(12.dp)).background(Palette.surface)) {
            if (art != null) AsyncImage(art, channel.card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            LiveBadge(Modifier.padding(12.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(channel.number, channel.card.title).joinToString(" · "), fontSize = 12.sp, color = Palette.secondary)
                Text(program?.title ?: channel.card.subtitle ?: channel.card.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (program != null && now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { liveProgress(program, now) }, modifier = Modifier.width(90.dp).height(3.dp),
                        color = Palette.text, trackColor = Palette.raised)
                    Text("${(program.end - now).coerceAtLeast(0)} min left", fontSize = 11.sp, color = Palette.secondary)
                }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxWidth()) {
    val imageWidth = (maxWidth * 0.4f).coerceAtMost(480.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        if (!television && art != null) AsyncImage(art, channel.card.title, Modifier.width(imageWidth).aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            Text(listOfNotNull(channel.number, channel.card.title).joinToString(" · "), fontSize = (if (television) 24 * scale else 14f).sp, color = Palette.secondary)
            Text(program?.title ?: channel.card.subtitle ?: channel.card.title, fontSize = (if (television) 54 * scale else 28f).sp,
                lineHeight = (if (television) 62 * scale else 34f).sp, fontWeight = FontWeight.Bold)
            program?.let {
                Text("${liveTime(it.start)} – ${liveTime(it.end, period = true)}", fontSize = (if (television) 24 * scale else 14f).sp, color = Palette.body)
                it.description?.let { description -> Text(description, fontSize = (if (television) 26 * scale else 14f).sp, color = Palette.body, maxLines = 2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                Button(watch, Modifier.height(40.dp * if (television) scale * 1.5f else 1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Watch")
                }
                favourite?.let { save -> Button(save, enabled = !channel.favoritePending, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                    Icon(Lucide.Star, null, Modifier.size(16.dp), tint = if (channel.favorite) Palette.accent else Palette.text); Spacer(Modifier.width(8.dp)); Text(if (channel.favorite) "Favourited" else "Favourite")
                } }
                if (guide != null) Button(guide, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                    Icon(Lucide.LayoutGrid, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Full guide")
                }
            }
        }
        if (television && art != null) AsyncImage(art, channel.card.title, Modifier.width(640.dp * scale).aspectRatio(16f / 9)
            .clip(RoundedCornerShape(16.dp * scale)), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
    }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(4.dp), color = Color(0xFFE8434F)) {
        Text("LIVE", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
}
