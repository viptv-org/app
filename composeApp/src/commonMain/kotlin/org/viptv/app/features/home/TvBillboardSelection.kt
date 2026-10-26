package org.viptv.app.features.home

import androidx.compose.runtime.State
import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class TvBillboardSelection(val card: Card, val portrait: Boolean)

// Coalesce held-key input, but prepare images immediately while native scrolling runs.
private const val SelectionSettleMillis = 220L
private const val QuietPollMillis = 16L

@Composable
internal fun rememberTvBillboardSelection(
    focused: State<Card?>,
    browsing: () -> Boolean,
    portrait: () -> Boolean,
    /** Milliseconds since the last navigation key event; null when nothing was pressed yet. */
    quietForMillis: () -> Long?,
    /** The pre-blurred soft-plane URL for a card, sized exactly as SoftArtwork requests it. */
    softArtwork: (Card) -> String? = { null },
    /**
     * True while a shelf or the rows column is animating. The swap waits for
     * calm *and* stillness: the decode + crossfade + semantics diff must never
     * land inside an animated frame — those are the 100 ms+ tail frames.
     */
    motionBusy: () -> Boolean = { false },
    resolve: (Card) -> Card = { it },
    enrich: (Card) -> Unit = {},
    settleMillis: () -> Long = { SelectionSettleMillis },
): TvBillboardSelection? {
    val context = LocalPlatformContext.current
    val raw = focused.value
    val card = raw?.let(resolve)
    LaunchedEffect(raw?.identity) {
        if (raw != null && (raw.logo == null || raw.description.isNullOrBlank())) {
            // Metadata requests for intermediate held-key selections otherwise
            // publish many Home updates during the active scroll animation.
            while (quietForMillis()?.let { it < settleMillis() } == true) delay(QuietPollMillis)
            enrich(raw)
        }
    }
    // These reads stay inside this composable so a focus/row change never
    // recomposes the whole home screen.
    val browsingNow = browsing()
    val portraitNow = portrait()
    var selected by remember { mutableStateOf<TvBillboardSelection?>(null) }
    LaunchedEffect(card, browsingNow, portraitNow) {
        if (!browsingNow) return@LaunchedEffect
        val card = card ?: return@LaunchedEffect
        withTimeoutOrNull(1200) {
            coroutineScope {
                listOfNotNull(
                    (if (portraitNow) card.poster else card.landscape ?: card.poster)?.let {
                        ImageRequest.Builder(context).data(it).size(if (portraitNow) 280 else 1160, if (portraitNow) 420 else 620).build()
                    },
                    card.logo?.let { org.viptv.app.images.logoRequest(context, it).newBuilder().size(420, 120).build() },
                    if (portraitNow) softArtwork(card)?.let { ImageRequest.Builder(context).data(it).size(480, 155).build() } else null,
                ).map { request -> async { SingletonImageLoader.get(context).execute(request) } }.awaitAll()
            }
        }
        while (true) {
            val quiet = quietForMillis()
            if ((quiet == null || quiet >= settleMillis()) && !motionBusy()) break
            delay(QuietPollMillis)
        }
        selected = TvBillboardSelection(card, portraitNow)
    }
    return selected
}
