package org.viptv.app.state

import org.viptv.app.connection.cardLookupKey
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal data class PlaybackKey(val title: String?, val source: String?)

internal fun playbackKey(title: Card?, source: SourceCandidate?): PlaybackKey? =
    if (title == null && source == null) null else PlaybackKey(title?.let(::cardLookupKey), source?.id)

data class PlaybackSegments(private val input: PlaybackInput? = null, private val ranges: List<TvSkipRange> = emptyList()) {
    internal fun of(playback: PlaybackInput?): List<TvSkipRange> = if (playback != null && playback === input) ranges else emptyList()
}

internal class CurrentPlayback(val key: PlaybackKey?, val input: PlaybackInput?, val segments: List<TvSkipRange>)

internal fun ApplicationState.currentPlayback(): CurrentPlayback {
    val key = playbackKey(sourceTitle, playingSource)
    val input = playback?.takeIf { it.owner == null || it.owner == key }
    return CurrentPlayback(key, input, playbackSegments.of(input))
}
