package org.viptv.app

import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SearchGroupsTest {
    private fun card(provider: String, kind: String = "movie") = Card(MediaIdentity("shared-title", kind, emptyList()), provider_id = provider,
        title = "Same title", badges = emptyList())

    @Test fun suppliedRowsKeepProviderProvenanceCategoryTitlesAndPartialFailures() {
        val common = card("stremio")
        val rows = listOf(SearchResultRow("anime", "Simkl Anime", "simkl", cards = listOf(card("simkl", "series").copy(category = "anime"))),
            SearchResultRow("addon:1", "First add-on", "stremio", addon_id = 1, cards = listOf(common)),
            SearchResultRow("addon:2", "Second add-on", "stremio", addon_id = 2, cards = listOf(common), message = "private partial failure"),
            SearchResultRow("addon:3", "Slow add-on", "stremio", addon_id = 3, state = "Failed", message = "private upstream address"))
        val groups = searchResultGroups(rows)
        assertEquals(rows.map { it.title }, groups.map { it.label })
        assertEquals(3, groups.sumOf { it.cards.size })
        assertEquals(common, groups[1].cards.single())
        assertEquals(common, groups[2].cards.single())
        assertNotEquals(searchFocusKey(groups[1], common), searchFocusKey(groups[2], common))
        assertEquals("Some search results are unavailable. Try again.", groups[2].message)
        assertFalse(groups.last().message.orEmpty().contains("private upstream"))
        assertEquals(listOf("First add-on", "Second add-on", "Slow add-on"), searchResultGroups(rows, setOf("Movies")).map { it.label })
    }

    @Test fun nativeAddonKeepsMixedKindsInOneRowAndSimklKeepsItsCategories() {
        val addon = IntegrationConnection("addon:1", "stremio", "account", name = "My add-on", enabled = true, revision = 1, capabilities = emptyList())
        val groups = searchGroups(listOf(card("addon:1"), card("addon:1", "series"), card("simkl"),
            card("simkl", "series").copy(category = "anime")), listOf(addon))
        assertEquals(listOf("My add-on", "Movies", "Anime"), groups.map { it.label })
        assertEquals(listOf("movie", "series"), groups.first().cards.map { it.identity.kind })
    }

    @Test fun rowsWithNothingToShowAreDroppedSoOneEmptyStateRemains() {
        val rows = listOf(SearchResultRow("one", "One", "simkl"), SearchResultRow("two", "Two", "stremio"))
        assertTrue(searchResultGroups(rows).isEmpty())
        assertTrue(searchResultGroups(rows, setOf("Channels")).isEmpty())
        assertEquals("No matching titles", searchNoResultsCopy(rows))
    }

    @Test fun everyProviderFailingLeavesOneFailureStateInsteadOfOneBlockPerProvider() {
        val rows = listOf(SearchResultRow("movies-search", "Movies Search", "stremio", state = "Failed"),
            SearchResultRow("series-search", "Series Search", "stremio", state = "Failed"),
            SearchResultRow("people-movies", "People Search Movies", "stremio", state = "Failed"),
            SearchResultRow("empty-provider", "Empty provider", "stremio", state = "Empty"))
        assertTrue(searchResultGroups(rows).isEmpty())
        assertEquals("This search provider could not respond. Try again.", searchNoResultsCopy(rows))
        assertEquals("No matching titles", searchNoResultsCopy(rows.filter { it.state != "Failed" }))
    }

    @Test fun aFilterThatLeavesOnlyFailedProvidersStillSaysNoMatchingTitles() {
        val rows = listOf(SearchResultRow("movies", "Movie provider", "stremio", state = "Failed"),
            SearchResultRow("channels", "Channel provider", "stremio", cards = listOf(card("stremio", "movie"))))
        assertTrue(searchResultGroups(rows, setOf("Channels")).isEmpty())
        assertEquals("No matching titles", searchNoResultsCopy(rows))
    }

    @Test fun aFilterKeepsOnlyTheProvidersItMatches() {
        val rows = listOf(SearchResultRow("movies", "Movie provider", "stremio", cards = listOf(card("stremio", "movie"))),
            SearchResultRow("channels", "Channel provider", "stremio", cards = listOf(card("stremio", "channel"))))
        assertEquals(listOf("Movie provider", "Channel provider"), searchResultGroups(rows).map { it.label })
        assertEquals(listOf("Movie provider"), searchResultGroups(rows, setOf("Movies")).map { it.label })
        assertEquals(listOf("Channel provider"), searchResultGroups(rows, setOf("Channels")).map { it.label })
    }

    @Test fun providersThatCouldNotAnswerSitBelowTheProvidersThatReturnedResults() {
        val rows = listOf(SearchResultRow("simkl:anime", "Anime", "simkl", state = "Failed"),
            SearchResultRow("simkl:movie", "Movies", "simkl", state = "Failed"),
            SearchResultRow("addon:1", "Cinemeta", "stremio", addon_id = 1, cards = listOf(card("addon:1"))),
            SearchResultRow("simkl:tv", "TV Shows", "simkl", state = "Failed"))
        assertEquals(listOf("Cinemeta", "Anime", "Movies", "TV Shows"), searchResultGroups(rows).map { it.label })
        assertTrue(searchResultGroups(rows.filter { it.state == "Failed" }).isEmpty())
    }

    @Test fun episodeCoordinatesDoNotCollide() {
        val first = card("addon", "series").copy(identity = MediaIdentity("same", "series", emptyList(), EpisodeCoordinates("aired", 1, 1)))
        val second = first.copy(identity = first.identity.copy(episode = EpisodeCoordinates("aired", 1, 2)))
        assertNotEquals(searchCardKey(first), searchCardKey(second))
    }
}
