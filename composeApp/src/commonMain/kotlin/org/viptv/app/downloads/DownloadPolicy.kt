package org.viptv.app.downloads

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.failsHardFilter
import org.viptv.app.connection.subtitleMode
import org.viptv.app.player.languageCode
import org.viptv.app.player.signsOnlySubtitle
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SubtitleCandidate

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
    var used = records.sumOf { it.storedBytes }
    if (used + neededBytes <= limitBytes) return emptyList()
    val plan = mutableListOf<DownloadRecord>()
    for (record in evictionOrder(records, protected)) {
        plan += record
        used -= record.storedBytes
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

internal const val DOWNLOAD_NEEDS_CONVERSION = "Needs the server to convert it, so it can't be saved to this device."
internal const val DOWNLOAD_TORRENT_ONLY = "Torrent sources can't be saved to this device."

internal fun downloadBlockReason(source: SourceCandidate): String? = when {
    source.delivery == "torrent" -> DOWNLOAD_TORRENT_ONLY
    source.presentation?.delivery_format?.lowercase() in STREAMED_FORMATS -> DOWNLOAD_NEEDS_CONVERSION
    else -> null
}

private val STREAMED_FORMATS = setOf("hls", "m3u8", "dash", "mpd")

internal fun downloadOrder(candidates: List<SourceCandidate>, stale: SourceCandidate?, release: SourceCandidate?): List<SourceCandidate> {
    val first = listOfNotNull(stale?.let { sameRelease(it, candidates) })
    val binge = release?.binge_group?.let { group -> candidates.filter { it.binge_group == group && it.provider_id == release.provider_id } }.orEmpty()
    val same = listOfNotNull(release?.let { sameRelease(it, candidates) })
    return (first + binge + same + candidates).distinct()
}

internal fun downloadedSourceIds(records: List<DownloadRecord>, rows: List<SourceCandidate>): Map<String, String> =
    records.filter { it.state != DownloadState.Failed }.mapNotNull { record ->
        record.source?.let { sameRelease(it, rows) }?.let { it.id to record.id }
    }.toMap()

internal fun downloadSubtitleChoice(candidates: List<SubtitleCandidate>, preferences: ProfilePreferences, source: SourceCandidate?): List<SubtitleCandidate> {
    val wanted = languageCode(preferences.preferred_subtitle_language) ?: languageCode(preferences.preferred_audio_language) ?: "en"
    val inLanguage = candidates.filter { languageCode(it.language) == wanted }
    val dialogue = inLanguage.firstOrNull { !signsOnlySubtitle(it.label, forced = false) }
    val dubbed = source != null && ("dubbed" in source.audio_flags ||
        source.audio_languages.any { languageCode(it) == (languageCode(preferences.preferred_audio_language) ?: "en") })
    val signs = if (preferences.subtitleMode == "foreign" && dubbed) inLanguage.firstOrNull { signsOnlySubtitle(it.label, forced = false) } else null
    return listOfNotNull(dialogue, signs)
}

private val SUBTITLE_MIMES = setOf("application/x-subrip", "application/x-ssa", "application/x-ass", "application/ttml+xml",
    "application/x-quicktime-tx3g", "application/pgs", "application/vobsub", "application/dvbsubs", "application/x-media3-cues")

internal fun downloadTrackKind(mime: String?): String? {
    val value = mime?.lowercase() ?: return null
    return when {
        value.startsWith("audio/") -> "audio"
        value.startsWith("text/") || value in SUBTITLE_MIMES -> "subtitle"
        else -> null
    }
}

internal fun downloadTrackLanguage(language: String?): String? =
    language?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "und" }
