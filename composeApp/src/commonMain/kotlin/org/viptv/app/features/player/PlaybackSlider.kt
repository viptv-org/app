package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.viptv.app.player.BufferedRange
import org.viptv.app.theme.Palette

private val TrackHeight = 3.dp
private val ThumbSize = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSlider(value: Float, change: (Float) -> Unit, modifier: Modifier, enabled: Boolean = true,
    finish: (() -> Unit)? = null, accent: Color = Palette.accent, label: String = "Playback position",
    segments: List<TimelineSegment> = emptyList(), buffered: List<BufferedRange> = emptyList(), duration: Long? = null) {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    val description = if (segments.isEmpty()) label else "$label. " + segments.joinToString(". ") { it.description }
    val bands = bufferedFractions(buffered, duration)
    Slider(value, change, modifier.semantics { contentDescription = description }, enabled = enabled, onValueChangeFinished = finish, colors = colors,
        thumb = { Box(Modifier.size(ThumbSize).background(Color.White, CircleShape)) },
        track = { state ->
            Box(Modifier.fillMaxWidth().height(TrackHeight)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Buffered media sits under the played fill and under the skip markers.
                    if (bands.isEmpty() && segments.isEmpty()) SliderDefaults.Track(state, Modifier.fillMaxSize(), colors = colors, enabled = enabled,
                        thumbTrackGapSize = 0.dp, drawStopIndicator = null)
                    else {
                        TimelineBarSpan(0f, 1f, colors.inactiveTrackColor, RoundedCornerShape(TrackHeight / 2))
                        bands.forEach { (start, end) -> TimelineBarSpan(start, end, Color.White.copy(alpha = 0.5f)) }
                        TimelineBarSpan(0f, state.coercedValueAsFraction, accent)
                        segments.forEach { segment -> TimelineBarSpan(segment.startFraction, segment.endFraction, segment.color, RoundedCornerShape(2.dp)) }
                    }
                }
            }
        })
}
