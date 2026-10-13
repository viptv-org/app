package org.viptv.app.player

/** UI track IDs are session-local; provider URLs stay behind the content loader. */
internal fun PlaybackInput.providerSubtitle(id: Int?) = id?.let { track ->
    val index = track.toLong() - Int.MIN_VALUE.toLong()
    providerSubtitles.value.getOrNull(index.toInt().takeIf { index in 0 until providerSubtitles.value.size.toLong() } ?: -1)
}

internal fun PlaybackInput.providerTracks(selected: String?) = providerSubtitles.value.mapIndexed { index, candidate ->
    PlaybackTrack(Int.MIN_VALUE + index, candidate.label, candidate.id == selected)
}
