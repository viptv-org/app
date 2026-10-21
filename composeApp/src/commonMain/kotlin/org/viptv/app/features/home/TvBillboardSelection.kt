package org.viptv.app.features.home

import androidx.compose.runtime.State
import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class TvBillboardSelection(val card: Card, val portrait: Boolean)

/**
 * How long navigation must be quiet before the billboard follows the focus.
 *
 * Comfortably longer than a held D-pad's repeat interval (~100 ms) and than the
 * gap between quick taps, so a held button never starts a swap: an artwork swap
 * mid-scroll re-decodes and re-animates the backdrop on every press, which is
 * what made fast navigation stutter.
 */
private const val SelectionSettleMillis = 220L

/** Poll interval while waiting for the D-pad to go quiet. */
private const val QuietPollMillis = 16L

/**
 * Follows the focus in two stages so fast navigation stays smooth AND the
 * billboard actually keeps up:
 *
 * 1. The moment a card is focused, every artwork URL the billboard planes will
 *    need (sharp art, logo, and the pre-blurred soft-plane URL) is decoded into
 *    the image cache. Decoding is off the frame path, so a held key can warm
 *    several cards without touching a frame.
 * 2. Only once navigation has been quiet for [SelectionSettleMillis] does the
 *    visible swap happen. The planes fade in from warm cache entries together,
 *    instead of each popping in as its own fetch finishes.
 */
@Composable
internal fun rememberTvBillboardSelection(
    focused: State<Card?>,
    browsing: () -> Boolean,
    portrait: () -> Boolean,
    /** Milliseconds since the last navigation key event; null when nothing was pressed yet. */
    quietForMillis: () -> Long?,
    /** The pre-blurred soft-plane URL for a card, sized exactly as SoftArtwork requests it. */
    softArtwork: (Card) -> String? = { null },
): TvBillboardSelection? {
    val context = LocalPlatformContext.current
    val card = focused.value
    // These reads stay inside this composable so a focus/row change never
    // recomposes the whole home screen.
    val browsingNow = browsing()
    val portraitNow = portrait()
    var selected by remember { mutableStateOf<TvBillboardSelection?>(null) }
    LaunchedEffect(card, browsingNow, portraitNow) {
        if (!browsingNow) return@LaunchedEffect
        val warm = card ?: return@LaunchedEffect
        coroutineScope {
            listOfNotNull(
                (if (portraitNow) warm.poster else warm.landscape ?: warm.poster)?.let {
                    ImageRequest.Builder(context).data(it).size(if (portraitNow) 280 else 1160, if (portraitNow) 420 else 620).build()
                },
                warm.logo?.let { org.viptv.app.images.logoRequest(context, it).newBuilder().size(420, 120).build() },
                // The blurred plane's proxied URL is its own cache entry; left
                // cold it pops in a beat after the sharp art. The small decode
                // size only samples; the URL key is what is warmed. Portrait
                // shelves are the only ones with a soft plane.
                if (portraitNow) softArtwork(warm)?.let { ImageRequest.Builder(context).data(it).size(480, 155).build() } else null,
            ).map { request -> async { SingletonImageLoader.get(context).execute(request) } }.awaitAll()
        }
    }
    LaunchedEffect(card, browsingNow, portraitNow) {
        if (!browsingNow) return@LaunchedEffect
        val card = card ?: return@LaunchedEffect
        // Never swap artwork while the D-pad is held or clicked in quick
        // succession: that is what made fast navigation feel like it froze.
        // The swap happens once navigation goes quiet, like a key release.
        // BufferedArtwork keeps the outgoing plane until the incoming bitmap is
        // ready, so swapping as soon as we settle cannot flash an empty plane.
        while (true) {
            val quiet = quietForMillis()
            if (quiet == null || quiet >= SelectionSettleMillis) break
            delay(QuietPollMillis)
        }
        selected = TvBillboardSelection(card, portraitNow)
    }
    return selected
}
