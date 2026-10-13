package org.viptv.app.preview

import androidx.compose.runtime.Composable
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.TvLiveScreen

/** Isolated feed edge cases; these variants make no provider requests. */
@Composable
internal fun PreviewTvGuide(variant: String, watch: (LiveChannel) -> Unit, manage: () -> Unit) {
    val channels = when (variant) {
        "Empty" -> emptyList()
        "NoGuide" -> PreviewTvLive.channels.map { it.copy(programs = emptyList()) }
        else -> PreviewTvLive.channels
    }
    // Move the displayed window ahead of 'now' while retaining the live program in the feed.
    // The initial target must be a rendered future cell, not the off-window current cell.
    val start = PreviewLive.start + if (variant == "FutureGuide") 60 else 0
    TvLiveScreen(channels, start, PreviewLive.now, watch, manage, PreviewTvLive.categories)
}
