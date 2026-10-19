package org.viptv.app.images

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware

/** Hardware bitmaps cannot draw into the software canvas that composites the backdrop plane. */
internal actual fun planeImageRequest(context: PlatformContext, url: String?, width: Int, height: Int): ImageRequest =
    ImageRequest.Builder(context).data(url).size(width, height).crossfade(false).allowHardware(false).build()
