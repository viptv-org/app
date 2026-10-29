package org.viptv.app

import kotlin.test.*
import org.viptv.app.components.metadataParts
import org.viptv.app.components.presentationBadges
import org.viptv.app.components.isNewEpisodesBadge
import org.viptv.app.components.newEpisodesBadge
import org.viptv.app.components.newEpisodesDescription
import org.viptv.app.components.cardChip
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

class NewEpisodesBadgeTest {
    private val show = Card(MediaIdentity("tt100:1:2", "series", emptyList()), provider_id = "addon:1", title = "Fixture Show", badges = emptyList())
    @Test fun recognisesTheServerCountLabelsOnly() {
        assertTrue(isNewEpisodesBadge("+1"))
        assertTrue(isNewEpisodesBadge("+12"))
        assertTrue(isNewEpisodesBadge("New episode"))
        assertFalse(isNewEpisodesBadge("Watched"))
        assertFalse(isNewEpisodesBadge("+"))
        assertFalse(isNewEpisodesBadge("+2 new"))
        assertFalse(isNewEpisodesBadge("4K"))
    }
    @Test fun theCompactLabelIsSpokenInWords() {
        assertEquals("1 new episode", newEpisodesDescription("+1"))
        assertEquals("2 new episodes", newEpisodesDescription("+2"))
        assertEquals("New episode", newEpisodesDescription("New episode"))
        assertEquals("Ended", newEpisodesDescription("Ended"))
    }
    @Test fun theCountLabelWinsTheArtSlotAndNeverBecomesAMetaChip() {
        val card = show.copy(badges = listOf("Simkl", "4K", "+2"))
        assertEquals("+2", card.newEpisodesBadge())
        assertEquals("+2", card.cardChip())
        assertEquals(listOf("4K"), card.presentationBadges())
        assertEquals("4K", show.copy(badges = listOf("Simkl", "4K")).cardChip())
        assertNull(show.cardChip())
        assertNull(show.copy(badges = listOf("Watched")).cardChip())
        assertEquals("+1", show.copy(badges = listOf("Watched", "+1")).cardChip())
    }
}
