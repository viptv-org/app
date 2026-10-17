package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.AudioConfig
import io.github.yuroyami.kiteplayer.PlayerConfig
import io.github.yuroyami.kiteplayer.PlayerEvent
import io.github.yuroyami.kiteplayer.PlaybackException
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.TrackKind
import io.github.yuroyami.kiteplayer.SeekMode
import io.github.yuroyami.kiteplayer.TrackId
import io.github.yuroyami.kiteplayer.compose.KitePlayerComposeVideoWithPresentationReady
import io.github.yuroyami.kiteplayer.compose.KiteVideoPresentationProof
import io.github.yuroyami.kiteplayer.compose.KitePlayerVideo
import io.github.yuroyami.kiteplayer.compose.KiteRenderPath
import io.github.yuroyami.kiteplayer.compose.rememberKitePlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun KitePlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    key(input) {
        val path = nativePlayerRenderPath()
        val player = rememberKitePlayer(PlayerConfig(frameEvents = path != KiteRenderPath.ComposeCanvas,
            audio = AudioConfig(preferredLanguages = listOfNotNull(input.preferences.preferred_audio_language)),
            subtitles = desktopSubtitleConfig(input.preferences)))
        val subtitleDirectory = subtitleCacheDirectory()
        val latestObserve by rememberUpdatedState(observe)
        val candidates by input.providerSubtitles.collectAsState()
        val latestPaused by rememberUpdatedState(paused)
        val providerIds = remember { mutableMapOf<String, TrackId>() }
        var subtitleTouched by remember { mutableStateOf(false) }
        var subtitleJob by remember { mutableStateOf<Job?>(null) }
        var opened by remember { mutableStateOf(false) }
        var rendererReady by remember { mutableStateOf(false) }
        val transport = remember { PlaybackTransportGate() }
        val requiredAudio = remember { RequiredAudioSelection(input.requiredAudioIndex) }
        val presented = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        val drawn = remember { java.util.concurrent.atomic.AtomicLong(0) }
        val scope = rememberCoroutineScope()
        if (path == KiteRenderPath.ComposeCanvas) KitePlayerComposeVideoWithPresentationReady(player = player, onRendererAttached = { rendererReady = true },
            modifier = modifier, onPresentation = { evidence ->
                drawn.incrementAndGet()
                val proof = when (evidence.proof) {
                    KiteVideoPresentationProof.WindowGpuCompleted -> PresentationProof.AndroidWindowGpuCompleted
                    KiteVideoPresentationProof.AndroidSoftwareDrawCompleted -> PresentationProof.AndroidSoftwareDrawCompleted
                    KiteVideoPresentationProof.DesktopDrawCompleted -> PresentationProof.DesktopDrawCompleted
                }
                if (presented.compareAndSet(false, true)) scope.launch {
                    latestObserve(input.mediaProgress(player.position().inWholeMilliseconds, player.state.value.durationMillis))
                    latestObserve(PlayerFact.Presentation(proof, evidence.atNanos,
                        exact = evidence.proof == KiteVideoPresentationProof.WindowGpuCompleted))
                }
            })
        else KitePlayerVideo(player, modifier, path = path, onRendererAttached = { rendererReady = true })
        LaunchedEffect(player, path) {
            if (path != KiteRenderPath.ComposeCanvas) player.events.collect { event ->
                if (event is PlayerEvent.FramePresented) drawn.incrementAndGet()
                if (event is PlayerEvent.FramePresented && presented.compareAndSet(false, true)) {
                    latestObserve(input.mediaProgress(player.position().inWholeMilliseconds, player.state.value.durationMillis))
                    latestObserve(PlayerFact.Presentation(PresentationProof.NativeRendererPresented, event.atNanos, event.exact))
                }
            }
        }
        LaunchedEffect(player) {
            input.diagnostics?.let { report ->
                while (true) {
                    kotlinx.coroutines.delay(2000)
                    val stats = player.stats.value
                    report("path=$path decoded=${stats.decodedVideoFrames} submitted=${stats.submittedFrames} drawn=${drawn.get()} " +
                        "decode_fps=${stats.videoDecodeFps} av_drift_ms=${stats.avDrift.inWholeMilliseconds} " +
                        "rebuffers=${stats.rebuffers} audio_underruns=${stats.audioUnderruns} hw=${when (stats.hardwareDecode) { is io.github.yuroyami.kiteplayer.HwdecStatus.HardwareZeroCopy -> "direct"; is io.github.yuroyami.kiteplayer.HwdecStatus.HardwareWithDownload -> "download"; else -> "software" }} seekable=${player.state.value.seekable}")
                }
            }
        }
        LaunchedEffect(input, rendererReady) {
            if (!rendererReady) return@LaunchedEffect
            try {
                player.open(MediaItem(uri = input.uri, headers = input.headers, startPosition = input.positionMillis.milliseconds))
                if (!applyRequiredAudio(player, requiredAudio) { latestObserve(it) }) return@LaunchedEffect
                transport.request(latestPaused, player.state.value.status.name)
                if (latestPaused) player.pause() else player.play()
                opened = true
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                if (error is PlaybackException) reportNativePlaybackFailure(playbackFailureSummary(input, error.error))
                latestObserve(PlayerFact.Failed)
            }
        }
        LaunchedEffect(opened, paused) { if (opened) {
            transport.request(paused, player.state.value.status.name)
            if (paused) player.pause() else player.play()
        } }
        LaunchedEffect(player, commands) {
            commands.collect { command ->
                if (opened) try {
                    when (command) {
                        is PlayerCommand.Seek -> {
                            if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                            else {
                                player.seek(command.positionMillis.coerceAtLeast(0).milliseconds, SeekMode.Precise)
                                latestObserve(input.mediaProgress(player.position().inWholeMilliseconds, player.state.value.durationMillis))
                            }
                        }
                        is PlayerCommand.Volume -> player.setVolume(command.value.coerceIn(0f, 1f))
                        is PlayerCommand.SubtitleSize -> player.setSubtitleScale(command.value.coerceIn(0.5f, 2f))
                        is PlayerCommand.SelectTrack -> {
                            if (command.kind == PlaybackTrackKind.Audio) requiredAudio.userSelected()
                            if (command.kind == PlaybackTrackKind.Subtitle) subtitleTouched = true
                            val prior = subtitleJob
                            prior?.cancel()
                            subtitleJob = launch {
                                try {
                                    prior?.join()
                                    currentCoroutineContext().ensureActive()
                                    val candidate = if (command.kind == PlaybackTrackKind.Subtitle) input.providerSubtitle(command.id) else null
                                    if (candidate == null) player.selectTrack(if (command.kind == PlaybackTrackKind.Audio) TrackKind.Audio else TrackKind.Subtitle, command.id?.let { TrackId(it) })
                                    else {
                                        val track = providerIds[candidate.id] ?: attachProviderSubtitle(player, input, candidate, subtitleDirectory).also { providerIds[candidate.id] = it }
                                        currentCoroutineContext().ensureActive()
                                        player.selectTrack(TrackKind.Subtitle, track)
                                        val snapshot = player.state.value
                                        latestObserve(PlayerFact.Tracks(PlaybackTracks(
                                            snapshot.tracks.audio.map { PlaybackTrack(it.id.value, it.label, it.id == snapshot.tracks.selectedAudio) },
                                            snapshot.tracks.subtitles.filterNot { it.id in providerIds.values }.map { PlaybackTrack(it.id.value, it.label, it.id == snapshot.tracks.selectedSubtitle) } + input.providerTracks(candidate.id))))
                                    }
                                } catch (error: CancellationException) { throw error }
                                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
                            }
                        }
                        is PlayerCommand.Speed -> player.setSpeed(command.value.coerceIn(0.25, 2.0))
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
            }
        }
        LaunchedEffect(candidates, opened, subtitleTouched) {
            if (!opened || subtitleTouched) return@LaunchedEffect
            val candidate = preferredProviderSubtitle(input, candidates) ?: return@LaunchedEffect
            val desired = input.preferences.preferred_subtitle_language ?: return@LaunchedEffect
            val embedded = player.state.value.tracks.let { tracks -> tracks.subtitles.find { it.id == tracks.selectedSubtitle } }
            if (embedded?.language?.let { canonicalLanguageTag(it).substringBefore('-') == canonicalLanguageTag(desired).substringBefore('-') } == true) return@LaunchedEffect
            val prior = subtitleJob
            prior?.cancel()
            subtitleJob = launch {
                try {
                    prior?.join()
                    val track = providerIds[candidate.id] ?: attachProviderSubtitle(player, input, candidate, subtitleDirectory).also { providerIds[candidate.id] = it }
                    ensureActive()
                    if (!subtitleTouched) player.selectTrack(TrackKind.Subtitle, track)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
            }
        }
        LaunchedEffect(candidates) {
            val snapshot = player.state.value
            latestObserve(PlayerFact.Tracks(PlaybackTracks(
                snapshot.tracks.audio.map { PlaybackTrack(it.id.value, it.label, it.id == snapshot.tracks.selectedAudio) },
                snapshot.tracks.subtitles.filterNot { it.id in providerIds.values }.map { PlaybackTrack(it.id.value, it.label, it.id == snapshot.tracks.selectedSubtitle) } +
                    input.providerTracks(providerIds.entries.firstOrNull { it.value == snapshot.tracks.selectedSubtitle }?.key))))
        }
        LaunchedEffect(player) {
            player.progress.collect { if (opened) latestObserve(input.mediaProgress(it.positionMillis, player.state.value.durationMillis)) }
        }
        LaunchedEffect(player) {
            var lastError: io.github.yuroyami.kiteplayer.PlaybackError? = null
            player.state.collect { state ->
                if (state.error != null) {
                    if (state.error != lastError) reportNativePlaybackFailure(playbackFailureSummary(input, state.error!!))
                    lastError = state.error
                    latestObserve(PlayerFact.Failed)
                }
                else if (transport.accept(state.status.name)) latestObserve(PlayerFact.Status(state.status.name))
                latestObserve(PlayerFact.Properties(state.volume, state.speed, state.subtitleScale))
                latestObserve(PlayerFact.Tracks(PlaybackTracks(
                    state.tracks.audio.mapIndexed { index, track -> PlaybackTrack(track.id.value, track.label.takeUnless { it.isBlank() || it == "und" } ?: "Audio track ${index + 1}", track.id == state.tracks.selectedAudio) },
                    state.tracks.subtitles.filterNot { it.id in providerIds.values }.map { PlaybackTrack(it.id.value, it.label, it.id == state.tracks.selectedSubtitle) } +
                        input.providerTracks(providerIds.entries.firstOrNull { it.value == state.tracks.selectedSubtitle }?.key))))
            }
        }
    }
}
