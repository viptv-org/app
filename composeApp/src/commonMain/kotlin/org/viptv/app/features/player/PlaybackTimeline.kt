package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.player.BufferedRange
import org.viptv.app.theme.Palette

private val SeekPreviewInset = 6.dp
private val SeekPreviewGap = 6.dp
private val SeekPreviewText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

@Composable
internal fun PlaybackTimeline(position: Long, duration: Long?, seek: (Long) -> Unit, remaining: Boolean = false,
    skips: List<TvSkipRange> = emptyList(), buffered: List<BufferedRange> = emptyList()) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    var hover by remember { mutableStateOf<Float?>(null) }
    val length = duration?.takeIf { it > 0 }
    fun at(fraction: Float?) = fraction?.let { value -> length?.let { (value.toDouble() * it).toLong() } }
    val target = at(scrub)
    val preview = at(scrub ?: hover)
    val shown = target ?: position
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(playbackTime(shown), fontSize = 12.sp, color = Palette.text)
        BoxWithConstraints(Modifier.weight(1f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.lastOrNull() ?: continue
                        when {
                            event.type == PointerEventType.Exit -> hover = null
                            change.pressed -> hover = null
                            else -> hover = fractionAt(change.position, size.width.toFloat(), SeekPreviewInset.toPx())
                        }
                    }
                }
            }) {
            PlaybackSlider(value = scrub ?: if (length != null) (position.toDouble() / length).toFloat().coerceIn(0f, 1f) else 0f,
                change = { scrub = it }, modifier = Modifier.fillMaxWidth(), enabled = length != null,
                finish = { scrub?.let { value -> if (length != null) seek((value * length).toLong()) }; scrub = null },
                accent = Palette.accent, segments = skips.timelineSegments(length), buffered = buffered, duration = length)
            if (preview != null) SeekPreviewBubble(playbackTime(preview), scrub ?: hover ?: 0f, maxWidth)
        }
        Text(length?.let { if (remaining) "-" + playbackTime((it - shown).coerceAtLeast(0)) else playbackTime(it) } ?: "--:--", fontSize = 12.sp, color = Palette.secondary)
    }
}

private fun fractionAt(position: Offset, width: Float, inset: Float): Float =
    ((position.x - inset) / (width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)

@Composable
private fun BoxScope.SeekPreviewBubble(text: String, fraction: Float, trackWidth: Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val size = measurer.measure(text, SeekPreviewText).size
    val chipWidth = with(density) { size.width.toDp() } + 16.dp
    val chipHeight = with(density) { size.height.toDp() } + 8.dp
    val x = (SeekPreviewInset + (trackWidth - SeekPreviewInset * 2) * fraction - chipWidth / 2)
        .coerceIn(0.dp, (trackWidth - chipWidth).coerceAtLeast(0.dp))
    Box(Modifier.align(Alignment.CenterStart).offset(x = x, y = -(chipHeight / 2 + SeekPreviewGap + PlaybackTrackHeight / 2))
        .clip(RoundedCornerShape(6.dp)).background(Palette.raised, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = SeekPreviewText, color = Palette.text, maxLines = 1)
    }
}

internal fun playbackTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun playbackSpeed(value: Double) = value.toString().removeSuffix(".0") + "×"