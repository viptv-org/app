package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.components.Motion
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton
import org.viptv.app.components.DropdownMenuItem

private val CompactButtonsBelow = 1000.dp
private val SourceLabelWidth = 200.dp

@Composable
internal fun PlaybackButtons(paused: Boolean, toggle: () -> Unit, skip: (Long) -> Unit, volume: Float,
    setVolume: (Float) -> Unit, speed: Double, setSpeed: (Double) -> Unit, close: () -> Unit, tracks: () -> Unit, seekEnabled: Boolean = true,
    streams: (() -> Unit)? = null, streamLabel: String? = null,
    next: (() -> Unit)? = null, nextEnabled: Boolean = false,
    episodes: (() -> Unit)? = null, episodesEnabled: Boolean = false) {
    var speeds by remember { mutableStateOf(false) }
    val playPause: @Composable () -> Unit = {
        IconButton(onClick = toggle, pressedScale = Motion.control) {
            if (paused) Icon(painterResource(Res.drawable.ic_play_zip), "Play", Modifier.size(24.dp))
            else Icon(Lucide.Pause, "Pause", Modifier.size(24.dp))
        }
    }
    val skipBack: @Composable () -> Unit = {
        IconButton(onClick = { skip(-10000) }, enabled = seekEnabled, pressedScale = Motion.control) { PlaybackSkipIcon(false, 22.dp) }
    }
    val skipForward: @Composable () -> Unit = {
        IconButton(onClick = { skip(10000) }, enabled = seekEnabled, pressedScale = Motion.control) { PlaybackSkipIcon(true, 22.dp) }
    }
    val nextAction: @Composable () -> Unit = {
        next?.let { IconButton(onClick = it, enabled = nextEnabled, pressedScale = Motion.control) { Icon(Lucide.SkipForward, "Next episode", Modifier.size(22.dp)) } }
    }
    val muteAction: @Composable () -> Unit = {
        IconButton(onClick = { setVolume(if (volume == 0f) 1f else 0f) }, pressedScale = Motion.control) {
            Icon(if (volume == 0f) Lucide.VolumeX else Lucide.Volume2, "Mute or unmute", Modifier.size(22.dp))
        }
    }
    val episodesAction: @Composable () -> Unit = {
        episodes?.let { action -> TextButton(action, enabled = episodesEnabled, pressedScale = Motion.control) {
            Icon(Lucide.List, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Episodes")
        } }
    }
    val streamsAction: @Composable (Modifier) -> Unit = { modifier ->
        streams?.let { action -> TextButton(onClick = action, modifier = modifier.widthIn(max = SourceLabelWidth), pressedScale = Motion.control) {
            Icon(Lucide.Layers, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(streamLabel ?: "Streams", maxLines = 1, overflow = TextOverflow.Ellipsis)
        } }
    }
    val tracksAction: @Composable () -> Unit = {
        IconButton(onClick = tracks, pressedScale = Motion.control) { Icon(Lucide.Captions, "Audio and subtitles", Modifier.size(22.dp)) }
    }
    val speedAction: @Composable () -> Unit = {
        Box {
            TextButton(onClick = { speeds = true }, pressedScale = Motion.control) { Text(playbackSpeed(speed)) }
            DropdownMenu(speeds, onDismissRequest = { speeds = false }) {
                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { choice ->
                    DropdownMenuItem(text = { Text(playbackSpeed(choice)) }, onClick = { setSpeed(choice); speeds = false })
                }
            }
        }
    }
    val closeAction: @Composable () -> Unit = {
        IconButton(onClick = close, pressedScale = Motion.control) { Icon(Lucide.X, "Close playback", Modifier.size(22.dp)) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < CompactButtonsBelow) Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                playPause(); skipBack(); skipForward(); nextAction(); Spacer(Modifier.weight(1f)); closeAction()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episodesAction()
                if (streams != null) streamsAction(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                tracksAction(); speedAction()
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            playPause(); skipBack(); skipForward(); nextAction(); muteAction()
            PlaybackSlider(volume, setVolume, Modifier.width(90.dp), accent = Palette.text, label = "Volume")
            Spacer(Modifier.weight(1f))
            episodesAction(); streamsAction(Modifier); tracksAction(); speedAction(); closeAction()
        }
    }
}
