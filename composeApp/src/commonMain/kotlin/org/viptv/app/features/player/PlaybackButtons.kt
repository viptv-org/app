package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackButtons(paused: Boolean, toggle: () -> Unit, skip: (Long) -> Unit, volume: Float,
    setVolume: (Float) -> Unit, speed: Double, setSpeed: (Double) -> Unit, close: () -> Unit, tracks: () -> Unit, seekEnabled: Boolean = true,
    streams: (() -> Unit)? = null, streamLabel: String? = null) {
    var speeds by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = toggle) {
            if (paused) Icon(painterResource(Res.drawable.ic_play_zip), "Play", Modifier.size(24.dp))
            else Icon(Lucide.Pause, "Pause", Modifier.size(24.dp))
        }
        IconButton(onClick = { skip(-10000) }, enabled = seekEnabled) { PlaybackSkipIcon(false, 22.dp) }
        IconButton(onClick = { skip(10000) }, enabled = seekEnabled) { PlaybackSkipIcon(true, 22.dp) }
        IconButton(onClick = { setVolume(if (volume == 0f) 1f else 0f) }) {
            Icon(if (volume == 0f) Lucide.VolumeX else Lucide.Volume2, "Mute or unmute", Modifier.size(22.dp))
        }
        PlaybackSlider(volume, setVolume, Modifier.width(90.dp), accent = Palette.text, label = "Volume")
        Spacer(Modifier.weight(1f))
        streams?.let { streamsAction ->
            TextButton(onClick = streamsAction) {
                Icon(Lucide.Layers, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(streamLabel ?: "Streams")
            }
        }
        IconButton(onClick = tracks) { Icon(Lucide.Captions, "Audio and subtitles", Modifier.size(22.dp)) }
        Box {
            TextButton(onClick = { speeds = true }) { Text(playbackSpeed(speed)) }
            DropdownMenu(speeds, onDismissRequest = { speeds = false }) {
                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { choice ->
                    DropdownMenuItem(text = { Text(playbackSpeed(choice)) }, onClick = { setSpeed(choice); speeds = false })
                }
            }
        }
        IconButton(onClick = close) { Icon(Lucide.X, "Close playback", Modifier.size(22.dp)) }
    }
}
