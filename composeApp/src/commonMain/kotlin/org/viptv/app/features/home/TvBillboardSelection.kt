package org.viptv.app.features.home

import androidx.compose.runtime.*
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.*
import org.viptv.engine.wire.Card

internal data class TvBillboardSelection(val card: Card, val portrait: Boolean)

/** Wait for focus to settle, then prepare both artwork and logo before replacing the summary. */
@Composable
internal fun rememberTvBillboardSelection(focused: Card?, browsing: Boolean, portrait: Boolean): TvBillboardSelection? {
    val context = LocalPlatformContext.current
    var selected by remember { mutableStateOf<TvBillboardSelection?>(null) }
    LaunchedEffect(focused, browsing, portrait) {
        if (!browsing) return@LaunchedEffect
        val card = focused ?: return@LaunchedEffect
        delay(400)
        withTimeoutOrNull(1900) {
            coroutineScope {
                listOfNotNull(if (portrait) card.poster else card.landscape ?: card.poster, card.logo).distinct().map { url -> async {
                    SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).size(1160, 620).build())
                } }.awaitAll()
            }
        }
        selected = TvBillboardSelection(card, portrait)
    }
    return selected
}
