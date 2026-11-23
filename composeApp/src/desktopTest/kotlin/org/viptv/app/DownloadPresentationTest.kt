package org.viptv.app

import org.viptv.app.FakeDownloads.Companion.GIB
import org.viptv.app.FakeDownloads.Companion.NOW
import org.viptv.app.FakeDownloads.Companion.episode
import org.viptv.app.FakeDownloads.Companion.item
import org.viptv.app.FakeDownloads.Companion.movie
import org.viptv.app.downloads.DownloadSettings
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.DownloadStorage
import org.viptv.app.features.downloads.*
import kotlin.test.*

class DownloadPresentationTest {
    private val settings = DownloadSettings()

    private fun line(state: DownloadState, done: Long = 0, total: Long? = 2L * GIB, watchedAt: Long? = null,
        expiresAt: Long? = null, keep: Boolean = false, wifiOnly: Boolean = true) =
        downloadStateLine(item(episode(1, 3), state, done, total, watchedAt = watchedAt, expiresAt = expiresAt, keep = keep),
            settings.copy(wifiOnly = wifiOnly), NOW)

    @Test fun everyStateReadsItsDesignLine() {
        assertEquals("Queued", line(DownloadState.Queued))
        assertEquals("Finding a source", line(DownloadState.Resolving))
        assertEquals("42% · 1.1 of 2.6 GB", line(DownloadState.Downloading, done = (1.1 * GIB).toLong(), total = (2.6 * GIB).toLong()))
        assertEquals("1.1 GB", line(DownloadState.Downloading, done = (1.1 * GIB).toLong(), total = null))
        assertEquals("Paused · 50%", line(DownloadState.Paused, done = GIB))
        assertEquals("Waiting for Wi‑Fi", line(DownloadState.WaitingForNetwork))
        assertEquals("Waiting for a connection", line(DownloadState.WaitingForNetwork, wifiOnly = false))
        assertEquals("Storage limit reached", line(DownloadState.WaitingForSpace))
        assertEquals("1080p · 2 GB", line(DownloadState.Completed, done = 2L * GIB))
        assertEquals("Couldn't download", line(DownloadState.Failed))
    }

    @Test fun keptAndWatchedRowsFollowTheKeepCopy() {
        assertEquals("1080p · 2 GB · Kept", line(DownloadState.Completed, keep = true))
        assertEquals("Watched · deletes in 23 h", line(DownloadState.Completed, watchedAt = NOW - 3_600_000L, expiresAt = NOW + 23 * 3_600_000L))
        assertEquals("Watched · kept", line(DownloadState.Completed, watchedAt = NOW - 3_600_000L, expiresAt = NOW + 23 * 3_600_000L, keep = true))
    }

    @Test fun expiryCountsInMinutesHoursAndDays() {
        assertEquals("45 min", expiryLabel(45 * 60_000L))
        assertEquals("1 min", expiryLabel(5_000L))
        assertEquals("23 h", expiryLabel(23 * 3_600_000L + 10 * 60_000L))
        assertEquals("6 d", expiryLabel(6 * 86_400_000L + 3_600_000L))
    }

    @Test fun sizesUseOneDecimalUnderAHundredGigabytes() {
        assertEquals("12.4", gigabytes((12.4 * GIB).toLong()))
        assertEquals("50", gigabytes(50L * GIB))
        assertEquals("120", gigabytes((120.3 * GIB).toLong()))
        assertEquals("12.4 GB of 50 GB used · 31 GB free on this device",
            storageLine(DownloadStorage((12.4 * GIB).toLong(), 31L * GIB), settings))
        assertEquals("12.4 GB used · 31 GB free on this device",
            storageLine(DownloadStorage((12.4 * GIB).toLong(), 31L * GIB), settings.copy(storageLimitBytes = null)))
    }

    @Test fun deleteWatchedSkipsKeptAndUnfinishedItems() {
        val watched = item(episode(1, 1), DownloadState.Completed, watchedAt = NOW, expiresAt = NOW + 1)
        val kept = item(episode(1, 2), DownloadState.Completed, watchedAt = NOW, keep = true)
        val unwatched = item(episode(1, 3), DownloadState.Completed)
        assertEquals(listOf(watched), deletableWatched(listOf(watched, kept, unwatched)))
        assertEquals("Deleted 1 watched download", deletedWatchedNotice(1))
        assertEquals("Removed 3 old downloads to make space", evictionNotice(3))
        assertEquals("Removed 1 old download to make space", evictionNotice(1))
    }

    @Test fun moviesComeFirstThenSeriesBySeasonAndEpisode() {
        val groups = downloadGroups(listOf(
            item(episode(2, 1), DownloadState.Completed), item(episode(1, 4), DownloadState.Queued),
            item(movie(), DownloadState.Completed), item(episode(1, 2), DownloadState.Completed)))
        assertIs<MovieDownload>(groups[0])
        val series = assertIs<SeriesDownloads>(groups[1])
        assertEquals("Severance", series.title)
        assertEquals(listOf(1L, 2L), series.seasons.map { it.first })
        assertEquals(listOf(2L, 4L), series.seasons[0].second.map { it.card.identity.episode?.episode })
        assertEquals("3 downloads · 6 GB", groupSummary(series))
    }

    @Test fun storageLimitChoicesStopTwoGigabytesShortOfTheDevice() {
        assertEquals(listOf("10 GB", "25 GB", "No limit"), storageLimitChoices(DownloadStorage(5L * GIB, 25L * GIB)).map { it.second })
        assertEquals(listOf("10 GB", "25 GB", "50 GB", "100 GB", "No limit"), storageLimitChoices(DownloadStorage(0, 500L * GIB)).map { it.second })
        assertEquals("After 1 day", deleteWatchedLabel(settings.deleteWatchedAfterMillis))
        assertEquals("50 GB", storageLimitLabel(settings.storageLimitBytes))
    }

    @Test fun seriesPlanSkipsDownloadedEpisodesAndStartsAtTheNextUnwatched() {
        val episodes = (1L..8L).map { episode(1, it, badges = if (it <= 3) listOf("Watched") else emptyList()) }
        val downloaded = item(episodes[4], DownloadState.Completed)
        val plan = SeriesDownloadPlan(episodes, mapOf(downloadKey(episodes[4]) to downloaded), NOW)
        assertEquals(listOf(4L, 6L), plan.next(3).map { it.identity.episode?.episode })
        assertEquals(7, plan.season(1).size)
        assertEquals(7, plan.series().size)
    }
}
