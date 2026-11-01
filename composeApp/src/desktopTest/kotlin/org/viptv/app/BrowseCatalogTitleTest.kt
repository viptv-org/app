package org.viptv.app

import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.catalogSource
import org.viptv.app.connection.catalogTitle
import kotlin.test.*
import kotlinx.serialization.json.Json

class BrowseCatalogTitleTest {
    @Test fun serverCatalogTitleIsUsedWhenPresent() {
        val catalog = Json.decodeFromString<DiscoveryCatalog>("""{"id":"top","name":"Popular","type":"movie","title":"Popular Movies · Friendly provider"}""")

        assertEquals("Popular Movies · Friendly provider", catalog.catalogTitle(listOf(catalog)))
    }

    @Test fun missingServerCatalogTitleFallsBackToCurrentLabel() {
        val catalog = Json.decodeFromString<DiscoveryCatalog>("""{"id":"top","name":"Popular","type":"movie"}""")

        assertNull(catalog.title)
        assertEquals("Popular Movies", catalog.catalogTitle(listOf(catalog)))
    }

    private fun catalogs(rows: List<Triple<String, String, String>>): List<DiscoveryCatalog> =
        rows.mapIndexed { index, (name, type, addon) ->
            DiscoveryCatalog("c$index", name, type, "addon:$index", index.toLong(), connection_name = addon)
        }

    private fun titles(vararg rows: Triple<String, String, String>): List<String> {
        val catalogs = catalogs(rows.toList())
        return catalogs.map { it.catalogTitle(catalogs) }
    }

    @Test fun oneCatalogSaysItsType() {
        assertEquals(listOf("Popular Movies"), titles(Triple("Popular", "movie", "Cinemeta")))
    }

    @Test fun catalogsOfDifferentTypesDifferByTypeAlone() {
        assertEquals(listOf("Popular Movies", "Popular Series"),
            titles(Triple("Popular", "movie", "Cinemeta"), Triple("Popular", "series", "Cinemeta")))
    }

    @Test fun aClashAcrossAddOnsNamesEachAddOn() {
        assertEquals(listOf("Popular Movies · Cinemeta", "Popular Series", "Popular Movies · Torrentio"),
            titles(Triple("Popular", "movie", "Cinemeta"), Triple("Popular", "series", "Cinemeta"),
                Triple("Popular", "movie", "Torrentio")))
    }

    @Test fun aNameThatAlreadySaysItsTypeIsNotDoubled() {
        assertEquals(listOf("Top Movies", "New Series", "top movie picks", "Anime Season"),
            titles(Triple("Top Movies", "movie", "A"), Triple("New Series", "series", "A"),
                Triple("top movie picks", "movie", "A"), Triple("Anime Season", "anime", "A")))
    }

    @Test fun aDoubledNameStillCountsTowardAClash() {
        assertEquals(listOf("Popular Movies · Cinemeta", "Popular Movies · Other"),
            titles(Triple("Popular", "movie", "Cinemeta"), Triple("Popular Movies", "movie", "Other")))
    }

    @Test fun aClashIgnoresLetterCase() {
        assertEquals(listOf("popular Movies · A", "Popular Movies · B"),
            titles(Triple("popular", "movie", "A"), Triple("Popular", "movie", "B")))
    }

    @Test fun aTypeTheClientDoesNotKnowIsTitleCased() {
        assertEquals(listOf("Live Events Live Sports", "Live Sports Now"),
            titles(Triple("Live Events", "live_sports", "A"), Triple("Live Sports Now", "live_sports", "A")))
    }

    @Test fun theAddOnNameIsOneLineBoundedWithAFallback() {
        assertEquals(listOf("Popular Movies · BadName", "Popular Movies · Stremio addon",
            "Popular Movies · ${"N".repeat(128)}"),
            titles(Triple("Popular", "movie", "  Bad\nName\u0007  "), Triple("Popular", "movie", " "),
                Triple("Popular", "movie", "N".repeat(300))))
    }

    @Test fun theSourceLineNamesTheAddOnOnlyOnce() {
        val one = catalogs(listOf(Triple("Popular", "movie", "Cinemeta")))
        assertEquals("Popular Movies from Cinemeta", one.single().catalogSource(one))
        val clashing = catalogs(listOf(Triple("Popular", "movie", "Cinemeta"), Triple("Popular", "movie", "Torrentio")))
        assertEquals("Popular Movies · Cinemeta", clashing.first().catalogSource(clashing))
        val nameless = catalogs(listOf(Triple("Popular", "movie", "")))
        assertEquals("Popular Movies", nameless.single().catalogSource(nameless))
    }
}
