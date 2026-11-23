package org.viptv.app.downloads

import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.failsHardFilter
import org.viptv.engine.wire.SourceCandidate

internal const val DOWNLOAD_FREE_RESERVE_BYTES = 2L shl 30

internal fun effectiveLimit(settings: DownloadSettings, usedBytes: Long, freeBytes: Long): Long {
    val device = usedBytes + freeBytes - DOWNLOAD_FREE_RESERVE_BYTES
    return settings.storageLimitBytes?.let { minOf(it, device) } ?: device
}

internal fun protectedIds(records: List<DownloadRecord>, playingId: String?): Set<String> {
    val smartNext = records.filter { it.smart && it.watchedAtMillis == null }
        .groupBy { it.profileId to (it.card.parent_identity?.id ?: it.card.identity.id) }
        .values.mapNotNull { episodes -> episodes.minWithOrNull(compareBy({ it.card.identity.episode?.season ?: 0 }, { it.card.identity.episode?.episode ?: 0 }))?.id }
    return records.filter { it.state.active || it.keep }.map { it.id }.toSet() + smartNext + listOfNotNull(playingId)
}

internal fun evictionOrder(records: List<DownloadRecord>, protected: Set<String>): List<DownloadRecord> {
    val candidates = records.filter { it.id !in protected && !it.state.active && it.bytesDone > 0 }
    val watched = candidates.filter { it.watchedAtMillis != null }.sortedBy { it.watchedAtMillis }
    val other = candidates.filter { it.watchedAtMillis == null }.sortedBy { it.lastPlayedAtMillis ?: it.addedAtMillis }
    return watched + other
}

internal fun evictionPlan(records: List<DownloadRecord>, protected: Set<String>, neededBytes: Long, limitBytes: Long): List<DownloadRecord>? {
    var used = records.sumOf { it.bytesDone }
    if (used + neededBytes <= limitBytes) return emptyList()
    val plan = mutableListOf<DownloadRecord>()
    for (record in evictionOrder(records, protected)) {
        plan += record
        used -= record.bytesDone
        if (used + neededBytes <= limitBytes) return plan
    }
    return null
}

internal fun expired(records: List<DownloadRecord>, settings: DownloadSettings, nowMillis: Long, playingId: String?): List<DownloadRecord> =
    records.filter { record ->
        val at = record.expiresAt(settings)
        at != null && at <= nowMillis && record.id != playingId && !record.state.active
    }

internal fun downloadCandidates(ranked: RankedSources, maxQualityHeight: Int?): List<SourceCandidate> {
    val usable = ranked.rows.filter { source ->
        source.delivery != "torrent" && !source.failsHardFilter() &&
            (maxQualityHeight == null || (source.quality_height ?: 0) <= maxQualityHeight)
    }
    val runs = mutableListOf<MutableList<SourceCandidate>>()
    usable.forEach { source ->
        val last = runs.lastOrNull()
        if (last != null && sameRank(last.first(), source)) last += source else runs += mutableListOf(source)
    }
    return runs.flatMap { run -> run.sortedBy { it.presentation?.size_bytes ?: Long.MAX_VALUE } }
}

private fun sameRank(a: SourceCandidate, b: SourceCandidate): Boolean =
    a.quality_height == b.quality_height && a.release_type == b.release_type && a.dynamic_range == b.dynamic_range &&
        a.audio_languages == b.audio_languages

internal fun sameRelease(stale: SourceCandidate, rows: List<SourceCandidate>): SourceCandidate? =
    stale.source_fingerprint?.let { wanted -> rows.firstOrNull { it.source_fingerprint == wanted } }
        ?: rows.firstOrNull {
            it.provider_id == stale.provider_id && it.label == stale.label &&
                it.presentation?.size_bytes != null && it.presentation?.size_bytes == stale.presentation?.size_bytes
        }
