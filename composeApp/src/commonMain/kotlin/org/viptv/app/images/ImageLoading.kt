package org.viptv.app.images

import androidx.compose.runtime.Composable
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path

internal expect fun imageCacheDirectory(context: PlatformContext): Path?

private var imageLoaderConfigured = false

@Composable
internal fun ConfigureImageLoading(diagnostics: ((String) -> Unit)? = null) {
    // Coil refuses a second factory once its singleton loader exists. Preview
    // scenes and UI tests compose this more than once per process.
    if (imageLoaderConfigured) return
    imageLoaderConfigured = true
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(PublicArtworkMapper()) }
            .eventListenerFactory(object : EventListener.Factory {
                override fun create(request: ImageRequest): EventListener = diagnostics?.let { ImageDiagnostics(it) } ?: EventListener.NONE
            })
            .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
            .diskCache {
                imageCacheDirectory(context)?.let { directory ->
                    DiskCache.Builder().directory(directory).maxSizeBytes(256L * 1024 * 1024).build()
                }
            }
            .build()
    }
}
