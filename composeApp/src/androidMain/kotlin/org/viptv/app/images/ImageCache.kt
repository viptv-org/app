package org.viptv.app.images

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath

internal actual fun imageCacheDirectory(context: PlatformContext): Path? =
    context.cacheDir.resolve("images").absolutePath.toPath()
