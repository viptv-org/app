package org.viptv.app.player

import org.viptv.app.connection.subtitleMode
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

private var nextCaptionStyle = 0
private class BrowserVideoLifetime { var active = false; var playRequest = 0L }

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    val video = remember(input.id) { (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        preload = "auto"
        style.width = "100%"; style.height = "100%"; style.objectFit = "contain"
        setAttribute("playsinline", "")
    } }
    val lifetime = remember(video) { BrowserVideoLifetime() }
    var hlsController by remember(video) { mutableStateOf<Hls?>(null) }
    val captionId = remember(video) { "viptv-caption-${++nextCaptionStyle}" }
    val captionStyle = remember(video) { document.createElement("style") as HTMLStyleElement }
    var captionScale by remember { mutableStateOf(input.preferences.subtitle_font_scale.toFloat().takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f) }
    val tracks = remember(video) { mutableMapOf<Int, HTMLTrackElement>() }
    val blobs = remember(video) { mutableListOf<String>() }
    var selected by remember(video) { mutableStateOf<String?>(null) }
    var subtitleTouched by remember { mutableStateOf(false) }
    var requestedSubtitle by remember { mutableStateOf<org.viptv.engine.wire.SubtitleCandidate?>(null) }
    var subtitleRequest by remember { mutableStateOf(0L) }
    val latestObserve by rememberUpdatedState(observe)
    LaunchedEffect(captionStyle, captionScale, input.preferences) {
        val declarations = browserCaptionDeclarations(input.preferences, captionScale)
        captionStyle.textContent = if (declarations.isEmpty()) "" else "#$captionId::cue {$declarations}"
        tracks.values.forEach { liftProviderCues(it, providerCueLine(captionScale)) }
        latestObserve(PlayerFact.Properties(video.volume.toFloat(), video.playbackRate, captionScale))
    }
    val candidates by input.providerSubtitles.collectAsState()
    LaunchedEffect(video, candidates) { latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(selected)))) }
    Box(modifier)
    DisposableEffect(video) {
        val container = requireNotNull(document.getElementById("viptv-video-layer") as? HTMLElement)
        video.id = captionId
        container.appendChild(video)
        container.appendChild(captionStyle)
        lifetime.active = true
        var disposed = false
        var removeHlsErrors: (() -> Unit)? = null
        observeBrowserFrame(video) { if (!disposed) {
            latestObserve(input.mediaProgress((video.currentTime * 1000).toLong(), video.duration.takeIf { it.isFinite() }?.let { (it * 1000).toLong() }))
            latestObserve(PlayerFact.Presentation(PresentationProof.BrowserCompositorPresented))
        } }
        video.onloadedmetadata = {
            if (!input.live) video.currentTime = input.positionMillis / 1000.0
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
        val playlist = input.isHlsPlaylist()
        // Chrome reports native HLS as "maybe", but that path can fail to demux
        // gateway playlists and jump to a live edge on resumed VOD. Prefer the
        // controlled MSE implementation; native HLS remains the fallback.
        if (playlist && Hls.isSupported()) {
            // A gateway VOD playlist can initially omit ENDLIST. Keep the
            // requested VOD origin instead of letting Hls choose a live-sync edge.
            hlsController = Hls(hlsPreferences(input.preferences.preferred_audio_language.orEmpty(),
                if ((input.preferences.subtitleMode != "never")) input.preferences.preferred_subtitle_language.orEmpty() else "",
                if (input.live) -1.0 else input.positionMillis.coerceAtLeast(0) / 1000.0)).also {
                val controller = it
                val event = Hls.Events.ERROR
                val listener = listenForFatalHlsError(controller, event) {
                    if (!disposed) latestObserve(PlayerFact.Failed)
                }
                removeHlsErrors = { removeHlsErrorListener(controller, event, listener) }
                it.subtitleDisplay = (input.preferences.subtitleMode != "never")
                it.loadSource(input.uri); it.attachMedia(video)
            }
        } else { video.src = input.uri }
        latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(null))))
        onDispose {
            disposed = true
            lifetime.active = false; lifetime.playRequest++
            tracks.values.forEach { track ->
                track.onload = null; track.onerror = null
                track.track.mode = TextTrackMode.DISABLED
            }
            blobs.forEach(::revokeSubtitleBlob)
            blobs.clear(); tracks.clear()
            video.onloadedmetadata = null; video.ontimeupdate = null; video.onerror = null; video.onended = null
            video.onplay = null; video.onpause = null; video.onwaiting = null; video.oncanplay = null
            removeHlsErrors?.invoke(); removeHlsErrors = null
            hlsController?.destroy(); hlsController = null
            video.pause(); video.removeAttribute("src"); video.load()
            container.removeChild(video)
            container.removeChild(captionStyle)
        }
    }
    // One effect owns selection, including cancellation while caption content
    // loads. The explicit choice (including Off) survives a gateway seek.
    LaunchedEffect(video, input, candidates, subtitleTouched, requestedSubtitle, subtitleRequest) {
        val candidate = if (subtitleTouched) requestedSubtitle?.let { matchingProviderSubtitle(it, candidates) }
            else preferredProviderSubtitle(input, candidates)
        if (candidate == null && (!subtitleTouched || requestedSubtitle != null)) return@LaunchedEffect
        val id = candidate?.let { input.providerTrackId(it.id) }
        try {
            selected = selectBrowserSubtitle(input, video, id, tracks, blobs, { providerCueLine(captionScale) }, suppressEmbedded = { hlsController?.subtitleDisplay = false }) { latestObserve(PlayerFact.ControlFailed) }
            latestObserve(PlayerFact.Tracks(PlaybackTracks(subtitles = input.providerTracks(selected))))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
    }
    LaunchedEffect(video, commands) {
        commands.collect { command ->
            try {
                when (command) {
                    is PlayerCommand.Seek -> if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                        else video.currentTime = command.positionMillis.coerceAtLeast(0) / 1000.0
                    is PlayerCommand.Volume -> video.volume = command.value.coerceIn(0f, 1f).toDouble()
                    is PlayerCommand.SubtitleSize -> captionScale = command.value.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
                    is PlayerCommand.SelectTrack -> {
                        require(command.kind == PlaybackTrackKind.Subtitle)
                        val candidate = command.id?.let { requireNotNull(input.providerSubtitle(it)) }
                        requestedSubtitle = candidate
                        subtitleTouched = true
                        subtitleRequest++
                    }
                    is PlayerCommand.Speed -> video.playbackRate = command.value.coerceIn(0.25, 2.0)
                }
                latestObserve(PlayerFact.Properties(video.volume.toFloat(), video.playbackRate, captionScale))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { latestObserve(PlayerFact.ControlFailed) }
        }
    }
    LaunchedEffect(paused, video) {
        val request = ++lifetime.playRequest
        if (paused) video.pause()
        else requestBrowserPlay(video,
            { if (lifetime.active && lifetime.playRequest == request) latestObserve(PlayerFact.Status("Paused")) },
            { if (lifetime.active && lifetime.playRequest == request) latestObserve(PlayerFact.Failed) })
    }
}
