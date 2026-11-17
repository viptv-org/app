package org.viptv.app.features.player

import org.viptv.engine.wire.Card

data class TvPlaybackExtras(
    val sourceLabel: String? = null,
    val sourceQuality: String? = null,
    val endTime: String? = null,
    val bufferLabel: String? = null,
    val skips: List<TvSkipRange> = emptyList(),
    val nextEpisode: Card? = null,
    val episodes: List<Card> = emptyList(),
    val thumbnails: List<TvScrubThumbnail> = emptyList(),
    val fallbackLabel: String? = null,
    val failureDescription: String? = null,
    val creditsLabel: String? = null,
    val skipIntros: Boolean = false,
    val skipCredits: Boolean = false,
)
data class TvSkipRange(val startMillis: Long, val endMillis: Long, val kind: TvSkipKind = TvSkipKind.Intro, val mixed: Boolean = false) {
    val label: String get() = when (kind) {
        TvSkipKind.Intro -> "Skip intro"
        TvSkipKind.Recap -> "Skip recap"
        TvSkipKind.Credits -> "Skip credits"
    }
}
enum class TvSkipKind { Intro, Recap, Credits }
data class TvScrubThumbnail(val startMillis: Long, val endMillis: Long, val image: String)
internal enum class TvPlayerScene { Controls, Buffer, Scrub, Tracks, Skip, Next, Error }
