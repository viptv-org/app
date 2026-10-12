package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.player.PlayerCommand
import org.viptv.app.player.playbackEndTime
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun BoxScope.TvPlayerControls(state: TvPlayerState, title: Card?, extras: TvPlaybackExtras, scale: Float,
    timeline: FocusRequester, audio: FocusRequester, toggle: () -> Unit, previewCommand: ((PlayerCommand) -> Unit)?,
    chooseSources: (() -> Unit)?, playEpisode: ((Card) -> Unit)?) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f),
        0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
    Column(Modifier.align(Alignment.TopStart).padding(start = 96.dp * scale, top = 64.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        Text(title?.title ?: "Playback", fontSize = (30 * scale).sp, lineHeight = (38 * scale).sp, fontWeight = FontWeight.Bold)
        title?.subtitle?.let { Text(it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary) }
    }
    (extras.endTime ?: state.duration?.let { playbackEndTime((it - state.position).coerceAtLeast(0)) })?.let { Text("Ends at $it", Modifier.align(Alignment.TopEnd).padding(end = 96.dp * scale, top = 72.dp * scale),
        fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary) }
    if (state.scrub == null) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 96.dp * scale, end = 96.dp * scale, bottom = 178.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        if (extras.episodes.isNotEmpty() && playEpisode != null) TvPlayerPill("Episodes", Lucide.List, scale, timeline) { state.showEpisodes = true }
        TvPlayerPill("Audio & Subtitles", Lucide.Captions, scale, timeline, audio) { state.showTracks = true; state.interaction++ }
        if (chooseSources != null) TvPlayerPill("Source" + (extras.sourceQuality?.let { " · $it" } ?: ""), Lucide.SlidersHorizontal, scale, timeline, activate = chooseSources)
        Spacer(Modifier.weight(1f))
        if (extras.nextEpisode != null && playEpisode != null) TvPlayerPill("Next episode", Lucide.SkipForward, scale, timeline) { state.showNext = true }
    }
    TvPlaybackTimeline(state, extras, scale, timeline, audio, previewCommand, title?.identity?.kind in listOf("channel", "tv") && state.duration == null)
    state.controlError?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(top = 170.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
}

@Composable
private fun TvPlayerPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, scale: Float,
    down: FocusRequester, requester: FocusRequester? = null, activate: () -> Unit) {
    TvAction(Modifier.height(56.dp * scale).focusProperties { this.down = down }, scale, requester = requester, activate = activate,
        cornerRadius = 28.dp * scale, contentPadding = PaddingValues(horizontal = 22.dp * scale)) {
        Icon(icon, null, Modifier.size(22.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(label, color = it, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}
