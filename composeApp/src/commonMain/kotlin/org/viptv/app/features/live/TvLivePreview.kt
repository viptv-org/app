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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvMediaBadge
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.pointerClickable
import org.viptv.app.format.providerText
import org.viptv.app.state.liveFavoriteTarget

@Composable
internal fun TvLivePreview(channel: LiveChannel, program: LiveProgram?, now: Int?, scale: Float, favorite: (() -> Unit)? = null,
    favoriteFocus: FocusRequester? = null, watchFocus: FocusRequester? = null, down: FocusRequester? = null,
    onHeight: (Int) -> Unit = {}, watch: () -> Unit) {
    val mutations = LocalPendingMutation.current
    val target = liveFavoriteTarget(channel.id)
    val pending = mutations.isPending(target)
    val failure = mutations.failure(target)
    Column(Modifier.offset(96.dp * scale, (-2).dp * scale).width(820.dp * scale).onSizeChanged { onHeight(it.height) },
        verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            if (program == null || liveFraming(program, now) == LiveFraming.OnAir)
                Text("LIVE", Modifier.background(Color(0xFFE5484D), RoundedCornerShape(6.dp * scale)).padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                    color = Color.White, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp,
                    letterSpacing = (1.08f * scale).sp, fontWeight = FontWeight.ExtraBold)
            Text(listOfNotNull(channel.number, channel.card.title).joinToString(" · "), fontSize = (22 * scale).sp,
                lineHeight = (26 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
        }
        (program?.title ?: channel.card.subtitle)?.let {
            Text(it, fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp,
                letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        program?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                Text("${liveTime(it.start)} – ${liveTime(it.end, true)}${liveRangeStatus(it, now)}", fontSize = (24 * scale).sp,
                    lineHeight = (30 * scale).sp, color = Palette.body, fontWeight = FontWeight.Medium)
                channel.card.badges.forEach { TvMediaBadge(it, scale) }
            }
            it.description.providerText()?.let { description -> Text(description, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp,
                color = Palette.body, maxLines = 2) }
        }
        failure?.let { Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, color = Palette.danger) }
    }
    if (favorite != null) TvAction(Modifier.offset(96.dp * scale, 306.dp * scale).heightIn(min = 52.dp * scale).focusProperties { if (down != null) this.down = down }, scale,
        requester = favoriteFocus, activate = if (failure != null) ({ mutations.retry(target) }) else favorite, contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
        when {
            pending -> { MutationSpinner(24.dp * scale, scale = scale, color = it); Spacer(Modifier.width(10.dp * scale)); Text("Saving…", color = it, fontSize = (22 * scale).sp) }
            failure != null -> { Icon(Lucide.RotateCcw, null, Modifier.size(24.dp * scale), tint = it); Spacer(Modifier.width(10.dp * scale)); Text("Retry", color = it, fontSize = (22 * scale).sp) }
            else -> { Icon(Lucide.Star, null, Modifier.size(24.dp * scale), tint = it); Spacer(Modifier.width(10.dp * scale))
                Text(if (channel.favorite) "Remove favourite" else "Favourite channel", color = it, fontSize = (22 * scale).sp) }
        }
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp * scale)
    Box(Modifier.offset(1184.dp * scale, (-12).dp * scale).size(640.dp * scale, 360.dp * scale)
        .then(if (watchFocus == null) Modifier else Modifier.focusRequester(watchFocus))
        .focusProperties { if (down != null) this.down = down }.clip(shape)
        .background(Color(0xFF212124)).onFocusChanged { focused = it.isFocused }
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = watch)) {
        AsyncImage(program?.artwork ?: channel.card.landscape ?: channel.card.poster, channel.card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Scrim.tvLivePreview))
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp * scale, bottom = 14.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            Icon(Lucide.Play, null, Modifier.size(24.dp * scale))
            Text("OK to watch", fontSize = (20 * scale).sp, lineHeight = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        if (focused) Box(Modifier.fillMaxSize().border(3.dp * scale, Color.White, shape))
    }
}
