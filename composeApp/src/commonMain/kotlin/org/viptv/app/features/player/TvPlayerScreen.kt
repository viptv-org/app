package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import org.viptv.app.components.TvBackdrop
import org.viptv.app.components.tvBackdropSource

@Composable
internal fun TvPlayerScreen(input: PlaybackInput?, paused: Boolean, error: String?, toggle: () -> Unit,
    observe: (PlayerFact) -> Unit, close: () -> Unit, title: Card?, extras: TvPlaybackExtras,
    chooseSources: (() -> Unit)?, playEpisode: ((Card) -> Unit)?, retry: (() -> Unit)?, backChanged: ((() -> Unit)?) -> Unit,
    previewFrame: String? = null, scene: TvPlayerScene = TvPlayerScene.Controls, initialFacts: List<PlayerFact> = emptyList(),
    externalFacts: Flow<PlayerFact> = emptyFlow(), previewCommand: ((PlayerCommand) -> Unit)? = null, trackFocus: Int? = null,
    playbackKey: Any? = input, trackChosen: (PlaybackTrackKind, String?) -> Unit = { _, _ -> },
    /** What shows where the video does not draw, as on the other players; black on every TV build. */
    canvasColor: Color = Color.Black, openSettings: (() -> Unit)? = null, autoplayNext: Boolean = false, reduceMotion: Boolean = false) {
    val state = remember(playbackKey) { TvPlayerState(input, initialFacts, scene) }
    var retainedInput by remember(playbackKey) { mutableStateOf(input) }
    SideEffect { if (input != null) retainedInput = input }
    val rendering = input ?: retainedInput
    val root = remember { FocusRequester() }; val timeline = remember { FocusRequester() }; val audio = remember { FocusRequester() }
    val latestClose by rememberUpdatedState(close)
    val scope = rememberCoroutineScope()
    val back = {
        if (state.back(latestClose)) scope.launch {
            withFrameNanos { }
            if (state.visible && !state.showTracks && !state.showNext && !state.showEpisodes) {
                // The timeline is disabled for live content; never focus it there.
                val live = state.live || (title?.identity?.kind in listOf("channel", "tv") && state.duration == null)
                if (!state.focusAudio && (state.duration ?: 0) > 0 && !live) timeline.requestFocus() else audio.requestFocus()
            } else if (!state.showTracks && !state.showNext && !state.showEpisodes) root.requestFocus()
        }
        Unit
    }
    DisposableEffect(state) { backChanged(back); onDispose { backChanged(null) } }
    // A stream whose clock advances without ever presenting a frame is a black
    // screen this TV cannot decode; surface it instead of playing nothing.
    LaunchedEffect(state) { while (true) { delay(1000); state.checkFirstFrame() } }
    LaunchedEffect(externalFacts, state) { externalFacts.collect(state::observe) }
    LaunchedEffect(state, state.ended, state.endHandled, extras.nextEpisode, playEpisode != null, state.failed) {
        state.offerNext(extras.nextEpisode != null && playEpisode != null)
    }
    LaunchedEffect(state.visible, paused, state.interaction, state.buffering, state.showTracks, state.showNext, state.showEpisodes, state.failed, state.scrub) {
        if (state.visible && !paused && !state.buffering && !state.showTracks && !state.showNext && !state.showEpisodes && !state.failed && state.scrub == null && input != null) { delay(5000); state.visible = false }
    }
    val skipActive = extras.skip?.let { state.position in it.startMillis until it.endMillis } == true && !state.visible
    LaunchedEffect(state.visible, state.buffering, state.failed, state.showTracks, state.showNext, state.showEpisodes, state.duration, state.focusAudio, skipActive) {
        if (state.visible && (!state.buffering || state.hasPresented) && !state.failed && !state.showTracks && !state.showNext && !state.showEpisodes) {
            // The timeline is disabled for live content; never focus it there.
            val live = state.live || (title?.identity?.kind in listOf("channel", "tv") && state.duration == null)
            if (!state.focusAudio && (state.duration ?: 0) > 0 && !live) timeline.requestFocus() else audio.requestFocus()
        }
        else if (!state.showTracks && !state.showNext && !state.failed && !state.showEpisodes && !skipActive) root.requestFocus()
    }
    TvBackdrop(Modifier.fillMaxSize().drawBehind { drawRect(canvasColor, blendMode = BlendMode.Src) }.focusRequester(root).onFocusChanged { state.rootFocused = it.isFocused }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) state.interaction++
        if (state.showTracks && event.key != Key.Escape) false
        else if (event.type == KeyEventType.KeyUp && event.key in listOf(Key.DirectionLeft, Key.DirectionRight) &&
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
            Key.DirectionUp -> if (!state.failed && !state.buffering && !state.showNext && !state.showTracks && !state.showEpisodes && (state.timelineFocused || state.rootFocused || !state.visible)) {
                state.scrub = null; state.reveal(audio = true); true
            } else false
            else -> if (event.key != Key.Back && !state.visible && !state.failed && !state.buffering && !state.showNext && !state.showTracks && !state.showEpisodes) {
                state.reveal(); false
            } else false
        }
    }.focusable()) {
        val scale = maxWidth / 1920.dp
        // The video keeps its full frame, also behind the next-episode panel at the end.
        Box(Modifier.fillMaxSize()) {
            if (input == null && previewFrame != null) AsyncImage(previewFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (rendering != null) PlayerSurface(rendering, paused || input == null, Modifier.fillMaxSize(), {
                if (input === rendering) { state.observe(it); observe(it) }
            }, state.commands)
        }
        if (state.showNext) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = .35f),
            .45f to Color.Black.copy(alpha = .55f), 1f to Color.Black.copy(alpha = .82f))))
        when {
            // `undecodable` is the "clock runs, no picture" case: the TV opened a
            // decoder it cannot display (unsupported Dolby Vision profile). Saying so
            // is the only honest state; a black screen with a moving bar is not.
            state.failed || state.undecodable -> TvPlayerFailure(extras, state.position,
                if (state.undecodable) "This TV can’t decode this stream. Choose another source — the 4K Dolby Vision releases are not supported by this device’s decoder." else error,
                scale, retry, chooseSources, close)
            state.buffering && !state.hasPresented -> TvPlayerBuffer(title, extras, scale)
            state.showNext && extras.nextEpisode != null -> TvNextEpisode(title, extras.nextEpisode, scale,
                // The countdown's last tick can land in the frame that closes the panel: only an open panel plays.
                { if (state.showNext) { state.endHandled = true; state.showNext = false; playEpisode?.invoke(extras.nextEpisode) } }, state::keepWatching, extras.creditsLabel,
                nextEpisodeAutoplays(autoplayNext, title, state.live) && state.ended, reduceMotion, state.interaction)
            else -> {
                TvControlsVisibility(state.visible && !state.showTracks && !state.showEpisodes) {
                    TvPlayerControls(state, title, extras, scale, timeline, audio,
                        toggle = toggle, previewCommand = previewCommand, chooseSources = chooseSources, playEpisode = playEpisode)
                }
                val skip = extras.skip
                if (!state.visible && skip != null && state.position in skip.startMillis until skip.endMillis) TvSkipPrompt(skip, state.position, scale) { state.send(PlayerCommand.Seek(skip.endMillis), previewCommand) }
            }
        }
        if (state.hasPresented && (state.buffering || input == null && rendering != null))
            androidx.compose.material3.CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center).size(48.dp * scale), color = Palette.text)
        if (state.showTracks) TvPlaybackTrackPicker(state.tracks, state.subtitleSize, scale,
            { choice ->
                state.send(choice, previewCommand)
                (choice as? PlayerCommand.SelectTrack)?.let { pick ->
                    trackChosen(pick.kind, (if (pick.kind == PlaybackTrackKind.Audio) state.tracks.audio else state.tracks.subtitles)
                        .firstOrNull { it.id == pick.id }?.language)
                }
            }, { state.showTracks = false; state.reveal() }, trackFocus,
            dismissChanged = { state.dismissTracks = it }, openSettings = openSettings)
        if (state.showEpisodes) TvPlayerEpisodePicker(extras.episodes, scale, title, { state.showEpisodes = false; playEpisode?.invoke(it) }, { state.showEpisodes = false })
    }
}
