package org.viptv.app.preview

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.player.*
import org.viptv.app.player.*
import org.viptv.app.state.Screen

/** Synthetic acknowledgements are confined to this fixture; Native uses real decoded video facts. */
@Composable
internal fun PreviewTvPlayer(variant: String, go: (Screen) -> Unit, backChanged: ((() -> Unit)?) -> Unit) {
    val scene = when {
        variant.startsWith("Tracks") -> TvPlayerScene.Tracks
        variant == "BufferFast" -> TvPlayerScene.Buffer
        else -> TvPlayerScene.entries.find { it.name == variant } ?: TvPlayerScene.Controls
    }
    val native = variant == "Native"
    val input = remember(native) { if (native) PlaybackInput("tv-player-native-qa", "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4") else null }
    val facts = remember { MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16) }
    LaunchedEffect(variant) {
        if (variant == "BufferFast") { delay(250); facts.emit(PlayerFact.Status("Playing")) }
    }
    var tracks by remember(variant) { mutableStateOf(when (variant) {
        "TracksInvalidFocus" -> PreviewTvPlayback.tracks.copy(subtitles = PreviewTvPlayback.tracks.subtitles.map { it.copy(selected = false) })
        "TracksAudioOnly" -> PreviewTvPlayback.tracks.copy(subtitles = emptyList())
        "TracksEmpty" -> PlaybackTracks(emptyList(), emptyList())
        else -> PreviewTvPlayback.tracks
    }) }
    var paused by remember { mutableStateOf(true) }
    val duration = 3132000L
    val initial = remember { if (native) emptyList() else listOf(PlayerFact.Progress(if (scene == TvPlayerScene.Skip) 5000 else 842000, duration),
        PlayerFact.Tracks(tracks), PlayerFact.Properties(1f, 1.0)) }
    val command: (PlayerCommand) -> Unit = { value ->
        when (value) {
            is PlayerCommand.Seek -> facts.tryEmit(PlayerFact.Progress(value.positionMillis, duration))
            is PlayerCommand.SelectTrack -> {
                tracks = if (value.kind == PlaybackTrackKind.Audio) tracks.copy(audio = tracks.audio.map { it.copy(selected = it.id == value.id) })
                    else tracks.copy(subtitles = tracks.subtitles.map { it.copy(selected = it.id == value.id) })
                facts.tryEmit(PlayerFact.Tracks(tracks))
            }
            is PlayerCommand.SubtitleSize -> facts.tryEmit(PlayerFact.Properties(1f, 1.0, value.value))
            else -> Unit
        }
    }
    TvPlayerScreen(input, paused, null, { paused = !paused }, { fact ->
        if (native) {
            when (fact) {
                is PlayerFact.Status -> { if (fact.name in listOf("Playing", "Paused", "Ended")) paused = fact.name != "Playing"; println("VIPTV_TV_PLAYER_QA status=${fact.name}") }
                is PlayerFact.Progress -> println("VIPTV_TV_PLAYER_QA position=${fact.positionMillis} duration=${fact.durationMillis}")
                is PlayerFact.Tracks -> println("VIPTV_TV_PLAYER_QA audio=${fact.options.audio.size} subtitles=${fact.options.subtitles.size}")
                PlayerFact.Failed -> println("VIPTV_TV_PLAYER_QA failed")
                else -> Unit
            }
        }
    }, { go(Screen.Home) }, if (native) PreviewData.hero.copy(title = "Flower", subtitle = "Public test clip", logo = null) else PreviewData.playerTitle,
        if (native) TvPlaybackExtras(sourceLabel = "Public test clip") else PreviewTvPlayback.extras,
        { go(Screen.Sources) }, { go(Screen.Sources) }, { go(Screen.Sources) }, backChanged,
        previewFrame = if (scene == TvPlayerScene.Skip) PreviewTvPlayback.skipFrame else PreviewTvPlayback.frame,
        scene = scene, initialFacts = initial, externalFacts = facts, previewCommand = if (native) null else command,
        trackFocus = if (variant == "TracksInvalidFocus" || variant == "TracksAudioOnly" || variant == "TracksEmpty") 999
            else if (scene == TvPlayerScene.Tracks) 11 else null)
}
