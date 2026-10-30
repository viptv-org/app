package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * A play tap reads the title's Details while the selector's discovery runs, so both publish into
 * the same state. A publication built from a snapshot it read earlier drops whatever the other
 * coroutine published in between: the page behind the selector stayed empty with no error,
 * because the read itself had succeeded.
 */
class DetailReadPublicationTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt0903747"
    private val origin = MetadataOrigin("stremio", "addon:21", "c".repeat(32))
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Breaking Bad", badges = emptyList(), metadata_origin = origin)
    /** A Continue Watching episode: scoped, so its Details are read behind the selector. */
    private fun tapped() = series.copy(identity = MediaIdentity("$show:2:3", "series", emptyList(), EpisodeCoordinates("aired", 2, 3)),
        parent_identity = series.identity, title = "Episode 3", progress = 0.45)
    private val detail = DetailSnapshot(series, episodes = (1L..5L).map {
        series.copy(identity = MediaIdentity("$show:1:$it", "series", emptyList(), EpisodeCoordinates("aired", 1, it)),
            parent_identity = series.identity, title = "Episode $it")
    })

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun host(detailDelayMillis: Long): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        when (request.url.encodedPath) {
            "/api/meta/series/$show" -> { delay(detailDelayMillis); json(Json.encodeToString(detail)) }
            "/api/streams" -> json("""{"id":"job-1"}""")
            "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
            else -> json("""{"streams":[]}""")
        }
    }

    private fun controllerFor(engine: MockEngine): Pair<ApplicationController, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store)
        controller.connection = RemoteConnection("https://watch.example", store,
            HttpClient(engine) { configureTransport() }, browserMode = false)
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "preview")
        return controller to scope
    }

    @Test fun everyPlayTapLandsItsDetailsWhileTheSelectorFindsSources() = runBlocking<Unit> {
        var lost = 0
        repeat(300) { round ->
            val answer = host(1L + round % 3)
            val (controller, scope) = controllerFor(MockEngine { request -> answer(request) })
            try {
                controller.playTitle(tapped())
                withTimeoutOrNull(2_000) { while (controller.state.value.detail == null) delay(1) }
                val state = controller.state.value
                if (state.detail == null) lost++
                assertEquals(Screen.Sources, state.screen, "round $round: the tap opens the selector")
                assertEquals(tapped().identity, state.sourceTitle?.identity, "round $round: for the tapped episode")
            } finally {
                controller.job?.cancel(); controller.cancelSourceDetail(); scope.cancel(); controller.connection?.close()
            }
        }
        assertEquals(0, lost, "Rounds whose Details read was published and then dropped by another write")
    }
}
