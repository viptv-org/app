package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class HeroCarousel(val card: Card?, val index: Int, val count: Int,
    val progress: () -> Float, val artworkReady: (String?) -> Unit)

/** Artwork must be decoded before its eight-second display interval starts. */
@Composable
internal fun rememberHeroCarousel(cards: List<Card>, enabled: Boolean, scopeKey: Any?): HeroCarousel {
    var index by remember(scopeKey) { mutableIntStateOf(0) }
    val active = if (cards.isEmpty()) 0 else index % cards.size
    val card = cards.getOrNull(active)
    val key = card?.identity?.let { "${it.kind}:${it.id}" }
    val artwork = card?.landscape ?: card?.poster
    var ready by remember(scopeKey, artwork) { mutableStateOf(artwork == null) }
    val progress = remember(scopeKey) { Animatable(0f) }
    val context = LocalPlatformContext.current
    LaunchedEffect(key) { progress.snapTo(0f) }
    LaunchedEffect(key, enabled, ready, cards.size) {
        if (enabled && ready && cards.size > 1) {
            val remaining = ((1f - progress.value) * 8000).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
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
    return HeroCarousel(card, active, cards.size, { progress.value }, { if (it == artwork) ready = true })
}
