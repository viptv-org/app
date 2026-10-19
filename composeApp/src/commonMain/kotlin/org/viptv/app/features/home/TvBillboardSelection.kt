package org.viptv.app.features.home

import androidx.compose.runtime.State
import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class TvBillboardSelection(val card: Card, val portrait: Boolean)

/** Wait for focus to settle, then prepare both artwork and logo before replacing the summary. */
@Composable
internal fun rememberTvBillboardSelection(focused: State<Card?>, browsing: () -> Boolean, portrait: () -> Boolean): TvBillboardSelection? {
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
        delay(400)
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
