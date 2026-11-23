package org.viptv.app.downloads

import kotlinx.coroutines.flow.StateFlow
import org.viptv.engine.wire.Card

enum class DownloadState { Queued, Resolving, Downloading, Paused, WaitingForNetwork, Completed, Failed }

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
)

data class DownloadStorage(val usedBytes: Long, val freeBytes: Long)

data class DownloadSettings(
    val wifiOnly: Boolean = true,
    val maxQualityHeight: Int? = 1080,
    val autoDeleteWatched: Boolean = true,
    val keepNextEpisodes: Int = 0,
)

interface Downloads {
    val items: StateFlow<List<DownloadItem>>
    val storage: StateFlow<DownloadStorage>
    val settings: StateFlow<DownloadSettings>
    fun updateSettings(settings: DownloadSettings)
    fun pause(id: String)
    fun resume(id: String)
    fun cancel(id: String)
    fun delete(id: String)
    fun localFile(id: String): String?
}

internal expect fun platformDownloads(): Downloads?
