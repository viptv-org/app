package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.viptv.engine.wire.Card

internal sealed interface LandscapeArt {
    data class Still(val url: String) : LandscapeArt
    data class Poster(val url: String) : LandscapeArt
    data object None : LandscapeArt
}

internal fun Card.landscapeArt(): LandscapeArt =
    landscape?.takeIf { it.isNotBlank() }?.let { LandscapeArt.Still(it) }
        ?: poster?.takeIf { it.isNotBlank() }?.let { LandscapeArt.Poster(it) }
        ?: LandscapeArt.None

@Composable
internal fun PosterInLandscape(
    poster: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onSuccess: (() -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        val context = LocalPlatformContext.current
        val softFill = remember(context, poster) {
            ImageRequest.Builder(context).data(poster).size(SoftFillWidth, SoftFillWidth * 3 / 2).build()
        }
        AsyncImage(softFill, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(SoftFillScrim))
        AsyncImage(poster, contentDescription, Modifier.fillMaxHeight().aspectRatio(2f / 3).align(Alignment.Center),
            contentScale = ContentScale.Crop, onSuccess = { onSuccess?.invoke() }, onError = onError)
    }
}

private const val SoftFillWidth = 16

private val SoftFillScrim = Color.Black.copy(alpha = .45f)
