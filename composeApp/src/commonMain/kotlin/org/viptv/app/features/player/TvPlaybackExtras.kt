package org.viptv.app.features.player

import org.viptv.engine.wire.Card

data class TvPlaybackExtras(
    val sourceLabel: String? = null,
    val sourceQuality: String? = null,
    val endTime: String? = null,
    val bufferLabel: String? = null,
    val skip: TvSkipRange? = null,
    val nextEpisode: Card? = null,
    val episodes: List<Card> = emptyList(),
    val thumbnails: List<TvScrubThumbnail> = emptyList(),
    val fallbackLabel: String? = null,
    val failureDescription: String? = null,
    val creditsLabel: String? = null,
)
data class TvSkipRange(val startMillis: Long, val endMillis: Long, val label: String = "Skip intro")
data class TvScrubThumbnail(val startMillis: Long, val endMillis: Long, val image: String)
internal enum class TvPlayerScene { Controls, Buffer, Scrub, Tracks, Skip, Next, Error }
