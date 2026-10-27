package org.viptv.app.features.player

import org.viptv.app.components.FocusLazyColumn as LazyColumn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.viptv.app.components.tvFocusOutline
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackTrackPicker(tracks: PlaybackTracks, select: (PlayerCommand.SelectTrack) -> Unit,
    close: () -> Unit, phone: Boolean, size: Float, setSize: (Float) -> Unit, insets: WindowInsets,
    /** The full Subtitles & audio settings; the menu ends with a row that opens them (app#44). */
    openSettings: (() -> Unit)? = null) {
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(onClick = close))
        val panel = if (phone) Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.56f)
            else Modifier.align(Alignment.BottomEnd).padding(end = 150.dp, bottom = 76.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 300.dp)
        Column(panel.clip(RoundedCornerShape(if (phone) 0.dp else 12.dp)).background(Palette.surface)
            .border(1.dp, Palette.raised, RoundedCornerShape(if (phone) 0.dp else 12.dp))
            .then(if (phone) Modifier.windowInsetsPadding(insets.endSheet()) else Modifier)
            .padding(if (phone) 20.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // NAV-07: the panel is dismissible on every size, not only on phone.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Audio & subtitles", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(close, Modifier.size(if (phone) 32.dp else 28.dp)) {
                    Icon(Lucide.X, "Close track selection", Modifier.size(if (phone) 18.dp else 16.dp))
                }
            }
            // The track lists scroll; the settings row below them always stays in view.
            Row(Modifier.fillMaxWidth().weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackColumn("Audio", tracks.audio, false, Modifier.weight(1f)) { select(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, it)) }
                TrackColumn("Subtitles", tracks.subtitles, true, Modifier.weight(1f)) { select(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, it)) }
                if (!phone && tracks.subtitles.isNotEmpty()) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Size", Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = Palette.secondary)
                    listOf("Small" to 0.8f, "Medium" to 1f, "Large" to 1.25f).forEach { (label, value) ->
                        PlaybackTrackChoice(label, size == value) { setSize(value) }
                    }
                }
            }
            if (openSettings != null) PlaybackSettingsLink(openSettings)
        }
    }
    }
}

/** Language preferences, subtitle size, colours, background and foreign-only mode live in Settings. */
@Composable
private fun PlaybackSettingsLink(open: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    HorizontalDivider(color = Palette.raised)
    Row(Modifier.fillMaxWidth().height(40.dp).onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 8.dp, width = 2.dp).clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClick = open).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Lucide.Settings, null, Modifier.size(16.dp))
        Text(PLAYER_SETTINGS_LINK, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.secondary)
    }
}

/** The Audio & subtitles menu's last row on every player. */
internal const val PLAYER_SETTINGS_LINK = "Subtitles & audio settings"

@Composable
private fun TrackColumn(label: String, tracks: List<PlaybackTrack>, subtitles: Boolean,
    modifier: Modifier, select: (Int?) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = Palette.secondary)
        if (tracks.isEmpty()) Text(if (subtitles) "No subtitle tracks available" else "No audio tracks available",
            Modifier.padding(12.dp), fontSize = 12.sp, color = Palette.secondary)
        LazyColumn {
            if (subtitles && tracks.isNotEmpty()) item { PlaybackTrackChoice("Off", tracks.none { it.selected }) { select(null) } }
            items(tracks, key = { it.id }) { track -> PlaybackTrackChoice(track.label, track.selected) { select(track.id) } }
        }
    }
}
