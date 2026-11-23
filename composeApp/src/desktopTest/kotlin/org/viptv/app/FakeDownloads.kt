package org.viptv.app

import kotlinx.coroutines.flow.MutableStateFlow
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadSettings
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.DownloadStorage
import org.viptv.app.downloads.Downloads
import org.viptv.app.features.downloads.DownloadActions
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity

class FakeDownloads(items: List<DownloadItem> = emptyList(), storage: DownloadStorage = DownloadStorage(12L * GIB, 31L * GIB),
    settings: DownloadSettings = DownloadSettings(), evicted: Int = 0) : Downloads {
    override val items = MutableStateFlow(items)
    override val storage = MutableStateFlow(storage)
    override val settings = MutableStateFlow(settings)
    override val evictionNotice = MutableStateFlow(evicted)
    val calls = mutableListOf<String>()

    override fun updateSettings(settings: DownloadSettings) { calls += "settings"; this.settings.value = settings }
    override fun pause(id: String) { calls += "pause:$id" }
    override fun resume(id: String) { calls += "resume:$id" }
    override fun cancel(id: String) { calls += "cancel:$id" }
    override fun delete(id: String) { calls += "delete:$id"; items.value = items.value.filterNot { it.id == id } }
    override fun keep(id: String, keep: Boolean) {
        calls += "keep:$id:$keep"
        items.value = items.value.map { if (it.id == id) it.copy(keep = keep) else it }
    }
    override fun localFile(id: String): String? = null
    override fun dismissEvictionNotice() { calls += "dismissEviction"; evictionNotice.value = 0 }

    internal fun actions(clock: () -> Long = { NOW }): DownloadActions = DownloadActions(this,
        startTitle = { calls += "title:${it.identity.id}:${it.identity.episode?.episode}" },
        startSeason = { _, season -> calls += "season:$season" },
        startNext = { _, count -> calls += "next:$count" },
        startSeries = { calls += "series" },
        play = { calls += "play:$it" },
        openPage = { calls += "open" },
        notice = { calls += "notice:$it" },
        clock = clock)

    companion object {
        const val GIB = 1L shl 30
        const val NOW = 1_800_000_000_000L

        fun episode(season: Long, number: Long, title: String = "Episode $number", series: String = "tt-series",
            progress: Double? = null, badges: List<String> = emptyList()) = Card(
            MediaIdentity(series, "series", emptyList(), episode = EpisodeCoordinates("$season:$number", season, number)),
            provider_id = "addon:cinemeta", title = title, badges = badges, progress = progress,
            parent_identity = MediaIdentity(series, "series", emptyList()), released_at_millis = NOW - 86_400_000L * (100 - number))

        fun movie(id: String = "tt-movie", title: String = "Arrival") = Card(MediaIdentity(id, "movie", emptyList()),
            provider_id = "addon:cinemeta", title = title, badges = emptyList())

        fun item(card: Card, state: DownloadState, done: Long = 0, total: Long? = 2L * GIB, seriesTitle: String? = "Severance",
            watchedAt: Long? = null, expiresAt: Long? = null, keep: Boolean = false, failure: String? = null, quality: Int? = 1080) =
            DownloadItem(id = "p1:${card.identity.id}:${card.identity.episode?.season}:${card.identity.episode?.episode}",
                profileId = "p1", card = card, seriesTitle = seriesTitle.takeIf { card.identity.episode != null }, state = state,
                bytesDone = done, bytesTotal = total, sourceLabel = "WEB-DL 1080p", qualityHeight = quality, failure = failure,
                addedAtMillis = NOW - 3_600_000L, watchedAtMillis = watchedAt, expiresAtMillis = expiresAt, keep = keep,
                lastPlayedAtMillis = null)
    }
}
