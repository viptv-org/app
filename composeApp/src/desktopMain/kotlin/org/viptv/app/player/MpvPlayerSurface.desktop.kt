package org.viptv.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.skia.Rect
import org.viptv.app.player.mpv.*
import org.viptv.engine.wire.SubtitleCandidate
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
internal fun MpvPlayerSurface(input: PlaybackInput, paused: Boolean, fill: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: Flow<PlayerCommand>) {
    key(input) {
        val host = mpvHost()
        val result = remember { runCatching { MpvSession(input) } }
        val session = result.getOrNull()
        val latestObserve by rememberUpdatedState(observe)
        val latestPaused by rememberUpdatedState(paused)
        if (session == null) {
            LaunchedEffect(Unit) { reportNativePlaybackFailure("mpv_initialization_failed"); latestObserve(PlayerFact.Failed) }
        } else {
        var revision by remember { mutableLongStateOf(0) }
        val renderer = remember { MpvRenderer(session, host) { revision++ } }
        val presented = remember { AtomicBoolean(false) }
        val lastDrawn = remember { AtomicLong(0) }
        val scope = rememberCoroutineScope()
        var opened by remember { mutableStateOf(false) }
        var subtitleTouched by remember { mutableStateOf(false) }
        var subtitleJob by remember { mutableStateOf<Job?>(null) }
        val providerIds = remember { mutableMapOf<String, Int>() }
        val subtitleDirectory = subtitleCacheDirectory()
        val subtitleFiles = remember { mutableListOf<File>() }
        val requiredAudio = remember { RequiredAudioSelection(input.requiredAudioIndex) }
        val transport = remember { PlaybackTransportGate() }
        val candidates by input.providerSubtitles.collectAsState()
        val state by session.state.collectAsState()
        DisposableEffect(renderer) {
            renderer.start()
            onDispose {
                renderer.close()
                subtitleFiles.forEach { it.delete() }
            }
        }
        Canvas(modifier.onSizeChanged { renderer.desiredWidth = it.width; renderer.desiredHeight = it.height; host.redraw() }) {
            revision // A native update invalidates this draw, independently of progress polling.
            drawIntoCanvas { target ->
                renderer.image?.let { image ->
                    target.nativeCanvas.drawImageRect(image, Rect.makeWH(size.width, size.height))
                    val frame = renderer.generation
                    if (frame > 0 && lastDrawn.getAndSet(frame) != frame) {
                        val now = System.nanoTime()
                        org.viptv.app.DesktopTorrentFrames.presented(renderer.framePositionMs, now)
                        renderer.drawn()
                        if (presented.compareAndSet(false, true)) scope.launch {
                            latestObserve(input.mediaProgress(renderer.framePositionMs, session.state.value.durationMs))
                            latestObserve(PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted, now, exact = false))
                        }
                    }
                }
            }
        }
        LaunchedEffect(session) {
            try {
                session.open(input)
                when (val choice = requiredAudio.choose(session.state.value.audio.map { AudioCandidate(it.id) }, tracksKnown = true)) {
                    is RequiredAudioChoice.Select -> {
                        session.property("aid", choice.id.toString())
                        check(session.selectedAudio() == choice.id)
                    }
                    RequiredAudioChoice.Unavailable -> error("Required audio unavailable")
                    RequiredAudioChoice.Unchanged -> Unit
                }
                renderer.authorizePresentation()
                opened = true
                transport.request(latestPaused, session.state.value.status)
                session.property("pause", if (latestPaused) "yes" else "no")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { reportNativePlaybackFailure(playbackFailureSummary(input, "mpv_open_failed", error.message ?: "Media could not be opened")); latestObserve(PlayerFact.Failed) }
        }
        LaunchedEffect(opened, paused) {
            if (opened) try {
                transport.request(paused, state.status)
                session.property("pause", if (paused) "yes" else "no")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
        }
        LaunchedEffect(opened, fill) {
            if (opened) try {
                session.property("panscan", if (fill) "1.0" else "0.0")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
        }
        suspend fun attach(candidate: SubtitleCandidate): Int {
            providerIds[candidate.id]?.let { return it }
            val content = shiftSubtitleBytes(requireNotNull(input.subtitleContent)(candidate.id), input.timelineOffsetMillis)
            var temporary: File? = null
            val file = try {
                withContext(Dispatchers.IO) {
                    val header = content.take(128).toByteArray().decodeToString().trimStart('\uFEFF', ' ', '\r', '\n')
                    val suffix = when { header.startsWith("WEBVTT") -> ".vtt"; header.startsWith("[Script Info]") -> ".ass"; else -> ".srt" }
                    File.createTempFile("viptv-subtitle-", suffix, subtitleDirectory).also { temporary = it; it.writeBytes(content) }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) { temporary?.delete() }
                throw error
            }
            // mpv may read asynchronously after sub-add: retain until this session closes.
            subtitleFiles.add(file)
            currentCoroutineContext().ensureActive()
            val before = session.state.value.subtitles.map { it.id }.toSet()
            session.command("sub-add", file.absolutePath, "cached", candidate.label, candidate.language ?: "")
            val id = withTimeout(5_000) {
                while (true) {
                    session.state.value.subtitles.firstOrNull { it.id !in before }?.let { return@withTimeout it.id }
                    delay(25)
                }
                @Suppress("UNREACHABLE_CODE") 0
            }
            providerIds[candidate.id] = id
            return id
        }
        fun selectSubtitle(candidate: SubtitleCandidate?, id: Int?) {
            val previous = subtitleJob
            previous?.cancel()
            subtitleJob = scope.launch {
                try {
                    previous?.join(); ensureActive()
                    val target = candidate?.let { attach(it) } ?: id
                    ensureActive()
                    session.property("sid", target?.toString() ?: "no")
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
            }
        }
        LaunchedEffect(commands, session) {
            commands.collect { command ->
                if (opened) try {
                    when (command) {
                        is PlayerCommand.Seek -> {
                            input.diagnostics?.invoke("seek_requested target_ms=${command.positionMillis} opened=$opened")
                            if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                            else session.command("seek", (command.positionMillis.coerceAtLeast(0) / 1000.0).toString(), "absolute+exact")
                        }
                        is PlayerCommand.Volume -> session.property("volume", (command.value.coerceIn(0f, 1f) * 100).toString())
                        is PlayerCommand.Speed -> session.property("speed", command.value.coerceIn(.25, 2.0).toString())
                        is PlayerCommand.SubtitleSize -> session.property("sub-scale", command.value.coerceIn(.5f, 2f).toString())
                        is PlayerCommand.SelectTrack -> if (command.kind == PlaybackTrackKind.Audio) {
                            requiredAudio.userSelected()
                            session.property("aid", command.id?.toString() ?: "no")
                        } else {
                            subtitleTouched = true
                            selectSubtitle(input.providerSubtitle(command.id), command.id?.takeIf { it >= 0 })
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
            }
        }
        LaunchedEffect(candidates, opened, subtitleTouched) {
            if (!opened || subtitleTouched) return@LaunchedEffect
            val candidate = preferredProviderSubtitle(input, candidates) ?: return@LaunchedEffect
            val desired = input.preferences.preferred_subtitle_language ?: return@LaunchedEffect
            val embedded = state.subtitles.firstOrNull { it.selected }
            if (embedded?.language?.let { canonicalLanguageTag(it).substringBefore('-') == canonicalLanguageTag(desired).substringBefore('-') } != true)
                selectSubtitle(candidate, null)
        }
        LaunchedEffect(state, candidates) {
            if (state.error != null) latestObserve(PlayerFact.Failed)
            else if (opened && transport.accept(state.status)) latestObserve(PlayerFact.Status(state.status))
            if (opened) latestObserve(input.mediaProgress(state.positionMs, state.durationMs))
            latestObserve(PlayerFact.Properties(state.volume, state.speed, state.subtitleScale))
            latestObserve(PlayerFact.Tracks(PlaybackTracks(state.audio,
                state.subtitles.filterNot { it.id in providerIds.values } + input.providerTracks(
                    providerIds.entries.firstOrNull { (_, id) -> state.subtitles.any { it.id == id && it.selected } }?.key))))
        }
        LaunchedEffect(session) {
            while (true) {
                delay(2_000)
                val value = session.state.value
                input.diagnostics?.invoke("path=${renderer.path} tone_map=${if (session.toneMapped) "on" else "off"} drawn=${lastDrawn.get()} source_fps=${value.sourceFps} dropped=${value.dropped} hw=${value.hwdec} buffering=${value.buffering} position_ms=${value.positionMs}")
            }
        }
        }
    }
}
