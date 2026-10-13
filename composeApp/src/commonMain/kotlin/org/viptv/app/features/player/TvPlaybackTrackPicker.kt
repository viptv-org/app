package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberTvDrawerMotion
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

@Composable
internal fun TvPlaybackTrackPicker(tracks: PlaybackTracks, size: Float, scale: Float, send: (PlayerCommand) -> Unit,
    close: () -> Unit, initialSubtitle: Int? = null) {
    val initial = remember { FocusRequester() }
    val subtitleFocus = tracks.subtitles.find { it.id == initialSubtitle } ?: tracks.subtitles.find { it.selected }
    val preferAudio = tracks.subtitles.isEmpty() && tracks.audio.isNotEmpty()
    val audioFocus = tracks.audio.find { it.selected } ?: tracks.audio.firstOrNull()
    val motion = rememberTvDrawerMotion(close)
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(540.dp * scale)
                .graphicsLayer { translationY = this.size.height * (1f - motion.progress.value) }
                .clip(RoundedCornerShape(topStart = 24.dp * scale, topEnd = 24.dp * scale)).background(Color(0xFF191818))
                .padding(start = 76.dp * scale, top = 48.dp * scale), horizontalArrangement = Arrangement.spacedBy(80.dp * scale)) {
                TvTrackColumn("Audio", Modifier.width(520.dp * scale), scale) {
                    if (tracks.audio.isEmpty()) item { Text("No audio tracks available", color = Palette.secondary, fontSize = (24 * scale).sp) }
                    items(tracks.audio, key = { it.id }) { track -> TvTrackRow(track.label, track.selected, scale,
                        if (preferAudio && track.id == audioFocus?.id) initial else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, track.id)) } }
                }
                TvTrackColumn("Subtitles", Modifier.width(520.dp * scale), scale) {
                    item { TvTrackRow("Off", tracks.subtitles.none { it.selected }, scale,
                        if (!preferAudio && subtitleFocus == null) initial else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, null)) } }
                    items(tracks.subtitles, key = { it.id }) { track -> TvTrackRow(track.label, track.selected, scale,
                        if (!preferAudio && track.id == subtitleFocus?.id) initial else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, track.id)) } }
                }
                TvTrackColumn("Subtitle size", Modifier.width(520.dp * scale), scale) {
                    items(listOf("Small" to 0.8f, "Medium" to 1f, "Large" to 1.25f, "Extra large" to 1.5f)) { (label, value) ->
                        TvTrackRow(label, size == value, scale, null) { send(PlayerCommand.SubtitleSize(value)) }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}

@Composable
private fun TvTrackColumn(label: String, modifier: Modifier, scale: Float, content: LazyListScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Text(label, Modifier.padding(start = 20.dp * scale), color = Palette.secondary, fontSize = (24 * scale).sp,
            lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp * scale), content = content)
    }
}

@Composable
private fun TvTrackRow(label: String, selected: Boolean, scale: Float, requester: FocusRequester?, choose: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(64.dp * scale), scale, requester = requester, activate = choose,
        background = Color.Transparent, arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 20.dp * scale)) { tint ->
        Box(Modifier.width(40.dp * scale)) { if (selected) Icon(Lucide.Check, null, Modifier.size(24.dp * scale), tint) }
        Text(label, color = if (!selected && tint == Palette.text) Color(0xFFDAD8D3) else tint,
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}
