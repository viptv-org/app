package org.viptv.app.features.home

import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class HeroCarousel(val card: Card?, val index: Int, val count: Int,
    val progress: HeroProgress, val artworkReady: (String?) -> Unit)

private const val SlideMillis = 8000L

@Stable
internal class HeroProgress {
    var fraction by mutableFloatStateOf(0f)
        private set
    var steps by mutableIntStateOf(100)
    private var elapsed = 0L
    private var owner: Any? = null

    fun bind(slide: Any?) {
        if (slide == owner) return
        owner = slide; elapsed = 0L; fraction = 0f
    }

    suspend fun run(slide: Any?) {
        bind(slide)
        var last = withFrameMillis { it }
        while (true) {
            val resolution = steps.coerceAtLeast(1).toLong()
            val step = (elapsed * resolution / SlideMillis).coerceAtMost(resolution)
            fraction = step.toFloat() / resolution
            if (elapsed >= SlideMillis) return
            val boundary = ((step + 1) * SlideMillis + resolution - 1) / resolution
            delay((boundary - elapsed).coerceAtLeast(1))
            val now = withFrameMillis { it }
            elapsed = (elapsed + (now - last).coerceAtLeast(0)).coerceAtMost(SlideMillis)
            last = now
        }
    }
}

/** Artwork must be decoded before its eight-second display interval starts. */
@Composable
internal fun rememberHeroCarousel(cards: List<Card>, enabled: Boolean, scopeKey: Any?): HeroCarousel {
    var index by remember(scopeKey) { mutableIntStateOf(0) }
    var anchor by remember(scopeKey) { mutableStateOf<Any?>(null) }
    val anchored = anchor?.let { shown -> cards.indexOfFirst { org.viptv.app.connection.cardLookupKey(it) == shown }.takeIf { it >= 0 } }
    val active = if (cards.isEmpty()) 0 else anchored ?: (index % cards.size)
    val card = cards.getOrNull(active)
    val key = card?.let { org.viptv.app.connection.cardLookupKey(it) }
    LaunchedEffect(key) { if (key != null) { anchor = key; index = active } }
    val artwork = card?.landscape ?: card?.poster
    var ready by remember(scopeKey, artwork) { mutableStateOf(artwork == null) }
    val progress = remember(scopeKey) { HeroProgress() }
    val context = LocalPlatformContext.current
    LaunchedEffect(progress, key) { progress.bind(key) }
    LaunchedEffect(key, enabled, ready, cards.size) {
        if (enabled && ready && cards.size > 1) {
            progress.run(key)
            val next = (active + 1) % cards.size
            anchor = org.viptv.app.connection.cardLookupKey(cards[next]); index = next
        }
    }
    LaunchedEffect(key, enabled, ready, cards.size) {
        if (enabled && ready && cards.size > 1) {
            val next = cards[(active + 1) % cards.size]
            withTimeoutOrNull(6000) { coroutineScope {
                listOfNotNull((next.landscape ?: next.poster)?.let { it to (1920 to 1080) }, next.logo?.let { it to (420 to 160) })
                    .map { (url, size) -> async {
                        val request = ImageRequest.Builder(context).data(url).size(size.first, size.second)
                        if (url == next.logo) request.transformations(org.viptv.app.images.TrimLogo)
                        SingletonImageLoader.get(context).execute(request.build())
                    } }.awaitAll()
            } }
        }
    }
    return HeroCarousel(card, active, cards.size, progress, { if (it == artwork) ready = true })
}
