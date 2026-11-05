package org.viptv.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.request.Options
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// Coil must not fetch on Dispatchers.IO during a synchronously-driven scene, or its state write races recomposition.
private class DeterministicFetcher : Fetcher {
    override suspend fun fetch(): FetchResult = throw IOException("Image loading is disabled in render tests.")
}

private class DeterministicFetcherFactory : Fetcher.Factory<coil3.Uri> {
    override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher = DeterministicFetcher()
}

@OptIn(DelicateCoilApi::class)
class DeterministicImageLoaderRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val context = PlatformContext.INSTANCE
            val previous = runCatching { SingletonImageLoader.get(context) }.getOrNull()
            SingletonImageLoader.setUnsafe { imageLoaderContext ->
                ImageLoader.Builder(imageLoaderContext)
                    .coroutineContext(Dispatchers.Unconfined)
                    .fetcherCoroutineContext(Dispatchers.Unconfined)
                    .decoderCoroutineContext(Dispatchers.Unconfined)
                    .components { add(DeterministicFetcherFactory()) }
                    .build()
            }
            try {
                base.evaluate()
            } finally {
                val installed = runCatching { SingletonImageLoader.get(context) }.getOrNull()
                if (previous == null) SingletonImageLoader.reset() else SingletonImageLoader.setUnsafe(previous)
                if (installed !== previous) runCatching { installed?.shutdown() }
            }
        }
    }
}