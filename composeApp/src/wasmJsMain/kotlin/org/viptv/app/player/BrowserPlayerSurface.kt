package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

@OptIn(ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)
@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit) {
    val video = remember(input.id) { (document.createElement("video") as HTMLVideoElement).apply {
        controls = true
        style.width = "100%"; style.height = "100%"
        setAttribute("playsinline", "")
    } }
    val latestObserve by rememberUpdatedState(observe)
    HtmlElementView(factory = { video }, modifier = modifier)
    DisposableEffect(video) {
        var hls: Hls? = null
        video.onloadedmetadata = { video.currentTime = input.positionMillis / 1000.0; null }
        video.ontimeupdate = { latestObserve(PlayerFact.Progress((video.currentTime * 1000).toLong(), video.duration.takeIf { it.isFinite() }?.let { (it * 1000).toLong() })); null }
        video.onerror = { _, _, _, _, _ -> latestObserve(PlayerFact.Failed); null }
        video.onended = { latestObserve(PlayerFact.Status("Ended")); null }
        val playlist = input.uri.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        if (playlist && video.canPlayType("application/vnd.apple.mpegurl").toString().isEmpty() && Hls.isSupported()) {
            hls = Hls().also { it.loadSource(input.uri); it.attachMedia(video) }
        } else { video.src = input.uri }
        onDispose {
            hls?.destroy()
            video.pause(); video.removeAttribute("src"); video.load()
            video.onloadedmetadata = null; video.ontimeupdate = null; video.onerror = null; video.onended = null
        }
    }
    LaunchedEffect(paused, video) { if (paused) video.pause() else video.play() }
}
