package org.viptv.app

import kotlin.test.*
import org.viptv.app.components.metadataParts
import org.viptv.app.components.presentationBadges
import org.viptv.app.features.search.searchGroups
import org.viptv.engine.wire.*

class MetadataPresentationTest {
    private val anime = Card(identity = MediaIdentity("naruto", "series", emptyList()), provider_id = "simkl", title = "Naruto", category = "anime", badges = listOf("Simkl"))
    @Test fun animeCategoryDoesNotChangeEpisodePlaybackIdentityOrRankOrder() {
        val movie = anime.copy(identity = MediaIdentity("film", "movie", emptyList()), category = "movie")
        val groups = searchGroups(listOf(anime, movie), emptyList())
        assertEquals(listOf("Anime", "Movies"), groups.map { it.label })
        assertEquals("series", groups.first().cards.first().identity.kind)
    }
    @Test fun resumeTextDoesNotReplaceStructuredTitleMetadata() {
        val enriched = anime.copy(subtitle = "36m left", metadata = CardMetadata(year = 2002, runtime_minutes = 23, genres = listOf("Action"), rating = 8.4, certification = "TV-14"))
        assertEquals(listOf("2002", "23m", "Action", "★ 8.4"), enriched.metadataParts())
        assertEquals(listOf("TV-14"), enriched.presentationBadges())
    }
    @Test fun oldAddonMetadataRemainsVisibleWhenTypedFieldsAreAbsent() {
        assertEquals(listOf("2026", "Drama"), anime.copy(subtitle = "2026 · Drama").metadataParts())
    }
}
