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

/**
 * How far the current slide is through its display interval.
 *
 * [fraction] only changes when the drawn fill would move by at least one pixel
 * ([steps] is the indicator's fill width in pixels), and the timer sleeps between
 * those steps instead of running a per-frame animation. On a software-rendered
 * TV, a per-vsync progress bar re-rasterised the whole window ~40 times a second.
 */
@Stable
internal class HeroProgress {
    /** Drawn fill, a multiple of `1 / steps`. */
    var fraction by mutableFloatStateOf(0f)
        private set
    /** Fill resolution; the indicator sets it to its own width in pixels. */
    var steps by mutableIntStateOf(100)
    private var elapsed = 0L
    private var owner: Any? = null

    /** Restarts the interval when the slide changes; the same slide keeps its elapsed time. */
    fun bind(slide: Any?) {
        if (slide == owner) return
        owner = slide; elapsed = 0L; fraction = 0f
    }

    /**
     * Runs the rest of the interval, resuming from where a cancelled run stopped
     * (at the last drawn step, so the bar never jumps backwards or forwards).
     * Time comes from the frame clock, sampled once per step.
     */
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
    val active = if (cards.isEmpty()) 0 else index % cards.size
    val card = cards.getOrNull(active)
    val key = card?.let { org.viptv.app.connection.cardLookupKey(it) }
    val artwork = card?.landscape ?: card?.poster
    var ready by remember(scopeKey, artwork) { mutableStateOf(artwork == null) }
    val progress = remember(scopeKey) { HeroProgress() }
    val context = LocalPlatformContext.current
    LaunchedEffect(progress, key) { progress.bind(key) }
    LaunchedEffect(key, enabled, ready, cards.size) {
        if (enabled && ready && cards.size > 1) {
            progress.run(key)
            index = (active + 1) % cards.size
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
