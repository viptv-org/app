package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    finish: (() -> Unit)? = null, accent: Color = Palette.accent, label: String = "Playback position") {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f))
    Slider(value, change, modifier.semantics { contentDescription = label }, enabled = enabled, onValueChangeFinished = finish, colors = colors,
        thumb = { Box(Modifier.size(12.dp).background(Color.White, CircleShape)) },
        track = { state -> SliderDefaults.Track(state, Modifier.height(3.dp), colors = colors,
            enabled = enabled, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
}
