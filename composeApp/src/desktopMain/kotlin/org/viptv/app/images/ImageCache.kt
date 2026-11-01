package org.viptv.app.images

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath

internal actual fun imageCacheDirectory(context: PlatformContext): Path? {
    if (java.lang.Boolean.getBoolean("viptv.disableImageDiskCache")) return null

    val base = when {
        System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        System.getProperty("os.name").startsWith("Mac") -> System.getProperty("user.home") + "/Library/Caches"
        else -> System.getenv("XDG_CACHE_HOME") ?: System.getProperty("user.home") + "/.cache"
    }
    return "$base/viptv-experiment/images".toPath()
}
