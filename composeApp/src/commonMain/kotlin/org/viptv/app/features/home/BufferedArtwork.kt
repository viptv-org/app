package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.*
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull

internal data class LoadedArtwork(val url: String, val painter: Painter)

/** Keep decoded artwork visible during loading; fade bitmap paints without a full-size offscreen texture. */
@Composable
internal fun BufferedArtwork(url: String?, modifier: Modifier, alignment: Alignment = Alignment.Center, onReady: (String?) -> Unit = {}, onError: (String) -> Unit = {}) {
    if (!org.viptv.app.components.LocalTvFocusAnchoring.current) {
        LegacyBufferedArtwork(url, modifier, alignment, onReady, onError)
        return
    }
    val size = rememberConstraintsSizeResolver()
    val incoming = rememberAsyncImagePainter(ImageRequest.Builder(LocalPlatformContext.current).data(url).size(size).build(), contentScale = ContentScale.Crop)
    val status by incoming.state.collectAsState()
    LaunchedEffect(url, status) {
        if (status is AsyncImagePainter.State.Error && url != null) onError(url)
    }
    val ready = (status as? AsyncImagePainter.State.Success)?.takeIf { it.result.request.data == url }
    ArtworkDissolve(if (ready != null && url != null) LoadedArtwork(url, ready.painter) else null,
        modifier.then(size), alignment, onReady)
}

@Composable
internal fun ArtworkDissolve(ready: LoadedArtwork?, modifier: Modifier, alignment: Alignment = Alignment.Center, onReady: (String?) -> Unit = {}) {
    var shown by remember { mutableStateOf<LoadedArtwork?>(null) }
    var outgoing by remember { mutableStateOf<LoadedArtwork?>(null) }
    val fade = remember { Animatable(1f) }
    val latest by rememberUpdatedState(ready)
    val readyCallback by rememberUpdatedState(onReady)
    // Finish each short dissolve before taking the newest ready image. Cancelling
    // midway promotes a partially transparent image to opaque and visibly flashes.
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.filterNotNull().conflate().collect { ready ->
            if (ready.url != shown?.url) {
                outgoing = shown
                fade.snapTo(if (outgoing == null) 1f else 0f)
                shown = ready
                readyCallback(ready.url)
                fade.animateTo(1f, tween(180))
                outgoing = null
            }
        }
    }
    Box(modifier) {
        // Source-over dissolve: keep the opaque base beneath the incoming
        // image. Fading both layers exposes the dark background at mid-transition.
        outgoing?.let { Image(it.painter, null, Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f; compositingStrategy = CompositingStrategy.ModulateAlpha
        }, contentScale = ContentScale.Crop, alignment = alignment) }
        shown?.let { Image(it.painter, null, Modifier.fillMaxSize().graphicsLayer {
            alpha = fade.value; compositingStrategy = CompositingStrategy.ModulateAlpha
        }, contentScale = ContentScale.Crop, alignment = alignment) }
    }
}

// Non-TV hero surfaces retain their existing timing and request behavior.
@Composable
private fun LegacyBufferedArtwork(url: String?, modifier: Modifier, alignment: Alignment = Alignment.Center, onReady: (String?) -> Unit = {}, onError: (String) -> Unit = {}) {
    val size = rememberConstraintsSizeResolver()
    val incoming = rememberAsyncImagePainter(ImageRequest.Builder(LocalPlatformContext.current).data(url).size(size).build(), contentScale = ContentScale.Crop)
    val status by incoming.state.collectAsState()
    var shown by remember { mutableStateOf<LoadedArtwork?>(null) }
    var outgoing by remember { mutableStateOf<LoadedArtwork?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(url, status) {
        if (url == null) { shown = null; outgoing = null; return@LaunchedEffect }
        if (status is AsyncImagePainter.State.Error && url != null) onError(url)
        val ready = status as? AsyncImagePainter.State.Success
        if (ready != null && ready.result.request.data == url && shown?.url != url) {
            outgoing = shown
            fade.snapTo(if (outgoing == null) 1f else 0f)
            shown = LoadedArtwork(url, ready.painter)
            onReady(url)
        }
    }
    LaunchedEffect(shown) {
        if (shown != null) { fade.animateTo(1f, tween(200)); outgoing = null }
    }
    Box(modifier.then(size)) {
        // True crossfade: the outgoing plane leaves as the incoming one
        // arrives. Holding the outgoing at full alpha while the incoming fades
        // in stacked both images — a bright flash mid-swap, then a hard cut.
        outgoing?.let { Image(it.painter, null, Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - fade.value; compositingStrategy = CompositingStrategy.ModulateAlpha
        }, contentScale = ContentScale.Crop, alignment = alignment) }
        shown?.let { Image(it.painter, null, Modifier.fillMaxSize().graphicsLayer {
            alpha = fade.value; compositingStrategy = CompositingStrategy.ModulateAlpha
        }, contentScale = ContentScale.Crop, alignment = alignment) }
    }
}
