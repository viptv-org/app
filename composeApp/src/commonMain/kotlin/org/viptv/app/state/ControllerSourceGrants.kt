package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.SourceCandidate

internal fun Exception.expiredSourceGrant(): Boolean = this is RemoteFailure && status == 404 && code == "source_not_found"

internal fun SourceCandidate.sameStream(other: SourceCandidate): Boolean =
    provider_id == other.provider_id && label == other.label && detail == other.detail &&
        presentation?.source_id == other.presentation?.source_id && presentation?.size_bytes == other.presentation?.size_bytes

internal fun RankedSources.equivalentOf(stale: SourceCandidate): SourceCandidate? =
    stale.source_fingerprint?.let { wanted -> rows.firstOrNull { it.source_fingerprint == wanted } }
        ?: rows.firstOrNull { it.sameStream(stale) }
        ?: stale.binge_group?.let { group -> rows.firstOrNull { it.provider_id == stale.provider_id && it.binge_group == group } }
        ?: autoplayTarget()

// Discovery grants expire on the server: a source that outlived its grant is found again by what it plays, never by its ID.
internal suspend fun ApplicationController.rediscoveredSource(stale: SourceCandidate): SourceCandidate? {
    val remote = connection ?: return null
    val profile = mutable.value.selectedProfile ?: return null
    val card = mutable.value.sourceTitle ?: return null
    val context = rankingContext(seriesMemoryOf(profile, card), sourceMemory.affinity(profile), capability)
    val outcome = remote.sources(card, SourceReuseContext(), context.device)
    val ranked = remote.rankSources(profile, outcome.candidates, context)
    if (mutable.value.selectedProfile != profile || mutable.value.sourceTitle?.let(::cardLookupKey) != cardLookupKey(card)) return null
    updateState { it.copy(sources = ranked.rows, sourceGroups = ranked.groups, sourceProducers = producersLabelled(outcome.producers)) }
    return ranked.equivalentOf(stale)
}
