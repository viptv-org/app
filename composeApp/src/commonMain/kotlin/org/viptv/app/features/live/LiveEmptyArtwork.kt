package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.viptv.app.resources.Res
import org.viptv.app.theme.Scrim

internal const val LiveEmptyWideArt = "files/live/live-empty-wide.webp"
internal const val LiveEmptyTallArt = "files/live/live-empty-tall.webp"

internal val LocalLiveEmptyArt = staticCompositionLocalOf<String?> { null }

// The scrim draws in code over the photo: drop it and the light text loses contrast over bright art.
@Composable
internal fun LiveEmptyArtwork(wide: Boolean, modifier: Modifier = Modifier, art: String? = null) {
    Box(modifier.fillMaxSize()) {
        AsyncImage(art ?: LocalLiveEmptyArt.current ?: Res.getUri(if (wide) LiveEmptyWideArt else LiveEmptyTallArt), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
        Box(Modifier.fillMaxSize().background(if (wide) Scrim.liveEmptyWide else Scrim.liveEmptyTall))
    }
}

// The band ends on the page ground, so its scrim has to reach the background at its bottom edge.
@Composable
internal fun LiveEmptyPhoneArt(modifier: Modifier = Modifier) {
    Box(modifier) {
        AsyncImage(Res.getUri(LiveEmptyTallArt), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
        Box(Modifier.fillMaxSize().background(Scrim.liveEmptyPhone))
    }
}

