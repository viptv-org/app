package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.IOException
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BackendOutageTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")

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
        fun attempts() = paths.count { it == "/api/bootstrap" }
    }
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun CoroutineScope.controller(server: Server, time: TestTimeSource,
        state: ApplicationState = ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown)): ApplicationController {
        val controller = ApplicationController(this, store, freshness = Freshness(time))
        controller.connection = RemoteConnection("https://watch.example", store, server.client, browserMode = false)
        controller.mutable.value = state
        controller.freshness.homeBuilt(base)
        return controller
    }
    private suspend fun ApplicationController.settle() { while (true) { val running = quietJob ?: return; running.join(); if (quietJob === running) return } }
    // The failures are counted per tick: a 5 s tick lets at most one attempt through the health gate.
    private suspend fun ApplicationController.tickUntil(server: Server, time: TestTimeSource, ticks: Int, steps: MutableList<Int>) =
        repeat(ticks) { step ->
            val before = server.paths.size
            time += 5.seconds
            freshnessTick(); settle()
            if (server.paths.size > before) steps += step
        }

    @Test fun aDeadBackendIsRetriedOnAWideningIntervalAndARebuildResetsIt() = runBlocking<Unit> {
        var dead = true
        val server = Server { path ->
            if (dead) throw IOException("Connection refused")
            if (path == "/api/bootstrap") json(bootstrap(home(7, listOf(card("new"))), null)) else error("Unexpected $path")
        }
        val time = TestTimeSource()
        val controller = controller(server, time)
        time += 5.minutes

        val attempts = mutableListOf<Int>()
        controller.tickUntil(server, time, 24, attempts)
        assertEquals(listOf(0, 2, 6, 14), attempts, "A dead backend is retried at 10 s, then 20, then 40, not on every tick")
        assertEquals(4, server.attempts())

        dead = false
        val before = server.paths.size
        while (controller.state.value.home?.revision != 7L) {
            check(server.paths.size - before < 10) { "The rebuild never landed" }
            time += 5.seconds
            controller.freshnessTick(); controller.settle()
        }
        dead = true
        time += 5.minutes
        val afterReset = mutableListOf<Int>()
        controller.tickUntil(server, time, 6, afterReset)
        assertEquals(listOf(0, 2), afterReset, "The first failure after a rebuild waits ten seconds again")
        controller.connection?.close()
    }

    @Test fun aServerThatAnswersWithAnErrorBacksOffWithoutClaimingAnOutage() = runBlocking<Unit> {
        val server = Server { path ->
            if (path == "/api/bootstrap") json("{}", HttpStatusCode.InternalServerError) else error("Unexpected $path")
        }
        val time = TestTimeSource()
        val controller = controller(server, time, ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown))
        controller.freshness.homeBuilt(null)
        time += 5.minutes

        val attempts = mutableListOf<Int>()
        controller.tickUntil(server, time, 8, attempts)
        assertEquals(listOf(0, 2, 4, 6), attempts, "A backend that answers a failure retries every ten seconds, never on a 5 s tick")
        controller.connection?.close()
    }
}
