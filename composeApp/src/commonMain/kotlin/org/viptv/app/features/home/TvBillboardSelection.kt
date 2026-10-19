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
 * Waits for focus to settle briefly, then swaps in the focused card's artwork.
 *
 * The settle window is short (a fast scroll still skips every swap, so nothing
 * thrashes) and the artwork is already warm: the focused card's images are
 * decoded ahead of time, so the backdrop follows the focus almost immediately
 * instead of a beat behind it.
 */
@Composable
internal fun rememberTvBillboardSelection(
    focused: State<Card?>,
    browsing: () -> Boolean,
    portrait: () -> Boolean,
    /** Milliseconds since the last navigation key event; null when nothing was pressed yet. */
    quietForMillis: () -> Long?,
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
        val card = card ?: return@LaunchedEffect
        // Never swap artwork while the D-pad is held or clicked in quick
        // succession: that is what made fast navigation feel like it froze.
        // The swap happens once navigation goes quiet, like a key release.
        while (true) {
            val quiet = quietForMillis()
            if (quiet == null || quiet >= SelectionSettleMillis) break
            delay(QuietPollMillis)
        }
        withTimeoutOrNull(1900) {
            coroutineScope {
                val artwork = if (portraitNow) card.poster else card.landscape ?: card.poster
                val requests = listOfNotNull(
                    artwork?.let { ImageRequest.Builder(context).data(it).size(if (portraitNow) 280 else 1160, if (portraitNow) 420 else 620).build() },
                    card.logo?.let { org.viptv.app.images.logoRequest(context, it).newBuilder().size(420, 120).build() },
                )
                requests.map { request -> async { SingletonImageLoader.get(context).execute(request) } }.awaitAll()
            }
        }
        selected = TvBillboardSelection(card, portraitNow)
    }
    return selected
}
