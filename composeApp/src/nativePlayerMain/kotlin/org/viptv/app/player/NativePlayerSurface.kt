package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.TrackKind
import io.github.yuroyami.kiteplayer.SeekMode
import io.github.yuroyami.kiteplayer.TrackId
import io.github.yuroyami.kiteplayer.compose.KitePlayerVideo
import io.github.yuroyami.kiteplayer.compose.rememberKitePlayer
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    key(input.id) {
        val player = rememberKitePlayer()
        val latestObserve by rememberUpdatedState(observe)
        var opened by remember { mutableStateOf(false) }
        KitePlayerVideo(player, modifier)
        LaunchedEffect(input.id) {
            try {
                player.open(MediaItem(uri = input.uri, headers = input.headers, startPosition = input.positionMillis.milliseconds))
                opened = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { latestObserve(PlayerFact.Failed) }
        }
        LaunchedEffect(opened, paused) { if (opened) { if (paused) player.pause() else player.play() } }
        LaunchedEffect(player, commands) {
            commands.collect { command ->
                if (opened) try {
                    when (command) {
                        is PlayerCommand.Seek -> {
                            player.seek(command.positionMillis.coerceAtLeast(0).milliseconds, SeekMode.Precise)
                            latestObserve(PlayerFact.Progress(player.position().inWholeMilliseconds, player.state.value.durationMillis))
                        }
                        is PlayerCommand.Volume -> player.setVolume(command.value.coerceIn(0f, 1f))
                        is PlayerCommand.SubtitleSize -> player.setSubtitleScale(command.value.coerceIn(0.5f, 2f))
                        is PlayerCommand.SelectTrack -> player.selectTrack(if (command.kind == PlaybackTrackKind.Audio) TrackKind.Audio else TrackKind.Subtitle, command.id?.let { TrackId(it) })
                        is PlayerCommand.Speed -> player.setSpeed(command.value.coerceIn(0.25, 2.0))
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
            }
        }
        LaunchedEffect(player) {
            player.progress.collect { latestObserve(PlayerFact.Progress(it.positionMillis, player.state.value.durationMillis)) }
        }
        LaunchedEffect(player) {
            player.state.collect { state ->
                latestObserve(if (state.error != null) PlayerFact.Failed else PlayerFact.Status(state.status.name))
                latestObserve(PlayerFact.Properties(state.volume, state.speed, state.subtitleScale))
                latestObserve(PlayerFact.Tracks(PlaybackTracks(
                    state.tracks.audio.mapIndexed { index, track -> PlaybackTrack(track.id.value, track.label.takeUnless { it.isBlank() || it == "und" } ?: "Audio track ${index + 1}", track.id == state.tracks.selectedAudio) },
                    state.tracks.subtitles.map { PlaybackTrack(it.id.value, it.label, it.id == state.tracks.selectedSubtitle) })))
            }
        }
    }
}
