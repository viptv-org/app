package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.viptv.app.player.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvPlayerScreen(input: PlaybackInput?, paused: Boolean, error: String?, toggle: () -> Unit,
    observe: (PlayerFact) -> Unit, close: () -> Unit, title: Card?, extras: TvPlaybackExtras,
    chooseSources: (() -> Unit)?, playEpisode: ((Card) -> Unit)?, retry: (() -> Unit)?, backChanged: ((() -> Unit)?) -> Unit,
    previewFrame: String? = null, scene: TvPlayerScene = TvPlayerScene.Controls, initialFacts: List<PlayerFact> = emptyList(),
    externalFacts: Flow<PlayerFact> = emptyFlow(), previewCommand: ((PlayerCommand) -> Unit)? = null, trackFocus: Int? = null) {
    val state = remember(input) { TvPlayerState(input, initialFacts, scene) }
    val root = remember { FocusRequester() }; val timeline = remember { FocusRequester() }; val audio = remember { FocusRequester() }
    val latestClose by rememberUpdatedState(close)
    val scope = rememberCoroutineScope()
    val back = {
        if (state.back(latestClose)) scope.launch {
            withFrameNanos { }
            if (state.visible && !state.showTracks && !state.showNext && !state.showEpisodes) {
                if ((state.duration ?: 0) > 0) timeline.requestFocus() else audio.requestFocus()
            } else if (!state.showTracks && !state.showNext && !state.showEpisodes) root.requestFocus()
        }
        Unit
    }
    DisposableEffect(state) { backChanged(back); onDispose { backChanged(null) } }
    LaunchedEffect(externalFacts, state) { externalFacts.collect(state::observe) }
    LaunchedEffect(state.visible, paused, state.interaction, state.buffering, state.showTracks, state.showNext, state.showEpisodes, state.failed, state.scrub) {
        if (state.visible && !paused && !state.buffering && !state.showTracks && !state.showNext && !state.showEpisodes && !state.failed && state.scrub == null && input != null) { delay(5000); state.visible = false }
    }
    val skipActive = extras.skip?.let { state.position in it.startMillis until it.endMillis } == true && !state.visible
    LaunchedEffect(state.visible, state.buffering, state.failed, state.showTracks, state.showNext, state.showEpisodes, state.duration, state.focusAudio, skipActive) {
        if (state.visible && !state.buffering && !state.failed && !state.showTracks && !state.showNext && !state.showEpisodes) {
            if (!state.focusAudio && (state.duration ?: 0) > 0) timeline.requestFocus() else audio.requestFocus()
        }
        else if (!state.showTracks && !state.showNext && !state.failed && !state.showEpisodes && !skipActive) root.requestFocus()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(if (state.showNext) Palette.background else Color.Black).focusRequester(root).onFocusChanged { state.rootFocused = it.isFocused }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) state.interaction++
        if (event.type == KeyEventType.KeyUp && event.key in listOf(Key.DirectionLeft, Key.DirectionRight) &&
            (state.timelineFocused || state.scrub != null)) true
        else if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.Escape -> { back(); true }
            Key.Spacebar, Key.MediaPlayPause -> { if (!state.failed) { state.reveal(); toggle() }; true }
            Key.DirectionLeft, Key.DirectionRight -> if (!state.failed && !state.showNext && (state.timelineFocused || state.rootFocused || !state.visible)) {
                state.step(if (event.key == Key.DirectionLeft) -1 else 1); true
            } else false
            Key.DirectionCenter, Key.Enter -> if (!state.failed && !state.showNext && (state.timelineFocused || state.rootFocused)) {
                if (state.scrub != null) state.commit(previewCommand) else { state.reveal(); toggle() }; true
            } else false
            Key.DirectionDown -> if (state.timelineFocused) { state.scrub = null; state.visible = false; root.requestFocus(); true }
                else if (!state.visible && !state.failed && !state.buffering && !state.showNext) { state.reveal(); true } else false
            Key.DirectionUp -> if (!state.failed && !state.buffering && !state.showNext && !state.showTracks && !state.showEpisodes && (state.timelineFocused || state.rootFocused)) {
                state.scrub = null; state.reveal(audio = true); true
            } else false
            else -> if (event.key != Key.Back && !state.visible && !state.failed && !state.buffering && !state.showNext && !state.showTracks && !state.showEpisodes) {
                state.reveal(); false
            } else false
        }
    }.focusable()) {
        val scale = maxWidth / 1920.dp
        val video = rememberTvVideoFrame(state.showNext, scale)
        if (input == null && previewFrame != null) AsyncImage(previewFrame, null, video, contentScale = ContentScale.Crop)
        if (input != null) PlayerSurface(input, paused, video, { state.observe(it); observe(it) }, state.commands)
        if (state.showNext) Box(video.background(Color.Black.copy(alpha = 0.25f)))
        when {
            state.failed -> TvPlayerFailure(extras, state.position, error, scale, retry, chooseSources, close)
            state.buffering -> TvPlayerBuffer(title, extras, scale)
            state.showNext && extras.nextEpisode != null -> TvNextEpisode(title, extras.nextEpisode, scale, { playEpisode?.invoke(extras.nextEpisode) }, { state.showNext = false; state.visible = false }, extras.creditsLabel)
            else -> {
                TvControlsVisibility(state.visible && !state.showTracks && !state.showEpisodes) {
                    TvPlayerControls(state, title, extras, scale, timeline, audio,
                        toggle = toggle, previewCommand = previewCommand, chooseSources = chooseSources, playEpisode = playEpisode)
                }
                val skip = extras.skip
                if (!state.visible && skip != null && state.position in skip.startMillis until skip.endMillis) TvSkipPrompt(skip, scale) { state.send(PlayerCommand.Seek(skip.endMillis), previewCommand) }
            }
        }
        if (state.showTracks) TvPlaybackTrackPicker(state.tracks, state.subtitleSize, scale,
            { state.send(it, previewCommand) }, { state.showTracks = false; state.reveal() }, trackFocus)
        if (state.showEpisodes) TvPlayerEpisodePicker(extras.episodes, scale, { state.showEpisodes = false; playEpisode?.invoke(it) }, { state.showEpisodes = false })
    }
}
