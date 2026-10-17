package org.viptv.app.player

/** UI track IDs are session-local; provider URLs stay behind the content loader. */
internal fun PlaybackInput.providerSubtitle(id: Int?) = id?.let { track ->
    providerSubtitleId(track)?.let { selected -> providerSubtitles.value.find { it.id == selected } }
}

internal fun PlaybackInput.providerTracks(selected: String?) = providerSubtitles.value.map { candidate ->
    PlaybackTrack(providerTrackId(candidate.id), candidate.label, candidate.id == selected)
}

/** A new lease can issue new opaque caption IDs. Never guess among duplicate labels. */
internal fun matchingProviderSubtitle(previous: org.viptv.engine.wire.SubtitleCandidate,
    candidates: List<org.viptv.engine.wire.SubtitleCandidate>): org.viptv.engine.wire.SubtitleCandidate? {
    val sameChoice = candidates.filter { it.provider_id == previous.provider_id &&
        it.language == previous.language && it.label == previous.label }
    return sameChoice.singleOrNull { it.id == previous.id } ?: sameChoice.singleOrNull()
}
