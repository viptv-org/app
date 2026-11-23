package org.viptv.app.downloads

import org.junit.Test
import org.viptv.app.connection.RankedSources
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadPolicyTest {
    @Test fun evictionTakesWatchedOldestFirstThenLeastRecentlyPlayed() {
        val records = listOf(
            record("played-late", added = 1, played = 900),
            record("watched-late", added = 2, watched = 500),
            record("never-played", added = 50),
            record("watched-early", added = 3, watched = 100),
            record("played-early", added = 4, played = 60),
        )
        assertEquals(listOf("watched-early", "watched-late", "never-played", "played-early", "played-late"),
            evictionOrder(records, emptySet()).map { it.id })
        assertEquals(listOf("watched-early", "watched-late"), evictionPlan(records, emptySet(), neededBytes = 20, limitBytes = 50)?.map { it.id })
        assertEquals(emptyList(), evictionPlan(records, emptySet(), neededBytes = 0, limitBytes = 50))
    }

    @Test fun activeKeptPlayingAndTheNextSmartEpisodeAreNeverEvicted() {
        val records = listOf(
            record("queued", added = 1, state = DownloadState.Queued),
            record("downloading", added = 2, state = DownloadState.Downloading),
            record("kept", added = 3, watched = 1, keep = true),
            record("playing", added = 4, watched = 2),
            record("smart-2", added = 5, smart = true, episode = 2),
            record("smart-3", added = 6, smart = true, episode = 3),
            record("smart-1", added = 7, smart = true, episode = 1, watched = 3),
        )
        val protected = protectedIds(records, playingId = "playing")
        assertEquals(setOf("queued", "downloading", "kept", "playing", "smart-2"), protected)
        assertEquals(listOf("smart-1", "smart-3"), evictionOrder(records, protected).map { it.id })
        assertNull(evictionPlan(records.filter { it.id in protected }, protected, neededBytes = 10, limitBytes = 10))
    }

    @Test fun expiryNeverTakesKeptActiveOrPlayingItems() {
        val settings = DownloadSettings(deleteWatchedAfterMillis = 100)
        val records = listOf(record("due", added = 1, watched = 0), record("kept", added = 2, watched = 0, keep = true),
            record("playing", added = 3, watched = 0), record("fresh", added = 4, watched = 50))
        assertEquals(listOf("due"), expired(records, settings, nowMillis = 100, playingId = "playing").map { it.id })
        assertEquals(emptyList(), expired(records, DownloadSettings(deleteWatchedAfterMillis = null), 1_000_000, null))
    }

    @Test fun downloadSourcesSkipTorrentsRespectTheCapAndPreferSmallerAmongEqualRanks() {
        fun source(id: String, height: Long, size: Long?, delivery: String = "http") =
            SourceCandidate(id, "addon", id, quality_height = height, delivery = delivery, presentation = SourcePresentation(size_bytes = size))
        val ranked = RankedSources(listOf(source("4k", 2160, 1), source("big", 1080, 9_000), source("torrent", 1080, 10, "torrent"),
            source("small", 1080, 4_000), source("720", 720, 100)))
        assertEquals(listOf("small", "big", "720"), downloadCandidates(ranked, 1080).map { it.id })
        assertEquals(listOf("4k", "small", "big", "720"), downloadCandidates(ranked, null).map { it.id })
    }

    @Test fun sameReleaseMatchesTheFingerprintThenLabelAndSize() {
        val stale = SourceCandidate("old", "addon", "Show 1080p", source_fingerprint = "fp", presentation = SourcePresentation(size_bytes = 5))
        val byFingerprint = stale.copy(id = "a", label = "renamed")
        val byLabel = stale.copy(id = "b", source_fingerprint = null)
        assertEquals("a", sameRelease(stale, listOf(byLabel, byFingerprint))?.id)
        assertEquals("b", sameRelease(stale.copy(source_fingerprint = null), listOf(byLabel))?.id)
        assertNull(sameRelease(stale.copy(source_fingerprint = null), listOf(byLabel.copy(presentation = SourcePresentation(size_bytes = 6)))))
    }

    private fun record(id: String, added: Long, watched: Long? = null, played: Long? = null, keep: Boolean = false,
        state: DownloadState = DownloadState.Completed, smart: Boolean = false, episode: Long = added) = DownloadRecord(
        id = id, profileId = "p", state = state, bytesDone = 10, bytesTotal = 10, addedAtMillis = added, watchedAtMillis = watched,
        lastPlayedAtMillis = played, keep = keep, smart = smart,
        card = Card(MediaIdentity("tt1:1:$episode", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, episode)),
            provider_id = "catalog", title = id, badges = emptyList(), parent_identity = MediaIdentity("tt1", "series", emptyList())))
}
