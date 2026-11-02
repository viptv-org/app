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
import org.viptv.app.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSlider(value: Float, change: (Float) -> Unit, modifier: Modifier, enabled: Boolean = true,
    finish: (() -> Unit)? = null, accent: Color = Palette.accent, label: String = "Playback position",
    segments: List<TimelineSegment> = emptyList()) {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    val description = if (segments.isEmpty()) label else "$label. " + segments.joinToString(". ") { it.description }
    val thumbSize = 12.dp
    Slider(value, change, modifier.semantics { contentDescription = description }, enabled = enabled, onValueChangeFinished = finish, colors = colors,
        thumb = { Box(Modifier.size(thumbSize).background(Color.White, CircleShape)) },
        track = { state ->
            Box(Modifier.height(3.dp)) {
                SliderDefaults.Track(state, Modifier.fillMaxSize(), colors = colors, enabled = enabled,
                    thumbTrackGapSize = 0.dp, drawStopIndicator = null)
                if (segments.isNotEmpty()) {
                    val inset = thumbSize / 2
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val span = maxWidth - thumbSize
                        segments.forEach { segment ->
                            Box(Modifier.offset(x = inset + span * segment.startFraction)
                                .width(span * (segment.endFraction - segment.startFraction).coerceAtLeast(0f))
                                .fillMaxHeight().background(segment.color, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        })
}
