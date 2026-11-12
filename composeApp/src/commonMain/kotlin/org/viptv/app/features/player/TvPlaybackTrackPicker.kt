package org.viptv.app.features.player

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.components.rememberTvDrawerMotion
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

@Composable
internal fun TvPlaybackTrackPicker(tracks: PlaybackTracks, size: Float, scale: Float, send: (PlayerCommand) -> Unit,
    close: () -> Unit, initialSubtitle: Int? = null, dismissChanged: ((() -> Unit)?) -> Unit,
    openSettings: (() -> Unit)? = null, pendingSubtitle: SubtitleSelection? = null,
    subtitleError: SubtitleSelection? = null, pendingAudio: Int? = null, audioError: Int? = null, subtitleSync: SubtitleSyncSession? = null) {
    val timing = subtitleSync?.ui?.collectAsState()?.value
    var showTiming by remember(subtitleSync) { mutableStateOf(false) }
    val initial = remember { FocusRequester() }
    val subtitleFocus = tracks.subtitles.find { it.id == initialSubtitle } ?: tracks.subtitles.find { it.selected }
    val preferAudio = tracks.subtitles.isEmpty() && tracks.audio.isNotEmpty()
    val audioFocus = tracks.audio.find { it.selected } ?: tracks.audio.firstOrNull()
    val motion = rememberTvDrawerMotion(close)
    DisposableEffect(motion.dismiss) { dismissChanged(motion.dismiss); onDispose { dismissChanged(null) } }
        Box(Modifier.fillMaxSize().focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
            .semantics { paneTitle = "Audio and subtitles" }) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(540.dp * scale)
                .graphicsLayer { translationY = this.size.height * (1f - motion.progress.value) }
                .clip(RoundedCornerShape(topStart = 24.dp * scale, topEnd = 24.dp * scale))
                .tvBackdropBlur(30.dp * scale).background(Color(0xFF161618).copy(alpha = .9f))
                .padding(start = 76.dp * scale, top = 48.dp * scale, bottom = if (openSettings != null) 28.dp * scale else 0.dp)) {
                if (showTiming && subtitleSync != null) {
                    LazyColumn(Modifier.weight(1f).width(1000.dp * scale).tvRingRoom(tvRingReach(scale), horizontal = false)) {
                        item {
                            SubtitleTimingChoices(subtitleSync, 24 * scale / 14) { label, selected, action ->
                                TvTrackRow(label, selected == true, scale, if (label == "Earlier by 0.5 s") initial else null,
                                    selectionState = selected, choose = action)
                            }
                        }
                    }
                } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(80.dp * scale)) {
                    TvTrackColumn("Audio", Modifier.width(520.dp * scale), scale) {
                        if (tracks.audio.size <= 1) item { Text("Default audio", color = Palette.secondary, fontSize = (24 * scale).sp) }
                        else items(tracks.audio, key = { it.id }) { track ->
                            val pending = pendingAudio == track.id
                            val failed = audioError == track.id
                            TvTrackRow(track.label, (track.selected && pendingAudio == null) || pending, scale,
                            if (preferAudio && track.id == audioFocus?.id) initial else null, pending = pending,
                            error = if (failed) audioTrackErrorCopy else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Audio, track.id)) } }
                    }
                    TvTrackColumn("Subtitles", Modifier.width(520.dp * scale), scale) {
                        item { TvTrackRow("Off", tracks.subtitles.none { it.selected } && pendingSubtitle == null || pendingSubtitle is SubtitleSelection.Off, scale,
                            if (!preferAudio && subtitleFocus == null) initial else null,
                            pending = pendingSubtitle is SubtitleSelection.Off,
                            error = if (subtitleError is SubtitleSelection.Off) subtitleTrackErrorCopy else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, null)) } }
                        items(tracks.subtitles, key = { it.id }) { track ->
                            val pending = (pendingSubtitle as? SubtitleSelection.Track)?.id == track.id
                            val failed = (subtitleError as? SubtitleSelection.Track)?.id == track.id
                            TvTrackRow(track.label, (track.selected && pendingSubtitle == null) || pending, scale,
                            if (!preferAudio && track.id == subtitleFocus?.id) initial else null, pending = pending,
                            error = if (failed) subtitleTrackErrorCopy else null) { send(PlayerCommand.SelectTrack(PlaybackTrackKind.Subtitle, track.id)) } }
                    }
                    TvTrackColumn("Subtitle size", Modifier.width(520.dp * scale), scale) {
                        if (timing?.providerSelected == true) item {
                            TvTrackRow(subtitleTimingLabel(timing), false, scale, null) { showTiming = true }
                        }
                        items(listOf("Small" to 0.8f, "Medium" to 1f, "Large" to 1.25f, "Extra large" to 1.5f)) { (label, value) ->
                            TvTrackRow(label, size == value, scale, null) { send(PlayerCommand.SubtitleSize(value)) }
                        }
                    }
                }
                if (openSettings != null && !showTiming) TvAction(Modifier.width(520.dp * scale).heightIn(min = 64.dp * scale), scale, activate = openSettings,
                    background = Color.Transparent, arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 20.dp * scale)) { tint ->
                    Box(Modifier.width(40.dp * scale)) { Icon(Lucide.Settings, null, Modifier.size(24.dp * scale), tint) }
                    Text(PLAYER_SETTINGS_LINK, Modifier.weight(1f), color = if (tint == Palette.text) Palette.body else tint,
                        fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Medium)
                    Icon(Lucide.ChevronRight, null, Modifier.size(24.dp * scale), tint)
                }
            }
        }
        LaunchedEffect(showTiming) { withFrameNanos { }; initial.requestFocus() }
}

@Composable
private fun TvTrackColumn(label: String, modifier: Modifier, scale: Float, content: LazyListScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Text(label, Modifier.padding(start = 20.dp * scale), color = Palette.secondary, fontSize = (24 * scale).sp,
            lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.tvRingRoom(tvRingReach(scale), horizontal = false), contentPadding = PaddingValues(vertical = tvRingReach(scale)),
            verticalArrangement = Arrangement.spacedBy(6.dp * scale), content = content)
    }
}

@Composable
private fun TvTrackRow(label: String, selected: Boolean, scale: Float, requester: FocusRequester?,
    pending: Boolean = false, error: String? = null, selectionState: Boolean? = null, choose: () -> Unit) {
    val highlighted = selected && !pending
    TvAction(Modifier.fillMaxWidth().height((64 * scale).dp).semantics { selectionState?.let { this.selected = it } }, scale, requester = requester, activate = choose,
        background = Color.Transparent, arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 20.dp * scale)) { tint ->
        Box(Modifier.width(40.dp * scale)) {
            when {
                pending -> CircularProgressIndicator(Modifier.size(22.dp * scale), strokeWidth = 2.dp, color = Palette.text)
                highlighted -> Icon(Lucide.Check, null, Modifier.size(24.dp * scale), tint)
            }
        }
        Text(label, color = if (!highlighted && tint == Palette.text) Color(0xFFDAD8D3) else tint,
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (error != null) {
            Spacer(Modifier.width(16.dp * scale))
            Text(error, color = Palette.danger, fontSize = (18 * scale).sp, lineHeight = (25 * scale).sp, maxLines = 1)
        }
    }
}
