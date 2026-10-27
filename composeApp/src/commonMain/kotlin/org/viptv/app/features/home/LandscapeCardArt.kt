package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.viptv.engine.wire.Card

/**
 * The art a 16:9 card shows (app#62). Imported rows (Stremio library) often carry
 * only a portrait poster, and a landscape card that read `landscape` alone drew
 * nothing at all. The poster is the fallback; only a card with neither image is
 * [None] and keeps the card's own no-art treatment.
 */
internal sealed interface LandscapeArt {
    /** A real 16:9 still: drawn cropped, as before. */
    data class Still(val url: String) : LandscapeArt
    /** Only a 2:3 poster: drawn by [PosterInLandscape], never stretched to fill. */
    data class Poster(val url: String) : LandscapeArt
    data object None : LandscapeArt
}

internal fun Card.landscapeArt(): LandscapeArt =
    landscape?.takeIf { it.isNotBlank() }?.let { LandscapeArt.Still(it) }
        ?: poster?.takeIf { it.isNotBlank() }?.let { LandscapeArt.Poster(it) }
        ?: LandscapeArt.None

/**
 * A portrait poster inside a 16:9 frame: the poster centred at full height over a
 * soft, darkened fill of itself (components.md §6: "Never stretch a small image
 * to fill").
 *
 * The fill is soft in one of two ways, so a TV shelf never pays a runtime blur per
 * card (docs/TV_FRAME_BUDGET.md):
 * - [runtimeBlur] set: [backdrop] is drawn through a blur pass. Callers pass null
 *   when the image service already returned a blurred bitmap.
 * - [runtimeBlur] null and [tinyBackdrop]: the poster is decoded at a few pixels
 *   wide and upscaled with bilinear filtering, which reads as a blur for the cost
 *   of a tiny bitmap.
 */
@Composable
internal fun PosterInLandscape(
    poster: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backdrop: String = poster,
    runtimeBlur: Dp? = null,
    tinyBackdrop: Boolean = false,
    onError: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        val context = LocalPlatformContext.current
        val fill: Any = if (tinyBackdrop && runtimeBlur == null)
            remember(backdrop) { ImageRequest.Builder(context).data(backdrop).size(16, 24).build() }
            else backdrop
        AsyncImage(fill, null, Modifier.matchParentSize().then(runtimeBlur?.let { Modifier.blur(it) } ?: Modifier),
            contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(PosterFillScrim))
        AsyncImage(poster, contentDescription, Modifier.fillMaxHeight().aspectRatio(2f / 3).align(Alignment.Center),
            contentScale = ContentScale.Crop, onError = onError?.let { handler -> { _: AsyncImagePainter.State.Error -> handler() } })
    }
}

/** Darkens the fill so the centred poster reads as the subject. */
private val PosterFillScrim = Color.Black.copy(alpha = .45f)
