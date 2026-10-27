package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton
import org.viptv.app.components.DropdownMenuItem

@Composable
internal fun PhoneTransport(paused: Boolean, toggle: () -> Unit, skip: (Long) -> Unit, seekEnabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        IconButton(enabled = seekEnabled, onClick = { skip(-10000) }, modifier = Modifier.size(56.dp)) {
            PlaybackSkipIcon(false, 34.dp)
        }
        FilledIconButton(onClick = toggle, modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised.copy(alpha = 0.8f), contentColor = Palette.text)) {
            if (paused) Icon(painterResource(Res.drawable.ic_play_zip), "Play", Modifier.size(28.dp))
            else Icon(Lucide.Pause, "Pause", Modifier.size(28.dp))
        }
        IconButton(enabled = seekEnabled, onClick = { skip(10000) }, modifier = Modifier.size(56.dp)) {
            PlaybackSkipIcon(true, 34.dp)
        }
    }
}

@Composable
internal fun PhonePlaybackToolbar(speed: Double, setSpeed: (Double) -> Unit, lock: () -> Unit, tracks: () -> Unit,
    episodes: (() -> Unit)? = null, episodesEnabled: Boolean = false, next: (() -> Unit)? = null,
    nextEnabled: Boolean = false, episodesFocus: FocusRequester? = null, nextFocus: FocusRequester? = null) {
    var speeds by remember { mutableStateOf(false) }
    val episodeAction: @Composable () -> Unit = {
        if (episodes != null) TextButton(episodes, enabled = episodesEnabled,
            modifier = episodesFocus?.let { Modifier.focusRequester(it) } ?: Modifier) {
            Icon(Lucide.List, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Episodes")
        }
    }
    val tracksAction: @Composable () -> Unit = {
        TextButton(onClick = tracks) {
            Icon(Lucide.Captions, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Audio & subtitles")
        }
    }
    val speedAction: @Composable () -> Unit = {
        Box {
            TextButton(onClick = { speeds = true }) {
                Icon(Lucide.Gauge, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(playbackSpeed(speed))
            }
            DropdownMenu(speeds, onDismissRequest = { speeds = false }) {
                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { value ->
                    DropdownMenuItem(text = { Text(playbackSpeed(value)) }, onClick = { setSpeed(value); speeds = false })
                }
            }
        }
    }
    val lockAction: @Composable () -> Unit = {
        TextButton(onClick = lock) {
            Icon(Lucide.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Lock")
        }
    }
    val nextAction: @Composable () -> Unit = {
        if (next != null) TextButton(next, enabled = nextEnabled,
            modifier = nextFocus?.let { Modifier.focusRequester(it) } ?: Modifier) {
            Icon(Lucide.SkipForward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Next episode")
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) Column {
            Row { episodeAction(); tracksAction() }
            Row(Modifier.fillMaxWidth()) { speedAction(); lockAction(); Spacer(Modifier.weight(1f)); nextAction() }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            episodeAction(); tracksAction(); speedAction(); lockAction(); Spacer(Modifier.weight(1f)); nextAction()
        }
    }
}
