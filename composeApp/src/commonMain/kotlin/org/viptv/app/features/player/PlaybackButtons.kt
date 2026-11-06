package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.viptv.app.components.pointerToggleable
import org.viptv.app.components.pressScale

internal val CompactButtonsBelow = 1000.dp
private val SourceLabelWidth = 200.dp

@Composable
internal fun PlaybackButtons(paused: Boolean, toggle: () -> Unit, skip: (Long) -> Unit, volume: Float,
    setVolume: (Float) -> Unit, speed: Double, setSpeed: (Double) -> Unit, close: () -> Unit, tracks: () -> Unit, seekEnabled: Boolean = true,
    streams: (() -> Unit)? = null, streamLabel: String? = null,
    next: (() -> Unit)? = null, nextEnabled: Boolean = false,
    episodes: (() -> Unit)? = null, episodesEnabled: Boolean = false,
    fill: Boolean = false, toggleFill: () -> Unit = {},
    fullscreen: Boolean = false, toggleFullscreen: (() -> Unit)? = null) {
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
    val episodesIcon: @Composable () -> Unit = {
        episodes?.let { action -> IconButton(onClick = action, enabled = episodesEnabled, pressedScale = Motion.control) { Icon(Lucide.List, "Episodes", Modifier.size(22.dp)) } }
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
    val fullscreenAction: @Composable () -> Unit = {
        toggleFullscreen?.let { PlaybackFullscreen(fullscreen, it) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < CompactButtonsBelow) Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                skipBack(); playPause(); skipForward(); nextAction(); muteAction(); closeAction()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episodesIcon()
                if (streams != null) streamsAction(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                tracksAction(); speedAction(); PlaybackFitFill(fill, toggleFill)
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            skipBack(); playPause(); skipForward(); nextAction()
            Spacer(Modifier.weight(1f))
            episodesAction(); streamsAction(Modifier); tracksAction(); muteAction()
            PlaybackSlider(volume, setVolume, Modifier.width(90.dp), accent = Palette.text, label = "Volume")
            speedAction(); PlaybackFitFill(fill, toggleFill); fullscreenAction(); closeAction()
        }
    }
}

@Composable
internal fun PlaybackFullscreen(fullscreen: Boolean, toggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (fullscreen) Palette.accent.copy(alpha = 0.16f) else Color.Transparent)
            .pressScale(interaction, Motion.control)
            .pointerToggleable(fullscreen, interaction, null, shape = CircleShape, onValueChange = { toggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (fullscreen) Lucide.Minimize else Lucide.Maximize, if (fullscreen) "Exit fullscreen" else "Enter fullscreen",
            Modifier.size(22.dp), tint = if (fullscreen) Palette.accent else Palette.text)
    }
}

@Composable
internal fun PlaybackFitFill(fill: Boolean, toggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (fill) Palette.accent.copy(alpha = 0.16f) else Color.Transparent)
            .pressScale(interaction, Motion.control)
            .pointerToggleable(fill, interaction, null, shape = CircleShape, onValueChange = { toggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (fill) Lucide.Minimize2 else Lucide.Maximize2, if (fill) "Fit video" else "Fill video",
            Modifier.size(22.dp), tint = if (fill) Palette.accent else Palette.text)
    }
}