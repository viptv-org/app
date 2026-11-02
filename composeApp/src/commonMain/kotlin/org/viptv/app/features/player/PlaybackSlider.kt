package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.viptv.app.player.BufferedRange
import org.viptv.app.theme.Palette

private val ThumbSize = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSlider(value: Float, change: (Float) -> Unit, modifier: Modifier, enabled: Boolean = true,
    finish: (() -> Unit)? = null, accent: Color = Palette.accent, label: String = "Playback position",
    buffered: List<BufferedRange> = emptyList(), duration: Long? = null) {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    val band = bufferedFractions(buffered, duration)
    Slider(value, change, modifier.semantics { contentDescription = label }, enabled = enabled, onValueChangeFinished = finish, colors = colors,
        thumb = { Box(Modifier.size(ThumbSize).background(Color.White, CircleShape)) },
        track = { state ->
            if (band.isEmpty()) SliderDefaults.Track(state, Modifier.height(3.dp), colors = colors,
                enabled = enabled, thumbTrackGapSize = 0.dp, drawStopIndicator = null)
            // Buffered media sits under the played fill, on the bar the thumb travels across.
            else BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp))
                .background(colors.inactiveTrackColor)) {
                BufferedSegments(band, Color.White.copy(alpha = 0.5f))
                Box(Modifier.fillMaxWidth(state.coercedValueAsFraction.coerceIn(0f, 1f)).fillMaxHeight().background(accent))
            }
        })
}
