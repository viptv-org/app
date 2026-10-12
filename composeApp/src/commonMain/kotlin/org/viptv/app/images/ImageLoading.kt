package org.viptv.app.images

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path

internal expect fun imageCacheDirectory(context: PlatformContext): Path?

@Composable
internal fun ConfigureImageLoading() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
            .diskCache {
                imageCacheDirectory(context)?.let { directory ->
                    DiskCache.Builder().directory(directory).maxSizeBytes(256L * 1024 * 1024).build()
                }
            }
            .build()
    }
}
