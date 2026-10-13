package org.viptv.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.viptv.app.features.details.detailSeasons
import org.viptv.app.features.details.episodeKey
import org.viptv.engine.wire.*

class DetailEpisodesTest {
    private fun episode(season: Long?, number: Long, id: String = "shared-title", provider: String = "provider") =
        Card(MediaIdentity(id, "series", emptyList(), EpisodeCoordinates("aired", season, number)),
            provider, "Episode $number", badges = emptyList())

    @Test fun sharedProviderIdsKeepDistinctEpisodesButDeduplicateRepeatedRecords() {
        val first = episode(1, 1)
        val second = episode(1, 2)
        val anotherProvider = episode(1, 1, provider = "another-provider")
        assertNotEquals(episodeKey(first), episodeKey(second))
        assertNotEquals(episodeKey(first), episodeKey(anotherProvider))
        assertEquals(3, detailSeasons(listOf(second, first, first, anotherProvider)).single().episodes.size)
    }

    @Test fun seasonsSortNumericallyAndRetainSpecialsAndUnknownCoordinates() {
        val unknown = episode(null, 1).copy(identity = MediaIdentity("unknown", "series", emptyList()))
        val groups = detailSeasons(listOf(episode(10, 1), unknown, episode(2, 3), episode(0, 1), episode(2, 1)))
        assertEquals(listOf(0L, 2L, 10L, null), groups.map { it.number })
        assertEquals(listOf(1L, 3L), groups[1].episodes.map { it.identity.episode?.episode })
        assertEquals("Specials", groups.first().label)
        assertEquals("Other episodes", groups.last().label)
        assertEquals("1 episode", groups.last().countLabel)
    }

    @Test fun identitySeparatorsCannotCollide() {
        assertNotEquals(episodeKey(episode(1, 1, id = "b|c", provider = "a")),
            episodeKey(episode(1, 1, id = "c", provider = "a|b")))
    }
}
