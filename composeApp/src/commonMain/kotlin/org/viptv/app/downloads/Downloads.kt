package org.viptv.app.downloads

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.viptv.engine.wire.Card

enum class DownloadState { Queued, Resolving, Downloading, Paused, WaitingForNetwork, WaitingForSpace, Completed, Failed }

@Serializable
data class DownloadTrack(val kind: String, val language: String?, val label: String?, val embedded: Boolean)

data class DownloadItem(
    val id: String,
    val profileId: String,
    val card: Card,
    val seriesTitle: String?,
    val state: DownloadState,
    val bytesDone: Long,
    val bytesTotal: Long?,
    val sourceLabel: String?,
    val qualityHeight: Int?,
    val failure: String?,
    val addedAtMillis: Long,
    val watchedAtMillis: Long?,
    val expiresAtMillis: Long?,
    val keep: Boolean,
    val lastPlayedAtMillis: Long?,
    val sourceProvider: String? = null,
    val sourceDetail: String? = null,
    val format: String? = null,
    val tracks: List<DownloadTrack> = emptyList(),
)

data class DownloadStorage(val usedBytes: Long, val freeBytes: Long)

data class DownloadSettings(
    val wifiOnly: Boolean = true,
    val maxQualityHeight: Int? = 1080,
    val storageLimitBytes: Long? = 50L shl 30,
    val deleteWatchedAfterMillis: Long? = 86_400_000L,
    val keepNextEpisodes: Int = 0,
    val downloadSubtitles: Boolean = true,
)

interface Downloads {
    val items: StateFlow<List<DownloadItem>>
    val storage: StateFlow<DownloadStorage>
    val settings: StateFlow<DownloadSettings>
    val evictionNotice: StateFlow<Int>
    fun updateSettings(settings: DownloadSettings)
    fun pause(id: String)
    fun resume(id: String)
    fun cancel(id: String)
    fun delete(id: String)
    fun keep(id: String, keep: Boolean)
    fun dismissEvictionNotice()
    fun localFile(id: String): String?
}

internal expect fun platformDownloads(): Downloads?
