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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.app.components.FavouriteStar
import org.viptv.app.components.LiveBadge
import org.viptv.app.components.TvAction
import org.viptv.app.components.withoutLiveTag
import org.viptv.app.components.TvMediaBadge
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.pointerClickable
import org.viptv.app.format.providerText
import org.viptv.app.state.liveFavoriteTarget

internal val TvLivePreviewTop = 12.dp
internal val TvLivePreviewGap = 12.dp
internal val TvLivePreviewActionTop = 306.dp

@Composable
internal fun TvLivePreview(channel: LiveChannel, program: LiveProgram?, now: Int?, scale: Float, favorite: (() -> Unit)? = null,
    favoriteFocus: FocusRequester? = null, watchFocus: FocusRequester? = null, down: FocusRequester? = null,
    onHeight: (Int) -> Unit = {}, watch: () -> Unit) {
    val mutations = LocalPendingMutation.current
    val target = liveFavoriteTarget(channel.id)
    val pending = mutations.isPending(target)
    val failure = mutations.failure(target)
    // The gap grows with the display text size, so the action keeps the board's spacing above it at every setting.
    val gap = TvLivePreviewGap * scale * LocalDensity.current.fontScale
    // The action sits at the board's y, and below the summary when the display text size makes the summary taller than that.
    Column(Modifier.offset(96.dp * scale, TvLivePreviewTop * scale).width(820.dp * scale).onSizeChanged { onHeight(it.height) },
        verticalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.fillMaxWidth().heightIn(min = TvLivePreviewActionTop * scale - TvLivePreviewTop * scale - gap),
            verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                if (program == null || liveFraming(program, now) == LiveFraming.OnAir)
                    LiveBadge(scale = scale, television = true)
                Text(listOfNotNull(channel.number, channel.card.title).joinToString(" · "), fontSize = (22 * scale).sp,
                    lineHeight = (26 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
            }
            (program?.title ?: channel.card.subtitle)?.let {
                Text(it, fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp,
                    letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            program?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    Text("${liveTime(it.start)} – ${liveTime(it.end, true)}${liveRangeStatus(it, now)}", fontSize = (24 * scale).sp,
                        lineHeight = (30 * scale).sp, color = Palette.body, fontWeight = FontWeight.Medium)
                    channel.card.badges.withoutLiveTag().forEach { TvMediaBadge(it, scale) }
                }
                it.description.providerText()?.let { description -> Text(description, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp,
                    color = Palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            failure?.let { Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, color = Palette.danger) }
        }
        if (favorite != null) TvAction(Modifier.heightIn(min = 52.dp * scale).focusProperties { if (down != null) this.down = down }, scale,
            requester = favoriteFocus, activate = if (failure != null) ({ mutations.retry(target) }) else favorite, contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
            when {
                pending -> { MutationSpinner(24.dp * scale, scale = scale, color = it); Spacer(Modifier.width(10.dp * scale)); Text("Saving…", color = it, fontSize = (22 * scale).sp) }
                failure != null -> { Icon(Lucide.RotateCcw, null, Modifier.size(24.dp * scale), tint = it); Spacer(Modifier.width(10.dp * scale)); Text("Retry", color = it, fontSize = (22 * scale).sp) }
                else -> { FavouriteStar(channel.favorite, 24.dp * scale, it); Spacer(Modifier.width(10.dp * scale))
                    Text(favouriteActionLabel(channel.favorite), color = it, fontSize = (22 * scale).sp) }
            }
        }
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp * scale)
    Box(Modifier.offset(1184.dp * scale, TvLivePreviewTop * scale).size(640.dp * scale, 360.dp * scale)
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
