package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.components.backendUnreachableLabel
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BackendBannerTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")
    private val started = 1_760_000_000_000L

    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "simkl", title = id, badges = emptyList())
    private fun home(revision: Long, catalog: List<Card> = listOf(card("c1"), card("c2"))) = HomeSnapshot(
        Scope("server", "owner", "12", revision), revision, hero = catalog.first(), featured = catalog, rows = listOf(
            HomeRow(browse = HomeBrowseAction(HomeBrowseTarget.DISCOVER, listOf(
                QueryValue("type", listOf("movie")), QueryValue("catalog", listOf("top")))),
                id = "top", title = "Top", provider_id = "addon:1", state = RowState.READY, cards = catalog)),
        complete = true)
    private val shown = home(6)

    private fun bootstrap(home: HomeSnapshot, revisions: Revisions?) = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(home)); put("integrations", JsonArray(emptyList()))
        revisions?.let { put("revisions", JsonObject(it.mapValues { (_, value) -> JsonPrimitive(value) })) }
    }.toString()

    private inner class Server(val handle: suspend MockRequestHandleScope.(String) -> HttpResponseData) {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request -> paths += request.url.encodedPath; handle(request.url.encodedPath) })
            { configureTransport() }
    }
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    private fun revisionsJson(value: Revisions) = JsonObject(value.mapValues { (_, v) -> JsonPrimitive(v) }).toString()

    private fun CoroutineScope.controller(server: Server, time: TestTimeSource,
        state: ApplicationState = ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown)): ApplicationController {
        val controller = ApplicationController(this, store, freshness = Freshness(time))
        controller.clockMillis = { started }
        controller.connection = RemoteConnection("https://watch.example", store, server.client, browserMode = false)
        controller.mutable.value = state
        controller.freshness.homeBuilt(base)
        return controller
    }
    private suspend fun ApplicationController.settle() { while (true) { val running = quietJob ?: return; running.join(); if (quietJob === running) return } }
    private suspend fun ApplicationController.tick(server: Server, time: TestTimeSource) {
        time += 5.seconds
        freshnessTick(); settle()
    }
    private fun MockRequestHandleScope.answer(path: String): HttpResponseData = when (path) {
        "/api/bootstrap" -> json(bootstrap(home(7, listOf(card("new"))), base))
        "/api/profiles/12/revisions" -> json(revisionsJson(base))
        else -> error("Unexpected $path")
    }

    @Test fun aFailedRequestRaisesTheBannerWithTheTimeTheOutageStarted() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/bootstrap") throw IOException("Connection refused") else error("Unexpected $path") }
        val time = TestTimeSource()
        val controller = controller(server, time)
        time += 5.minutes
        controller.tick(server, time)
        assertTrue(controller.state.value.backendBannerShown)
        assertEquals(started, controller.state.value.backendUnreachableSince, "The banner holds when the backend stopped answering, not the latest attempt")
        time += 5.minutes
        controller.tick(server, time)
        assertEquals(started, controller.state.value.backendUnreachableSince, "A repeat failure is the same outage")
        controller.connection?.close()
    }

    @Test fun dismissHidesTheBannerUntilTheBackendAnswersAndStopsAnsweringAgain() = runBlocking<Unit> {
        var dead = true
        val server = Server { path -> if (dead) throw IOException("Connection refused") else answer(path) }
        val time = TestTimeSource()
        val controller = controller(server, time)
        controller.freshness.homeBuilt(null)
        time += 5.minutes
        controller.tick(server, time)
        assertTrue(controller.state.value.backendBannerShown)

        controller.dismissBackendBanner()
        assertFalse(controller.state.value.backendBannerShown, "Dismiss hides it")
        repeat(6) { controller.tick(server, time) }
        assertFalse(controller.state.value.backendBannerShown, "A dismissed banner stays hidden while the same outage lasts")

        dead = false
        repeat(10) { controller.tick(server, time) }
        assertNull(controller.state.value.backendUnreachableSince, "An answer ends the outage")
        assertFalse(controller.state.value.backendBannerDismissed)

        dead = true
        time += 5.minutes
        controller.tick(server, time)
        assertTrue(controller.state.value.backendBannerShown, "A new outage shows the banner again")
        controller.connection?.close()
    }

    @Test fun anyUnansweredRequestRaisesTheBannerAndTheNextAnswerClearsIt() = runBlocking<Unit> {
        var dead = true
        val server = Server { path -> if (dead) throw IOException("Connection refused") else answer(path) }
        val time = TestTimeSource()
        val controller = controller(server, time, ApplicationState(screen = Screen.Discover, selectedProfile = "12", home = shown))
        runCatching { controller.connection!!.revisions("12") }
        assertTrue(controller.state.value.backendBannerShown, "A failed Browse request means the backend is unreachable")
        dead = false
        controller.connection!!.revisions("12")
        assertFalse(controller.state.value.backendBannerShown, "The next answer clears it")
        controller.connection?.close()
    }

    @Test fun aServerThatAnswersWithAnErrorNeverRaisesTheBanner() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/bootstrap") json("{}", HttpStatusCode.InternalServerError) else error("Unexpected $path") }
        val time = TestTimeSource()
        val controller = controller(server, time)
        controller.freshness.homeBuilt(null)
        time += 5.minutes
        repeat(3) { controller.tick(server, time) }
        assertNull(controller.state.value.backendUnreachableSince, "A server that answered is reachable, whatever it answered")
        controller.connection?.close()
    }

    @Test fun theBannerLabelNamesTheLocalTimeTheBackendStoppedAnswering() {
        val local = java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault())
        val clock = org.viptv.app.format.formatTimeOfDay(local.hour, local.minute, org.viptv.app.format.is24HourTime())
        assertEquals("Backend unreachable since $clock.", backendUnreachableLabel(started))
    }
}
