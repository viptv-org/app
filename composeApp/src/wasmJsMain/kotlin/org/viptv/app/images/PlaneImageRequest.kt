package org.viptv.app.images

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Skia bitmaps draw into software canvases directly. */
internal actual fun planeImageRequest(context: PlatformContext, url: String?, width: Int, height: Int): ImageRequest =
    ImageRequest.Builder(context).data(url).size(width, height).crossfade(false).build()
