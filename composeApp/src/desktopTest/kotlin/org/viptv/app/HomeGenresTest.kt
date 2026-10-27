package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/** The Home Genres chip offers the genres Browse offers and opens Browse filtered to one. */
class HomeGenresTest {
    private val available = FeatureAvailability("Available")
    private fun genre(kind: ControlKind, vararg choices: Pair<String, String>) =
        QueryControl("genre", "Genre", kind, false, choices.map { Choice(it.first, it.second) })
    private fun controls(controls: List<QueryControl> = emptyList(), catalogs: List<DiscoveryCatalog> = emptyList(), discovery: FeatureAvailability = available) =
        DiscoveryControls("stremio", listOf("movie", "series"), controls, catalogs, discovery, available, available, FeatureAvailability("Unsupported"))

    @Test fun catalogGenresComeFromTheDefaultCatalogAndKeepItInTheQuery() {
        val required = DiscoveryCatalog("by-year", "By year", "movie", "addon:1", 1,
            listOf(QueryControl("year", "Year", ControlKind.SINGLECHOICE, true, emptyList()), genre(ControlKind.MULTICHOICE, "Horror" to "Horror")))
        val popular = DiscoveryCatalog("top", "Popular", "movie", "addon:1", 1,
            listOf(genre(ControlKind.MULTICHOICE, "Action" to "Action", "Comedy" to "Comedy", "Action" to "Action again")))
        val series = DiscoveryCatalog("top", "Popular", "series", "addon:1", 1, listOf(genre(ControlKind.MULTICHOICE, "Drama" to "Drama")))
        val choices = controls(catalogs = listOf(required, popular, series)).genreChoices("movie")
        assertEquals(listOf("Action", "Comedy"), choices.map { it.label })
        assertEquals(BrowseQuery("movie", "top", "addon:1", genre = "Comedy"), choices[1].query)
    }

    @Test fun providerGenresSkipTheirAnyChoice() {
        val choices = controls(listOf(genre(ControlKind.SINGLECHOICE, "all" to "All genres", "" to "Any", "science-fiction" to "Science Fiction"))).genreChoices("movie")
        assertEquals(listOf(GenreChoice("Science Fiction", BrowseQuery("movie", genre = "science-fiction"))), choices)
    }

    @Test fun noGenresWhenBrowseHasNoneOrIsUnavailable() {
        assertEquals(emptyList(), controls(catalogs = listOf(DiscoveryCatalog("top", "Popular", "movie", "addon:1", 1))).genreChoices("movie"))
        assertEquals(emptyList(), controls(listOf(genre(ControlKind.SINGLECHOICE, "drama" to "Drama").copy(maximum_selections = 0))).genreChoices("movie"))
        assertEquals(emptyList(), controls(listOf(genre(ControlKind.SINGLECHOICE, "drama" to "Drama")), discovery = FeatureAvailability("Unavailable")).genreChoices("movie"))
    }

    @Test fun homeLoadsGenresOncePerProfileAndAGenreOpensFilteredBrowseThatBackLeaves() = runBlocking {
        val requests = mutableListOf<Url>()
        val client = HttpClient(MockEngine { request ->
            synchronized(requests) { requests += request.url }
            val body = when (request.url.encodedPath) {
                "/api/discovery-controls" -> """{"provider_id":"stremio","media_types":["movie"],"controls":[],"catalogs":[{"id":"top","name":"Popular","type":"movie","connection_id":"addon:1","addon_id":1,
                    "controls":[{"id":"genre","label":"Genre","kind":"MultiChoice","required":false,"choices":[{"value":"Action","label":"Action"},{"value":"Comedy","label":"Comedy"}]}]}],
                    "discovery":{"availability":"Available"},"search":{"availability":"Available"},"filters":{"availability":"Available"},"calendar":{"availability":"Unsupported"}}"""
                "/api/discover" -> """{"cards":[],"complete":true}"""
                else -> return@MockEngine respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        fun home(revision: Long) = HomeSnapshot(Scope("server", "account", "qa", 1), revision, rows = emptyList(), complete = true)
        fun controlRequests() = synchronized(requests) { requests.count { it.encodedPath == "/api/discovery-controls" } }
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "qa", home = home(1))
        try {
            controller.loadHomeGenres()
            val genres = withTimeout(5_000) { controller.state.first { it.homeGenres != null } }.homeGenres!!
            assertEquals("qa", genres.profile)
            assertEquals(listOf("Action", "Comedy"), genres.choices.map { it.label })
            assertEquals("movie", synchronized(requests) { requests.single() }.parameters["type"])
            controller.loadHomeGenres()
            assertEquals(1, controlRequests(), "Home asks once per profile and Home revision")
            // An add-on or provider change gives Home a new revision, and with it fresh genres.
            controller.mutable.value = controller.state.value.copy(home = home(2))
            controller.loadHomeGenres()
            withTimeout(5_000) { controller.state.first { it.homeGenres?.homeRevision == 2L } }
            assertEquals(2, controlRequests())

            controller.browseGenre(genres.choices[1])
            val browsed = withTimeout(5_000) { controller.state.first { !it.busy && it.browseQuery.genre != null } }
            assertEquals(Screen.Discover, browsed.screen)
            assertEquals(BrowseQuery("movie", "top", "addon:1", genre = "Comedy"), browsed.browseQuery)
            val discover = synchronized(requests) { requests.filter { it.encodedPath == "/api/discover" } }
            assertTrue(discover.isNotEmpty() && discover.all { it.parameters["genre"] == "Comedy" }, "every Browse request carries the genre: $discover")
            assertEquals(listOf(Screen.Home), browsed.navBack)

            controller.navigateBack()
            assertEquals(Screen.Home, controller.state.value.screen)
        } finally { scope.cancel(); remote.close() }
    }
}
