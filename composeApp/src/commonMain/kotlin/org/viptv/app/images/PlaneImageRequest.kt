package org.viptv.app.images

import coil3.PlatformContext
import coil3.request.ImageRequest

/** Backdrop plane decode: software bitmaps on Android so one-shot rasterization can draw them. */
internal expect fun planeImageRequest(context: PlatformContext, url: String?, width: Int, height: Int): ImageRequest
