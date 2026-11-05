package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.player.PlayerCommand
import org.viptv.app.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.TvPlaybackTimeline(state: TvPlayerState, extras: TvPlaybackExtras, scale: Float,
    requester: FocusRequester, up: FocusRequester, previewCommand: ((PlayerCommand) -> Unit)?, live: Boolean = false) {
    val length = state.duration?.takeIf { it > 0 && !live }
    val segments = extras.skips.timelineSegments(length)
    val positionDescription = if (segments.isEmpty()) "Position" else "Position. " + segments.joinToString(". ") { it.description }
    val actual = if (length != null) (state.position.toDouble() / length).toFloat().coerceIn(0f, 1f) else 0f
    val current = if (length != null) ((state.scrub ?: state.position).toDouble() / length).toFloat().coerceIn(0f, 1f) else 0f
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter).padding(horizontal = 96.dp * scale, vertical = 116.dp * scale).fillMaxWidth().height(44.dp * scale)
        .tvFocusOutline(state.timelineFocused, 22.dp * scale, scale)) {
        Slider(current, { if (length != null) { state.scrub = (it * length).toLong(); state.reveal() } },
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(maxWidth + 44.dp * scale).offset(x = (-22).dp * scale).height(44.dp * scale)
                .focusRequester(requester).onFocusChanged { state.timelineFocused = it.isFocused }.focusProperties { this.up = up }
                .semantics { contentDescription = positionDescription; if (length != null) setProgress { target ->
                    state.send(PlayerCommand.Seek((target.coerceIn(0f, 1f) * length).toLong()), previewCommand); true } },
            enabled = length != null, onValueChangeFinished = { state.commit(previewCommand) },
            thumb = { Box(Modifier.size(44.dp * scale).background(ColorWhite.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(28.dp * scale).background(ColorWhite, CircleShape))
            } },
            track = { BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp * scale).clip(RoundedCornerShape(3.dp * scale)).background(ColorWhite.copy(alpha = .22f))) {
                bufferedFractions(state.buffered, length).forEach { (start, end) -> TimelineBarSpan(start, end, ColorWhite.copy(alpha = .5f)) }
                segments.forEach { segment -> TimelineBarSpan(segment.startFraction, segment.endFraction, segment.color) }
                TimelineBarSpan(0f, actual, Palette.accent)
                if (state.scrub != null) TimelineBarSpan(minOf(actual, current), maxOf(actual, current), ColorWhite.copy(alpha = .55f))
            } })
        state.scrub?.let { point ->
            val image = extras.thumbnails.firstOrNull { point in it.startMillis until it.endMillis }?.image
            TvSeekPreview(point, image, current, maxWidth, scale)
        }
    }
    Row(Modifier.align(Alignment.BottomCenter).padding(start = 96.dp * scale, end = 96.dp * scale, bottom = 88.dp * scale).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (live) "LIVE" else playbackTime(state.position), fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold,
            color = if (live) Palette.danger else Palette.text, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
        Text(length?.let { "−" + playbackTime((it - state.position).coerceAtLeast(0)) } ?: "--:--",
            fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
    }
}
private val ColorWhite = androidx.compose.ui.graphics.Color.White
