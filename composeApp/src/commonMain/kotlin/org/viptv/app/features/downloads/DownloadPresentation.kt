package org.viptv.app.features.downloads

import org.viptv.app.connection.seasonNumber
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadSettings
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.DownloadStorage
import org.viptv.app.downloads.DownloadTrack
import org.viptv.app.player.languageName
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.features.details.episodeKey
import org.viptv.engine.wire.Card

internal const val DOWNLOADS_TITLE = "Downloads"
internal const val STORAGE_LIMIT_REACHED = "Storage limit reached"
internal const val STORAGE_LIMIT_BODY = "Raise the limit or delete downloads to continue."
internal const val DOWNLOADS_EMPTY_TITLE = "No downloads yet"
internal const val DOWNLOADS_EMPTY_BODY = "Download episodes from a title's page to watch them without a connection."
internal const val OFFLINE_BANNER = "You're offline. Downloads still play."
internal const val DOWNLOAD_DETAILS_TITLE = "Download details"
internal const val CHOOSE_SOURCE = "Choose source…"
internal const val CHANGE_SOURCE = "Change source"
internal const val DOWNLOAD_SUBTITLES = "Download subtitles"
internal const val RELEASE_PROMPT_TITLE = "Download more from this release?"
internal const val REST_OF_SEASON = "Rest of season"
internal const val NOT_NOW = "Not now"
internal const val DOWNLOAD_SUBTITLES_BODY = "Saves add-on subtitles in your language with each download."

internal enum class DownloadControlState { None, Requested, Queued, Resolving, Downloading, Halted, Completed, Failed }

internal fun DownloadItem?.controlState(requested: Boolean): DownloadControlState = when (this?.state) {
    null -> if (requested) DownloadControlState.Requested else DownloadControlState.None
    DownloadState.Queued -> DownloadControlState.Queued
    DownloadState.Resolving -> DownloadControlState.Resolving
    DownloadState.Downloading -> DownloadControlState.Downloading
    DownloadState.Paused, DownloadState.WaitingForNetwork, DownloadState.WaitingForSpace -> DownloadControlState.Halted
    DownloadState.Completed -> DownloadControlState.Completed
    DownloadState.Failed -> DownloadControlState.Failed
}

internal val DownloadItem.active: Boolean get() = state !in setOf(DownloadState.Completed, DownloadState.Failed)

internal val DownloadItem.fraction: Float?
    get() = bytesTotal?.takeIf { it > 0 }?.let { (bytesDone.toDouble() / it).toFloat().coerceIn(0f, 1f) }

internal fun DownloadItem.percent(): Int = ((fraction ?: 0f) * 100).toInt()

internal val DownloadItem.watched: Boolean get() = watchedAtMillis != null

internal fun downloadKey(card: Card): String = episodeKey(card)

private const val GIB = 1L shl 30

internal fun gigabytes(bytes: Long): String {
    val gib = bytes.coerceAtLeast(0).toDouble() / GIB
    if (gib >= 100) return kotlin.math.round(gib).toLong().toString()
    val tenths = kotlin.math.round(gib * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

private const val MIB = 1L shl 20

internal fun sizeLabel(bytes: Long): String = if (bytes < GIB) "${megabytes(bytes)} MB" else "${gigabytes(bytes)} GB"

private fun megabytes(bytes: Long): Long = kotlin.math.round(bytes.coerceAtLeast(0).toDouble() / MIB).toLong()

private fun sizeAmount(bytes: Long, total: Long): String = when {
    bytes >= GIB && total >= GIB -> gigabytes(bytes)
    bytes < GIB && total < GIB -> megabytes(bytes).toString()
    else -> sizeLabel(bytes)
}

internal fun expiryLabel(remainingMillis: Long): String {
    val minutes = (remainingMillis.coerceAtLeast(0) + 59_999) / 60_000
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} d"
    }
}

internal fun downloadStateLine(item: DownloadItem, settings: DownloadSettings, nowMillis: Long): String = when (item.state) {
    DownloadState.Queued -> "Queued"
    DownloadState.Resolving -> "Finding a source"
    DownloadState.Downloading -> item.bytesTotal?.takeIf { it > 0 }
        ?.let { total -> "${item.percent()}% · ${sizeAmount(item.bytesDone, total)} of ${sizeLabel(total)}" }
        ?: sizeLabel(item.bytesDone)
    DownloadState.Paused -> "Paused · ${item.percent()}%"
    DownloadState.WaitingForNetwork -> if (settings.wifiOnly) "Waiting for Wi‑Fi" else "Waiting for a connection"
    DownloadState.WaitingForSpace -> STORAGE_LIMIT_REACHED
    DownloadState.Completed -> completedLine(item, nowMillis)
    DownloadState.Failed -> "Couldn't download"
}

private fun completedLine(item: DownloadItem, nowMillis: Long): String {
    val expires = item.expiresAtMillis
    return when {
        item.watched && item.keep -> "Watched · kept"
        item.watched && expires != null -> "Watched · deletes in ${expiryLabel(expires - nowMillis)}"
        else -> listOfNotNull(item.qualityHeight?.let { "${it}p" }, sizeLabel(item.bytesTotal ?: item.bytesDone),
            "Kept".takeIf { item.keep }).joinToString(" · ")
    }
}

internal fun downloadSourceLine(item: DownloadItem): String? {
    val provider = item.sourceProvider?.takeIf(String::isNotBlank) ?: return null
    val size = (item.bytesTotal ?: item.bytesDone).takeIf { it > 0 && item.state == DownloadState.Completed }?.let(::sizeLabel)
    return listOfNotNull(provider, item.qualityHeight?.let { "${it}p" }, size).joinToString(" · ")
}

internal fun downloadRowStatus(item: DownloadItem, settings: DownloadSettings, nowMillis: Long): String? =
    if (item.state == DownloadState.Completed && !item.watched && downloadSourceLine(item) != null) "Kept".takeIf { item.keep }
    else downloadStateLine(item, settings, nowMillis)

internal class DownloadFact(val label: String, val value: String)

internal fun downloadFacts(item: DownloadItem): List<DownloadFact> = listOfNotNull(
    item.sourceProvider?.takeIf(String::isNotBlank)?.let { DownloadFact("Provider", it) },
    item.qualityHeight?.let { DownloadFact("Quality", "${it}p") },
    (item.bytesTotal ?: item.bytesDone).takeIf { it > 0 }?.let { DownloadFact("Size", sizeLabel(it)) },
    item.format?.takeIf(String::isNotBlank)?.let { DownloadFact("Format", it.uppercase()) },
)

internal fun trackName(track: DownloadTrack): String {
    val language = languageName(track.language)
    val label = track.label?.trim()?.takeIf { it.isNotEmpty() && !it.equals(language, ignoreCase = true) }
    return listOfNotNull(language, label).joinToString(" · ").ifEmpty { "Unknown language" }
}

internal fun trackOrigin(track: DownloadTrack): String = if (track.embedded) "Embedded" else "Add-on"

internal fun noTracksLine(item: DownloadItem, kind: String): String = when {
    item.state != DownloadState.Completed -> "Listed when the download finishes."
    item.tracks.isEmpty() -> "Checking tracks…"
    kind == "audio" -> "No audio tracks found."
    else -> "No subtitles."
}

internal fun releaseLine(source: org.viptv.engine.wire.SourceCandidate): String =
    listOfNotNull(source.providerLabel(), source.qualityLabel()).joinToString(" · ")

internal fun storageLine(storage: DownloadStorage, settings: DownloadSettings): String =
    settings.storageLimitBytes?.let { limit -> "${sizeLabel(storage.usedBytes)} of ${sizeLabel(limit)} used · ${sizeLabel(storage.freeBytes)} free on this device" }
        ?: "${sizeLabel(storage.usedBytes)} used · ${sizeLabel(storage.freeBytes)} free on this device"

internal fun storageFraction(storage: DownloadStorage, settings: DownloadSettings): Float {
    val capacity = settings.storageLimitBytes ?: (storage.usedBytes + storage.freeBytes)
    return if (capacity <= 0) 0f else (storage.usedBytes.toDouble() / capacity).toFloat().coerceIn(0f, 1f)
}

internal fun settingsStorageLine(storage: DownloadStorage): String =
    "${sizeLabel(storage.usedBytes)} used by downloads · ${sizeLabel(storage.freeBytes)} free"

internal fun evictionNotice(count: Int): String =
    if (count == 1) "Removed 1 old download to make space" else "Removed $count old downloads to make space"

internal fun deletedWatchedNotice(count: Int): String =
    if (count == 1) "Deleted 1 watched download" else "Deleted $count watched downloads"

internal fun deletableWatched(items: List<DownloadItem>): List<DownloadItem> =
    items.filter { it.state == DownloadState.Completed && it.watched && !it.keep }

internal fun episodeLabel(card: Card): String {
    val episode = card.identity.episode ?: return card.title
    return "S${episode.seasonNumber()} E${episode.episode} · ${card.title}"
}

internal fun controlName(card: Card, item: DownloadItem?, requested: Boolean): String = when (item.controlState(requested)) {
    DownloadControlState.None, DownloadControlState.Failed -> "Download ${card.identity.episode?.let { "S${it.seasonNumber()} E${it.episode}" } ?: card.title}"
    DownloadControlState.Completed -> "Downloaded"
    else -> "Downloading ${item?.percent() ?: 0}%"
}

internal sealed interface DownloadGroup {
    val key: String
    val items: List<DownloadItem>
    val bytes: Long get() = items.sumOf { it.bytesTotal ?: it.bytesDone }
}

internal data class MovieDownload(val item: DownloadItem) : DownloadGroup {
    override val key: String get() = "movie:${item.id}"
    override val items: List<DownloadItem> get() = listOf(item)
}

internal data class SeriesDownloads(override val key: String, val title: String, val seasons: List<Pair<Long, List<DownloadItem>>>) : DownloadGroup {
    override val items: List<DownloadItem> get() = seasons.flatMap { it.second }
}

internal fun downloadGroups(items: List<DownloadItem>): List<DownloadGroup> {
    val (episodes, movies) = items.partition { it.card.identity.episode != null }
    val movieGroups = movies.sortedBy { it.addedAtMillis }.map(::MovieDownload)
    val seriesGroups = episodes.groupBy { item ->
        val parent = item.card.parent_identity ?: item.card.identity
        "series:${item.card.provider_id}:${parent.kind}:${parent.id}"
    }.map { (key, members) ->
        val seasons = members.groupBy { it.card.identity.episode!!.seasonNumber() }.toList().sortedBy { it.first }
            .map { (season, list) -> season to list.sortedBy { it.card.identity.episode!!.episode } }
        SeriesDownloads(key, members.firstNotNullOfOrNull { it.seriesTitle } ?: members.first().card.title, seasons)
    }.sortedBy { it.title.lowercase() }
    return movieGroups + seriesGroups
}

internal fun groupSummary(group: SeriesDownloads): String {
    val count = group.items.size
    return "$count ${if (count == 1) "download" else "downloads"} · ${sizeLabel(group.bytes)}"
}

internal val storageLimitChoices: List<Pair<Long?, String>> = listOf(10L, 25L, 50L, 100L).map { (it shl 30) to "$it GB" } + (null to "No limit")

internal fun storageLimitChoices(storage: DownloadStorage): List<Pair<Long?, String>> {
    val capacity = storage.usedBytes + storage.freeBytes - (2L shl 30)
    return storageLimitChoices.filter { (limit, _) -> limit == null || storage.usedBytes + storage.freeBytes <= 0 || limit <= capacity }
}

internal fun storageLimitLabel(limit: Long?): String = storageLimitChoices.firstOrNull { it.first == limit }?.second
    ?: limit?.let(::sizeLabel) ?: "No limit"

private const val HOUR = 3_600_000L

internal val deleteWatchedChoices: List<Pair<Long?, String>> = listOf(
    0L to "Right after watching", HOUR to "After 1 hour", 24 * HOUR to "After 1 day", 7 * 24 * HOUR to "After 1 week", null to "Never")

internal fun deleteWatchedLabel(after: Long?): String = deleteWatchedChoices.firstOrNull { it.first == after }?.second
    ?: after?.let { "After ${expiryLabel(it)}" } ?: "Never"

internal val qualityChoices: List<Pair<Int?, String>> = listOf(null to "Best", 1080 to "1080p", 720 to "720p", 480 to "480p")

internal fun qualityLabel(height: Int?): String = qualityChoices.firstOrNull { it.first == height }?.second ?: height?.let { "${it}p" } ?: "Best"

internal val smartDownloadChoices: List<Pair<Int, String>> = listOf(0 to "Off", 1 to "1", 3 to "3", 5 to "5")

internal fun smartDownloadLabel(count: Int): String = smartDownloadChoices.firstOrNull { it.first == count }?.second ?: count.toString()
