package org.viptv.app.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

internal fun interface AddonLogoSource {
    suspend fun load(path: String): ByteArray
}

internal val LocalAddonLogos = staticCompositionLocalOf<AddonLogoSource?> { null }

internal object AddonLogoCache {
    private const val LIMIT = 64
    private val gate = Mutex()
    private val loaded = LinkedHashMap<String, ByteArray>()
    private val failed = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()

    fun peek(path: String): ByteArray? = loaded[path]

    suspend fun load(path: String, source: AddonLogoSource): ByteArray? {
        gate.withLock {
            loaded[path]?.let { return it }
            failed[path]?.let { if (it.elapsedNow() < 1.minutes) return null }
        }
        val bytes = try { source.load(path) } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
        gate.withLock {
            if (bytes == null || bytes.isEmpty()) { failed[path] = TimeSource.Monotonic.markNow(); return null }
            failed.remove(path)
            loaded.remove(path); loaded[path] = bytes
            while (loaded.size > LIMIT) loaded.remove(loaded.keys.first())
        }
        return bytes
    }

    internal suspend fun clear() = gate.withLock { loaded.clear(); failed.clear() }
}

@Composable
internal fun AddonLogoTile(logo: String?, modifier: Modifier, logoModifier: Modifier = Modifier,
    fallback: @Composable BoxScope.() -> Unit) {
    val source = LocalAddonLogos.current
    val bytes by produceState(logo?.let(AddonLogoCache::peek), logo, source) {
        if (logo != null && source != null && value == null) value = AddonLogoCache.load(logo, source)
    }
    var shown by remember(bytes) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!shown) fallback()
        bytes?.let { data ->
            val context = LocalPlatformContext.current
            val request = remember(context, logo, data) {
                ImageRequest.Builder(context).data(data).memoryCacheKey("addon-logo:$logo")
                    .diskCachePolicy(CachePolicy.DISABLED).build()
            }
            AsyncImage(request, null, Modifier.fillMaxSize().then(logoModifier), contentScale = ContentScale.Fit,
                onSuccess = { shown = true }, onError = { shown = false })
        }
    }
}
