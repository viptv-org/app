package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTrackElement
import org.w3c.dom.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    val video = remember(input.id) { (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        preload = "auto"
        style.width = "100%"; style.height = "100%"; style.objectFit = "contain"
        setAttribute("playsinline", "")
    } }
    val tracks = remember(video) { mutableMapOf<Int, HTMLTrackElement>() }
    val blobs = remember(video) { mutableListOf<String>() }
    var selected by remember(video) { mutableStateOf<String?>(null) }
    val latestObserve by rememberUpdatedState(observe)
    val candidates by input.providerSubtitles.collectAsState()
    LaunchedEffect(candidates) { latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(selected)))) }
    Box(modifier)
    DisposableEffect(video) {
        val container = requireNotNull(document.getElementById("viptv-video-layer") as? HTMLElement)
        container.appendChild(video)
        var hls: Hls? = null
        var disposed = false
        observeBrowserFrame(video) { if (!disposed) {
            latestObserve(input.mediaProgress((video.currentTime * 1000).toLong(), video.duration.takeIf { it.isFinite() }?.let { (it * 1000).toLong() }))
            latestObserve(PlayerFact.Presentation(PresentationProof.BrowserCompositorPresented))
        } }
        video.onloadedmetadata = {
            video.currentTime = input.positionMillis / 1000.0
            latestObserve(input.mediaProgress((video.currentTime * 1000).toLong(), video.duration.takeIf { it.isFinite() }?.let { (it * 1000).toLong() }))
            null
        }
        video.onplay = { latestObserve(PlayerFact.Status("Playing")); null }
        video.onpause = { latestObserve(PlayerFact.Status("Paused")); null }
        video.onwaiting = { latestObserve(PlayerFact.Status("Buffering")); null }
        video.oncanplay = { latestObserve(PlayerFact.Status(if (video.paused) "Paused" else "Playing")); null }
        video.ontimeupdate = { latestObserve(input.mediaProgress((video.currentTime * 1000).toLong(), video.duration.takeIf { it.isFinite() }?.let { (it * 1000).toLong() })); null }
        video.onerror = { _, _, _, _, _ -> latestObserve(PlayerFact.Failed); null }
        video.onended = { latestObserve(PlayerFact.Status("Ended")); null }
        val playlist = input.uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        if (playlist && video.canPlayType("application/vnd.apple.mpegurl").toString().isEmpty() && Hls.isSupported()) {
            hls = Hls().also { it.loadSource(input.uri); it.attachMedia(video) }
        } else { video.src = input.uri }
        latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(null))))
        onDispose {
            disposed = true
            blobs.forEach(::revokeSubtitleBlob)
            blobs.clear(); tracks.clear()
            video.onloadedmetadata = null; video.ontimeupdate = null; video.onerror = null; video.onended = null
            video.onplay = null; video.onpause = null; video.onwaiting = null; video.oncanplay = null
            hls?.destroy()
            video.pause(); video.removeAttribute("src"); video.load()
            container.removeChild(video)
        }
    }
    LaunchedEffect(video, commands) {
        var subtitleJob: Job? = null
        commands.collect { command ->
            try {
                when (command) {
                    is PlayerCommand.Seek -> if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                        else video.currentTime = command.positionMillis.coerceAtLeast(0) / 1000.0
                    is PlayerCommand.Volume -> video.volume = command.value.coerceIn(0f, 1f).toDouble()
                    is PlayerCommand.SubtitleSize -> latestObserve(PlayerFact.ControlFailed)
                    is PlayerCommand.SelectTrack -> {
                        subtitleJob?.cancel()
                        subtitleJob = launch {
                            try {
                                require(command.kind == PlaybackTrackKind.Subtitle)
                                val candidate = input.providerSubtitle(command.id)
                                if (command.id != null) requireNotNull(candidate)
                                val chosen = if (candidate == null) null else tracks.getOrPut(requireNotNull(command.id)) {
                                    val content = requireNotNull(input.subtitleContent)(candidate.id)
                                    ensureActive()
                                    val uri = subtitleBlob(content.decodeToString()).also { blobs.add(it) }
                                    (document.createElement("track") as HTMLTrackElement).apply {
                                        kind = "subtitles"; label = candidate.label; srclang = candidate.language; src = uri
                                        onload = { placeProviderCues(this, input.timelineOffsetMillis / 1000.0); null }
                                        onerror = { _, _, _, _, _ -> latestObserve(PlayerFact.ControlFailed); null }
                                        video.appendChild(this)
                                    }
                                }
                                tracks.values.forEach { it.track.mode = if (it == chosen) TextTrackMode.SHOWING else TextTrackMode.DISABLED }
                                selected = candidate?.id
                                latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(selected))))
                            } catch (error: CancellationException) { throw error }
                            catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
                        }
                    }
                    is PlayerCommand.Speed -> video.playbackRate = command.value.coerceIn(0.25, 2.0)
                }
                latestObserve(PlayerFact.Properties(video.volume.toFloat(), video.playbackRate))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
        }
    }
    LaunchedEffect(paused, video) { if (paused) video.pause() else video.play() }
}
