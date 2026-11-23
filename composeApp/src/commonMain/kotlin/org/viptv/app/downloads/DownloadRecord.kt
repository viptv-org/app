package org.viptv.app.downloads

import kotlinx.serialization.Serializable
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

@Serializable
internal data class DownloadRecord(
    val id: String,
    val profileId: String,
    val card: Card,
    val seriesTitle: String? = null,
    val state: DownloadState = DownloadState.Queued,
    val bytesDone: Long = 0,
    val bytesTotal: Long? = null,
    val source: SourceCandidate? = null,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val failure: String? = null,
    val addedAtMillis: Long,
    val watchedAtMillis: Long? = null,
    val keep: Boolean = false,
    val lastPlayedAtMillis: Long? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
    val smart: Boolean = false,
    val retryAtMillis: Long? = null,
    val subtitles: List<DownloadSubtitle> = emptyList(),
    val tracks: List<DownloadTrack> = emptyList(),
    val pinned: Boolean = false,
    val release: SourceCandidate? = null,
    val revision: Int = 0,
    val previous: DownloadPrevious? = null,
    val subtitlesFetched: Boolean = false,
    val probed: Boolean = false,
) {
    val fileName: String get() = downloadFileName(id, revision)

    val storedBytes: Long get() = bytesDone + (previous?.bytes ?: 0)

    val previousFileName: String? get() = previous?.let { downloadFileName(id, it.revision) }

    fun item(settings: DownloadSettings): DownloadItem = DownloadItem(
        id = id, profileId = profileId, card = card, seriesTitle = seriesTitle, state = state,
        bytesDone = bytesDone, bytesTotal = bytesTotal, sourceLabel = source?.label,
        qualityHeight = source?.quality_height?.toInt(), failure = failure, addedAtMillis = addedAtMillis,
        watchedAtMillis = watchedAtMillis, expiresAtMillis = expiresAt(settings), keep = keep,
        lastPlayedAtMillis = lastPlayedAtMillis,
        sourceProvider = source?.let { it.presentation?.provider_name?.takeIf(String::isNotBlank) ?: it.provider_id },
        sourceDetail = source?.detail, format = downloadFormat(source, mimeType),
        tracks = tracks + subtitles.map { DownloadTrack("subtitle", it.language, it.label, embedded = false) },
    )

    fun expiresAt(settings: DownloadSettings): Long? {
        val watched = watchedAtMillis ?: return null
        val delay = settings.deleteWatchedAfterMillis ?: return null
        return if (keep) null else watched + delay
    }
}

@Serializable
internal data class DownloadSubtitle(val id: String, val providerId: String, val language: String, val label: String, val fileName: String) {
    fun candidate() = org.viptv.engine.wire.SubtitleCandidate(id, providerId, language, label)
}

@Serializable
internal data class DownloadPrevious(val revision: Int, val source: SourceCandidate?, val mimeType: String?, val bytes: Long,
    val subtitles: List<DownloadSubtitle>, val tracks: List<DownloadTrack>, val subtitlesFetched: Boolean)

@Serializable
internal data class OfflineProgress(val profileId: String, val card: Card, val positionMillis: Long, val durationMillis: Long?,
    val ended: Boolean, val atMillis: Long)

@Serializable
internal data class DownloadBook(
    val records: List<DownloadRecord> = emptyList(),
    val settings: DownloadSettingsRecord = DownloadSettingsRecord(),
    val progress: List<OfflineProgress> = emptyList(),
    val evicted: Int = 0,
)

@Serializable
internal data class DownloadSettingsRecord(
    val wifiOnly: Boolean = true,
    val maxQualityHeight: Int? = 1080,
    val storageLimitBytes: Long? = 50L shl 30,
    val deleteWatchedAfterMillis: Long? = 86_400_000L,
    val keepNextEpisodes: Int = 0,
    val downloadSubtitles: Boolean = true,
) {
    fun settings() = DownloadSettings(wifiOnly, maxQualityHeight, storageLimitBytes, deleteWatchedAfterMillis, keepNextEpisodes, downloadSubtitles)
}

internal fun DownloadSettings.record() =
    DownloadSettingsRecord(wifiOnly, maxQualityHeight, storageLimitBytes, deleteWatchedAfterMillis, keepNextEpisodes, downloadSubtitles)

internal fun downloadId(profileId: String, card: Card): String = "$profileId|${cardLookupKey(card)}"

internal fun downloadFileName(id: String, revision: Int): String = downloadFileName(if (revision == 0) id else "$id#$revision")

internal fun downloadFileName(id: String): String {
    val safe = id.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").takeLast(80)
    val hash = id.fold(1125899906842597L) { acc, char -> 31 * acc + char.code }.toULong().toString(16)
    return "$safe-$hash.media"
}

internal val DownloadState.active: Boolean
    get() = this != DownloadState.Completed && this != DownloadState.Failed

internal fun downloadFormat(source: SourceCandidate?, mimeType: String?): String? =
    source?.presentation?.container?.takeIf(String::isNotBlank)
        ?: mimeType?.substringAfter('/')?.substringAfterLast('-')?.takeIf(String::isNotBlank)
        ?: source?.presentation?.delivery_format?.takeIf(String::isNotBlank)
