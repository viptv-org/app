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

private data class LoadedArtwork(val url: String, val painter: Painter)

/** Keep decoded artwork visible during loading; fade bitmap paints without a full-size offscreen texture. */
@Composable
internal fun BufferedArtwork(url: String?, modifier: Modifier, alignment: Alignment = Alignment.Center, onReady: (String?) -> Unit = {}) {
    val size = rememberConstraintsSizeResolver()
    val incoming = rememberAsyncImagePainter(ImageRequest.Builder(LocalPlatformContext.current).data(url).size(size).build(), contentScale = ContentScale.Crop)
    val status by incoming.state.collectAsState()
    var shown by remember { mutableStateOf<LoadedArtwork?>(null) }
    var outgoing by remember { mutableStateOf<LoadedArtwork?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(url, status) {
        if (url == null) { shown = null; outgoing = null; return@LaunchedEffect }
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
