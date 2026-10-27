package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvMediaBadge
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvLivePreview(channel: LiveChannel, program: LiveProgram?, now: Int?, scale: Float, favorite: (() -> Unit)? = null, watch: () -> Unit) {
    Column(Modifier.offset(96.dp * scale, (-2).dp * scale).width(820.dp * scale), verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            Text("LIVE", Modifier.background(Color(0xFFE5484D), RoundedCornerShape(6.dp * scale)).padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                color = Color.White, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp,
                letterSpacing = (1.08f * scale).sp, fontWeight = FontWeight.ExtraBold)
            Text(listOfNotNull(channel.number, channel.card.title).joinToString(" · "), fontSize = (22 * scale).sp,
                lineHeight = (26 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
        }
        Text(program?.title ?: channel.card.subtitle ?: channel.card.title, fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp,
            letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2)
        program?.let {
            val remaining = if (now != null && now in it.start until it.end) " · ${it.end - now} min left" else ""
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                Text("${liveTime(it.start)} – ${liveTime(it.end, true)}$remaining", fontSize = (24 * scale).sp,
                    lineHeight = (30 * scale).sp, color = Palette.body, fontWeight = FontWeight.Medium)
                channel.card.badges.forEach { TvMediaBadge(it, scale) }
            }
            it.description?.let { description -> Text(description, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp,
                color = Palette.body, maxLines = 2) }
        }
    }
    if (favorite != null) TvAction(Modifier.offset(96.dp * scale, 306.dp * scale).height(52.dp * scale), scale,
        activate = favorite, enabled = !channel.favoritePending, contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
        Icon(Lucide.Star, null, Modifier.size(24.dp * scale), tint = it); Spacer(Modifier.width(10.dp * scale))
        Text(if (channel.favorite) "Remove favourite" else "Favourite channel", color = it, fontSize = (22 * scale).sp)
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp * scale)
    Box(Modifier.offset(1184.dp * scale, (-12).dp * scale).size(640.dp * scale, 360.dp * scale).clip(shape)
        .background(Color(0xFF212124)).onFocusChanged { focused = it.isFocused }
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = watch)) {
        AsyncImage(program?.artwork ?: channel.card.landscape ?: channel.card.poster, channel.card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(90.dp * scale)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp * scale, bottom = 14.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            Icon(Lucide.Play, null, Modifier.size(24.dp * scale))
            Text("OK to watch", fontSize = (20 * scale).sp, lineHeight = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        if (focused) Box(Modifier.fillMaxSize().border(3.dp * scale, Color.White, shape))
    }
}
