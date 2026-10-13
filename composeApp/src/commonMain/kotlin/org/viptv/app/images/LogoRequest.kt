package org.viptv.app.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.Bitmap
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation

internal object TrimLogo : Transformation() {
    override val cacheKey = "viptv-transparent-logo-bounds-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = trimLogoBitmap(input)
}
internal expect fun trimLogoBitmap(input: Bitmap): Bitmap
internal fun logoRequest(context: PlatformContext, url: String?): ImageRequest =
    ImageRequest.Builder(context).data(url).transformations(TrimLogo).build()

@Composable
internal fun rememberLogoRequest(url: String?): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url) { logoRequest(context, url) }
}

internal data class LogoBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal fun logoBounds(width: Int, height: Int, alpha: (Int, Int) -> Int): LogoBounds? {
    if (width <= 0 || height <= 0 || width.toLong() * height > 2_000_000) return null
    var left = width; var top = height; var right = -1; var bottom = -1
    for (y in 0 until height) for (x in 0 until width) if (alpha(x, y) > 2) {
        left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
    }
    return if (right < left || (left == 0 && top == 0 && right == width - 1 && bottom == height - 1)) null
        else LogoBounds(left, top, right + 1, bottom + 1)
}
