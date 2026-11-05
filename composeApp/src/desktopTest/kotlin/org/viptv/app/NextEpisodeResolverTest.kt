package org.viptv.app

import org.viptv.app.features.player.nextEpisodeAfter
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.MetadataOrigin
import kotlin.test.*

class NextEpisodeResolverTest {
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(season: Long?, number: Long, provider: String = "addon:7", metadataOrigin: MetadataOrigin? = origin) =
        Card(MediaIdentity("private-$season-$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
            provider_id = provider, title = "S$season E$number", badges = emptyList(),
            parent_identity = parent, metadata_origin = metadataOrigin)

    @Test fun sameSeasonFindsTheFollowingEpisode() {
        val list = (1L..6L).map { episode(1, it) }
        assertEquals("S1 E3", nextEpisodeAfter(episode(1, 2), list)?.title)
    }

    @Test fun aSeasonBoundaryFindsTheNextSeasonsFirstEpisode() {
        val season1 = (1L..2L).map { episode(1, it) }
        val season2 = (1L..3L).map { episode(2, it) }
        assertEquals("S2 E1", nextEpisodeAfter(episode(1, 2), season1 + season2)?.title)
        assertEquals("S2 E1", nextEpisodeAfter(episode(1, 2), (season1 + season2).reversed())?.title, "the resolver orders, not the input")
    }

    @Test fun theLastEpisodeHasNoNext() {
        val list = (1L..4L).map { episode(1, it) }
        assertNull(nextEpisodeAfter(episode(1, 4), list))
    }

    @Test fun aFlatNumberedSeriesStillFindsItsNextEpisode() {
        val list = (1L..4L).map { episode(null, it).copy(title = "Episode $it") }
        assertEquals("Episode 3", nextEpisodeAfter(episode(null, 2), list)?.title)
        assertEquals("Episode 2", nextEpisodeAfter(episode(null, 1), list.reversed())?.title)
        assertNull(nextEpisodeAfter(episode(null, 4), list))
    }

    @Test fun anAbsentSeasonSortsAsSeasonOneBesideItsStoredEpisodes() {
        val first = episode(1, 1)
        val stored = episode(null, 2)
        val third = episode(1, 3)
        val list = listOf(first, stored, third)
        assertEquals(stored, nextEpisodeAfter(first, list))
        assertEquals(third, nextEpisodeAfter(stored, list))
    }

    @Test fun episodesFromAnotherProviderParentOrIncarnationAreIgnored() {
        val here = episode(1, 2)
        val good = episode(1, 3)
        val foreign = listOf(
            episode(1, 3, provider = "addon:8"),
            episode(1, 3, metadataOrigin = origin.copy(incarnation = "b".repeat(32))),
            good.copy(parent_identity = parent.copy(id = "other-show")),
        )
        assertEquals(good, nextEpisodeAfter(here, listOf(here, good) + foreign))
    }
}