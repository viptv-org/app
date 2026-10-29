package org.viptv.app.images

import androidx.compose.runtime.Composable
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import okio.Path

internal expect fun imageCacheDirectory(context: PlatformContext): Path?

// setSafe refuses once Coil has built its default loader; replacing that default is the only way in.
@OptIn(DelicateCoilApi::class)
@Composable
internal fun ConfigureImageLoading(diagnostics: ((String) -> Unit)? = null) {
    val factory = SingletonImageLoader.Factory { context -> buildImageLoader(context, diagnostics) }
    try {
        SingletonImageLoader.setSafe(factory)
    } catch (_: IllegalStateException) {
        // The live loader is Coil's default: no artwork mapper, no app caches.
        SingletonImageLoader.setUnsafe(factory)
    }
}

private fun buildImageLoader(context: PlatformContext, diagnostics: ((String) -> Unit)?): ImageLoader =
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
