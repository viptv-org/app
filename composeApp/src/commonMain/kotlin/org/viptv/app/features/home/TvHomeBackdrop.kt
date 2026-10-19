package org.viptv.app.features.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.viptv.app.images.planeImageRequest
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import kotlin.math.roundToInt

/**
 * The home backdrop as ONE pre-composited texture.
 *
 * The ambient artwork, its soft underlay and every gradient mask are drawn once
 * into a software bitmap when the artwork changes (off the UI thread); each frame
 * afterwards blits a single opaque bitmap instead of re-compositing five blended
 * full-screen layers. First render pays the composition; scrolling never does.
 */
@Composable
internal fun TvHomeBackdrop(card: Card?, browsing: Boolean, portrait: Boolean = false, hero: Card? = card, heroReady: (String?) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
        val scale = maxWidth / 1920.dp
        val widthPx = constraints.maxWidth
        val motion = animateFloatAsState(if (browsing) 1f else 0f, tween(450, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "Hero artwork departure")

        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val heroArt = hero?.landscape ?: hero?.poster
        val browseArt = if (portrait) card?.poster else card?.landscape ?: card?.poster
        val heroBitmap = rememberRasterizedPlane(widthPx, heroArt, heroReady) { canvasWidth, canvasHeight, painter, density, direction ->
            drawHeroPlane(painter, canvasWidth, canvasHeight, density, direction)
        }
        val browseBitmap = rememberRasterizedPlane(widthPx, browseArt, null) { canvasWidth, canvasHeight, painter, density, direction ->
            drawBrowsingPlane(painter, canvasWidth, canvasHeight, portrait, density, direction)
        }

        if (motion.value < 0.99f) heroBitmap?.let { bitmap ->
            Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - motion.value; translationY = -80.dp.toPx() * scale * motion.value
                clip = true
            }, contentScale = ContentScale.FillBounds)
        }
        if (motion.value > 0.01f) browseBitmap?.let { bitmap ->
            Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer {
                alpha = motion.value; translationY = 80.dp.toPx() * scale * (1f - motion.value)
                clip = true
            }, contentScale = ContentScale.FillBounds)
        }
    }
}

/**
 * Decode the artwork and rasterize the composed plane once per URL change.
 * The decode uses a software bitmap on Android so the offscreen canvas accepts it.
 */
@Composable
private fun rememberRasterizedPlane(canvasWidth: Int, url: String?, onReady: ((String?) -> Unit)?,
    draw: DrawScope.(canvasWidth: Int, canvasHeight: Int, painter: Painter, density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection) -> Unit): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val planeWidth = canvasWidth
    val planeHeight = (canvasWidth * 1080f / 1920f).roundToInt().coerceAtLeast(1)
    val context: PlatformContext = LocalContext.current
    val painter = rememberAsyncImagePainter(planeImageRequest(context, url, planeWidth, planeHeight), contentScale = ContentScale.Fit)
    val status by painter.state.collectAsState()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    LaunchedEffect(url, status) {
        if (status is coil3.compose.AsyncImagePainter.State.Success) {
            val composed = withContext(Dispatchers.Default) {
                val target = ImageBitmap(planeWidth, planeHeight)
                val canvas = Canvas(target)
                CanvasDrawScope().draw(density, direction, canvas, Size(planeWidth.toFloat(), planeHeight.toFloat())) {
                    draw(planeWidth, planeHeight, painter, density, direction)
                }
                target
            }
            bitmap = composed
            onReady?.invoke(url)
        }
    }
    return bitmap
}

/**
 * The ambient wash behind the rows: the artwork is drawn at 1/8 scale and
 * bilinear-upscaled back, twice — a progressive blur baked into the plane
 * bitmap once per artwork change. Zero per-frame cost.
 */
private fun DrawScope.softUnderlay(painter: Painter, boxW: Float, boxH: Float,
    density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection) {
    val w = (boxW / 8f).toInt().coerceAtLeast(1)
    val h = (boxH / 8f).toInt().coerceAtLeast(1)
    val small = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, direction, Canvas(small), Size(w.toFloat(), h.toFloat())) {
        drawCropped(painter, w.toFloat(), h.toFloat())
    }
    // Second pass at half the small size smooths the upscale further.
    val w2 = (w / 2f).toInt().coerceAtLeast(1)
    val h2 = (h / 2f).toInt().coerceAtLeast(1)
    val smaller = ImageBitmap(w2, h2)
    CanvasDrawScope().draw(density, direction, Canvas(smaller), Size(w2.toFloat(), h2.toFloat())) {
        drawImage(small, srcSize = IntSize(w, h), dstSize = IntSize(w2, h2), filterQuality = FilterQuality.Medium)
    }
    drawImage(smaller, srcSize = IntSize(w2, h2),
        dstOffset = IntOffset.Zero, dstSize = IntSize(boxW.toInt(), boxH.toInt()),
        alpha = 0.45f, filterQuality = FilterQuality.Medium)
}

/**
 * Aspect-fill (center-crop) draw: the painter is scaled to cover the box and
 * clipped, exactly like ContentScale.Crop — never stretched.
 */
private fun DrawScope.drawCropped(painter: Painter, boxW: Float, boxH: Float, alpha: Float = 1f) {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.width <= 0f || intrinsic.height <= 0f) {
        with(painter) { draw(Size(boxW, boxH), alpha) }
        return
    }
    val scale = maxOf(boxW / intrinsic.width, boxH / intrinsic.height)
    val drawW = intrinsic.width * scale
    val drawH = intrinsic.height * scale
    clipRect(right = boxW, bottom = boxH) {
        translate(left = (boxW - drawW) / 2f, top = (boxH - drawH) / 2f) {
            with(painter) { draw(Size(drawW, drawH), alpha) }
        }
    }
}

/** Hero mode: full-bleed artwork with the left-to-right and bottom scrims baked in. */
private fun DrawScope.drawHeroPlane(painter: Painter, canvasWidth: Int, canvasHeight: Int,
    density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection) {
    drawCropped(painter, size.width, size.height)
    drawRect(Brush.horizontalGradient(
        0f to Palette.background.copy(alpha = .86f),
        .36f to Palette.background.copy(alpha = .4f),
        .7f to Color.Transparent))
    drawRect(Brush.verticalGradient(
        0f to Palette.background.copy(alpha = .3f),
        .45f to Color.Transparent,
        .8f to Palette.background.copy(alpha = .9f),
        1f to Palette.background))
}

/** Browsing mode, matching the layered original: portrait keeps the soft poster
 * wash plus its corner card; landscape shows the sharp art panel with masks. */
private fun DrawScope.drawBrowsingPlane(painter: Painter, canvasWidth: Int, canvasHeight: Int, portrait: Boolean,
    density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection) {
    drawRect(Palette.background)
    val bandHeight = size.height * (620f / 1080f)
    if (portrait) {
        // Soft blurred poster wash across the band (scaled 1.2 in the original).
        scale(scaleX = 1.2f, scaleY = 1.2f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            softUnderlay(painter, size.width / 1.2f, bandHeight / 1.2f, density, direction)
        }
        // Poster card on the right edge.
        val cardW = size.width * (280f / 1920f)
        val cardH = size.width * (420f / 1920f)
        val cardX = size.width * (1520f / 1920f)
        val cardY = size.height * (96f / 1080f)
        translate(left = cardX, top = cardY) { drawCropped(painter, cardW, cardH) }
    } else {
        val panelX = size.width * (760f / 1920f)
        val panelW = size.width * (1160f / 1920f)
        translate(left = panelX, top = 0f) { drawCropped(painter, panelW, bandHeight) }
        drawRect(Brush.horizontalGradient(
            0.52f to Color.Transparent, 1f to Palette.background),
            topLeft = Offset(panelX + panelW * .52f, 0f), size = Size(panelW * .48f, bandHeight))
    }
    // Bottom fade under the rows (both modes).
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Palette.background),
        topLeft = Offset(0f, size.height * (380f / 1080f)), size = Size(size.width, size.height * (240f / 1080f)))
}
