package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class UnresolvedTitleSearchTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val origin = MetadataOrigin("stremio", "addon:2", "1f0d6a2c")
    private val unresolved = Card(MediaIdentity("anilist:21", "series", listOf(ExternalId("anilist", "21"))),
        provider_id = "addon:2", title = "One Piece", badges = emptyList(), metadata_origin = origin)
    private val resolved = Card(MediaIdentity("tt0388629", "series", listOf(ExternalId("imdb", "tt0388629"))),
        provider_id = "addon:4", title = "One Piece", badges = emptyList())
    private fun episode(season: Long, number: Long) = resolved.copy(
        identity = MediaIdentity("tt0388629:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = resolved.identity, title = "Episode $season-$number")
    private val found = DetailSnapshot(resolved, episodes = (1L..8L).map { episode(1, it) } + (1L..12L).map { episode(4, it) },
        seasons = listOf(EpisodeSeason(1, 8, 0), EpisodeSeason(4, 12, 8)))
    private val needsSearch = """{"error_code":"title_needs_search","query":"One Piece","type":"series","season":4}"""

    private fun metaWith(detail: DetailSnapshot, vararg fields: Pair<String, Long>): String {
        val value = Json.parseToJsonElement(Json.encodeToString(detail)).jsonObject
        return JsonObject(value + fields.associate { (name, number) -> name to JsonPrimitive(number) }).toString()
    }

    private inner class Host(private val metaStatus: HttpStatusCode = HttpStatusCode.NotFound,
        private val metaBody: String = needsSearch, private val answerResolved: Boolean = false) {
        val metaRequests = mutableListOf<String>()
        val searchRequests = mutableListOf<String>()

        private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
            respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))

        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/search" -> {
                    synchronized(searchRequests) { searchRequests += request.url.parameters["query"].orEmpty() }
                    json(Json.encodeToString(SearchSnapshot(listOf(
                        SearchResultRow("addon:4", "Cinemeta", "stremio", addon_id = 4, cards = listOf(resolved))))))
                }
                path.startsWith("/api/meta/") -> {
                    synchronized(metaRequests) { metaRequests += path }
                    when {
                        answerResolved && path.contains(resolved.identity.id) -> json(Json.encodeToString(found))
                        metaStatus == HttpStatusCode.OK -> json(metaBody)
                        else -> json(metaBody, metaStatus)
                    }
                }
                path == "/api/streams" && request.method == HttpMethod.Post -> json("""{"id":"job-1"}""")
                path == "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)

        fun controller(scope: CoroutineScope, from: Screen = Screen.Home): ApplicationController {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = from, selectedProfile = "2", preferences = PreferencesSnapshot())
            return controller
        }

        fun metas() = synchronized(metaRequests) { metaRequests.toList() }
        fun searches() = synchronized(searchRequests) { searchRequests.toList() }

        fun close(controller: ApplicationController) {
            controller.job?.cancel(); controller.cancelSourceDetail(); controller.releaseRemotePlayback()
            remote.close()
        }
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while (!condition()) delay(5) } } catch (_: TimeoutCancellationException) { fail("timed out waiting for $what") }
    }

    @Test fun anUnresolvedTitleOpensSearchPrefilledWithItsTypeAndSearchesOnce() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.open(unresolved)
            until("the search") { controller.state.value.results.isNotEmpty() }
            val state = controller.state.value
            assertEquals(Screen.Search, state.screen, "Search takes the Details page's place")
            assertEquals("One Piece", state.searchTerm)
            assertEquals(setOf("Series", "Anime"), state.searchFilters, "a series searches its series and anime titles")
            assertEquals(4L, state.pendingDetailSeason, "the unresolved title's season carries into the search")
            assertNull(state.detail, "no Details shell is left behind")
            assertNull(state.error, "no error screen")
            assertEquals(listOf("One Piece"), host.searches(), "one search request")
            assertEquals(listOf("/api/meta/series/anilist:21"), host.metas())
        } finally { host.close(controller) }
    }

    @Test fun backFromThePrefilledSearchReturnsToThePageTheViewerCameFrom() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.open(unresolved)
            until("the search") { controller.state.value.results.isNotEmpty() }
            controller.navigateBack()
            val state = controller.state.value
            assertEquals(Screen.Home, state.screen, "Back returns to the row the card was opened from")
            assertTrue(state.navBack.isEmpty(), "the Details page never entered the Back history")
        } finally { host.close(controller) }
    }

    @Test fun aPlayTapOnAnUnresolvedTitleOpensSearchInsteadOfTheSourceError() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playTitle(unresolved)
            until("the search") { controller.state.value.results.isNotEmpty() }
            val state = controller.state.value
            assertEquals(Screen.Search, state.screen, "Search takes the selector's place")
            assertEquals("One Piece", state.searchTerm)
            assertEquals(setOf("Series", "Anime"), state.searchFilters)
            assertEquals(4L, state.pendingDetailSeason, "the season still carries into the search")
            assertNull(state.error, "no source error")
            assertNull(state.sourceTitle, "the selector is gone")
            assertFalse(state.findingSources)
            assertNull(state.playTapTitle, "the failed play tap is cleared")
            assertEquals(listOf("One Piece"), host.searches(), "one search request")
        } finally { host.close(controller) }
    }

    @Test fun aMovieSearchesMoviesOnly() = runBlocking<Unit> {
        val host = Host(metaBody = """{"error_code":"title_needs_search","query":"Dune","type":"movie"}""")
        val controller = host.controller(this)
        try {
            controller.open(unresolved.copy(identity = MediaIdentity("anilist:9", "movie", emptyList())))
            until("the search") { controller.state.value.searchTerm == "Dune" }
            val state = controller.state.value
            assertEquals(setOf("Movies"), state.searchFilters)
            assertNull(state.pendingDetailSeason, "a body without a season prefills no season")
        } finally { host.close(controller) }
    }

    @Test fun otherFailuresKeepTheirDetailsErrorPage() = runBlocking<Unit> {
        for ((body, retryable) in listOf("""{"error":"Title not found"}""" to true,
            """{"error_code":"metadata_not_found"}""" to false)) {
            val host = Host(metaBody = body)
            val controller = host.controller(this)
            try {
                controller.open(unresolved)
                until("the failure") { !controller.state.value.busy && controller.state.value.error != null }
                val state = controller.state.value
                assertEquals(Screen.Details, state.screen, "$body keeps the Details page")
                assertEquals(retryable, state.detailRetryable, "$body keeps its retry")
                assertTrue(host.searches().isEmpty(), "$body sends no search")
            } finally { host.close(controller) }
        }
    }

    @Test fun aResultOpenedFromTheSearchTakesTheUnresolvedTitlesSeason() = runBlocking<Unit> {
        val host = Host(answerResolved = true)
        val controller = host.controller(this)
        try {
            controller.open(unresolved)
            until("the search") { controller.state.value.results.isNotEmpty() }
            controller.open(resolved)
            until("Details") { controller.state.value.detail != null }
            val state = controller.state.value
            assertEquals(Screen.Details, state.screen)
            assertEquals(DetailFocus(4), state.detailFocus, "the season the unresolved title asked for")
            assertNull(state.pendingDetailSeason, "the season is spent on the first open")
        } finally { host.close(controller) }
    }

    @Test fun aMetaFocusSeasonAndEpisodeReachTheDetailsPage() = runBlocking<Unit> {
        val host = Host(HttpStatusCode.OK, metaWith(found, "focus_season" to 4L, "focus_episode" to 11L))
        val controller = host.controller(this)
        try {
            controller.open(resolved)
            until("Details") { controller.state.value.detail != null }
            assertEquals(DetailFocus(4, 11), controller.state.value.detailFocus)
        } finally { host.close(controller) }
    }

    @Test fun aMetaWithoutFocusLeavesTheDetailsPageOnItsDefaultSeason() = runBlocking<Unit> {
        val host = Host(HttpStatusCode.OK, Json.encodeToString(found))
        val controller = host.controller(this)
        try {
            controller.open(resolved)
            until("Details") { controller.state.value.detail != null }
            assertNull(controller.state.value.detailFocus, "an older server sends no focus fields")
        } finally { host.close(controller) }
    }
}
