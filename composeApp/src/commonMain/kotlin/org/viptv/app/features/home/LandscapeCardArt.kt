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
 * The fill is the same poster decoded a few pixels wide and upscaled with
 * bilinear filtering, which reads as a blur. That costs one tiny bitmap and no
 * blur pass, so it is safe on a TV shelf (docs/TV_FRAME_BUDGET.md), looks the
 * same on Android below 12 (where `Modifier.blur` does nothing), and shares the
 * poster's download.
 */
@Composable
internal fun PosterInLandscape(
    poster: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
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
            contentScale = ContentScale.Crop, onError = onError)
    }
}

/** Decode width of the soft fill; small enough that upscaling it is the blur. */
private const val SoftFillWidth = 16

/** Darkens the fill so the centred poster reads as the subject. */
private val SoftFillScrim = Color.Black.copy(alpha = .45f)
