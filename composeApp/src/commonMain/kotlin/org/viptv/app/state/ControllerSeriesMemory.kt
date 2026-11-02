package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal data class SeriesMemoryKey(val profile: String, val series: String)

internal fun ApplicationController.seriesMemoryOf(profile: String, card: Card?): SeriesMemory? =
    card?.let(::seriesKey)?.let { seriesMemory[SeriesMemoryKey(profile, it)] }

internal fun ApplicationController.rememberStartedSource(profile: String, card: Card?, source: SourceCandidate?) {
    if (profile.isEmpty()) return
    val key = card?.let(::seriesKey) ?: return
    val binge = source?.binge_group ?: return
    val provider = source.provider_id.takeIf { it.isNotEmpty() } ?: return
    seriesMemory[SeriesMemoryKey(profile, key)] = SeriesMemory(provider, binge)
}

private fun seriesKey(card: Card): String? {
    val series = card.parent_identity ?: card.identity.takeIf { it.kind == "series" && it.episode != null }
        ?.copy(episode = null) ?: return null
    return mediaLookupKey(series, card.metadata_origin, card.provider_id)
}
