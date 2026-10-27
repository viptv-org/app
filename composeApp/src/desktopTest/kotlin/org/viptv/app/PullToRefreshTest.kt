package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/** Phone pull-to-refresh forces the quiet lane's rebuild (app#1). */
class PullToRefreshTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")
    private fun card(id: String, progress: Double? = null) =
        Card(identity = MediaIdentity(id, "movie", emptyList()), provider_id = "simkl", title = id, badges = emptyList(), progress = progress)
    private fun home(revision: Long, continuing: List<Card>) = HomeSnapshot(
        Scope("server", "owner", "12", revision), revision, hero = card("c1"), featured = listOf(card("c1")), rows = listOf(
            HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = continuing)),
        complete = true)
    private val shown = home(6, listOf(card("old")))
    private fun bootstrap(home: HomeSnapshot, revisions: Map<String, String>) = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(home)); put("integrations", JsonArray(emptyList()))
        put("revisions", JsonObject(revisions.mapValues { (_, value) -> JsonPrimitive(value) }))
    }.toString()

    private inner class Server(val handle: suspend MockRequestHandleScope.(String) -> HttpResponseData) {
        val paths: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        val client = HttpClient(MockEngine { request -> paths += request.url.encodedPath; handle(request.url.encodedPath) }) { configureTransport() }
    }
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    private fun revisionsJson(value: Map<String, String>) = JsonObject(value.mapValues { (_, v) -> JsonPrimitive(v) }).toString()

    private fun CoroutineScope.controller(server: Server, state: ApplicationState =
        ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown)): ApplicationController {
        val controller = ApplicationController(this, store)
        controller.connection = RemoteConnection("https://watch.example", store, server.client, browserMode = false)
        controller.mutable.value = state
        controller.freshness.homeBuilt(base)
        return controller
    }

    @Test fun aPullRebuildsHomeRightAwayEvenWhenNoTokenMovedAndACheckJustRan() = runBlocking<Unit> {
        val busyDuringBuild = mutableListOf<Boolean>()
        lateinit var controller: ApplicationController
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(base))
                "/api/bootstrap" -> { busyDuringBuild += controller.state.value.busy; json(bootstrap(home(7, listOf(card("new", 0.4), card("old"))), base)) }
                else -> error("Unexpected $path")
            }
        }
        controller = controller(server, ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown,
            navBack = listOf(Screen.Library), searchTerm = "draft"))
        // A check finished a moment ago: the automatic lane would coalesce and compare tokens here.
        controller.freshen(); controller.quietJob?.join()
        server.paths.clear()
        controller.pullToRefresh()
        val state = controller.state.value
        assertEquals(listOf("/api/bootstrap"), server.paths, "A pull skips the revision comparison and the check coalescing")
        assertEquals(listOf("new", "old"), state.home?.rows?.first()?.cards?.map { it.identity.id })
        assertEquals(listOf(false), busyDuringBuild, "No full-screen loading cover")
        assertEquals(Screen.Home, state.screen); assertEquals(listOf(Screen.Library), state.navBack); assertEquals("draft", state.searchTerm)
        assertFalse(state.busy); assertNull(state.error)
        controller.connection?.close()
    }

    @Test fun aPullReloadsTheMyListHistoryOrDetailsPageOnDisplay() = runBlocking<Unit> {
        val movie = card("m1")
        val query = defaultLibraryQuery()
        val list = LibrarySnapshot(Scope("server", "owner", "12", 4), 4, query, listOf(card("old")), complete = true,
            availability = Availability.AVAILABLE)
        for (screen in listOf(Screen.Library, Screen.History, Screen.Details)) {
            val server = Server { path ->
                when (path) {
                    "/api/bootstrap" -> json(bootstrap(home(7, listOf(card("new", 0.4))), base))
                    "/api/profiles/12/library" -> json(Json.encodeToString(list.copy(cards = listOf(card("new"), card("old")))))
                    "/api/profiles/12/history" -> json(Json.encodeToString(listOf(HistoryEntry(card("new", 0.4), 1L))))
                    "/api/meta/movie/m1" -> json(Json.encodeToString(DetailSnapshot(movie.copy(progress = 0.4), episodes = emptyList())))
                    else -> error("Unexpected $path")
                }
            }
            val controller = controller(server, ApplicationState(screen = screen, selectedProfile = "12", home = shown,
                libraryQuery = query, libraryPage = list, detail = DetailSnapshot(movie, episodes = emptyList())))
            controller.pullToRefresh()
            val now = controller.state.value
            assertEquals(screen, now.screen); assertFalse(now.busy); assertNull(now.error)
            assertFalse(server.paths.any { it.endsWith("/revisions") }, "No revision comparison on $screen")
            when (screen) {
                Screen.Library -> assertEquals(listOf("new", "old"), now.libraryPage?.cards?.map { it.identity.id })
                Screen.History -> assertEquals(listOf("new"), now.history.map { it.entry.card.identity.id })
                else -> assertEquals(0.4, now.detail?.item?.progress, "Details on display shows the new progress")
            }
            controller.connection?.close()
        }
    }

    @Test fun aFailedPullKeepsTheShownDataAndEndsWithoutAnError() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/bootstrap") json("{}", HttpStatusCode.InternalServerError) else error("Unexpected $path") }
        val controller = controller(server)
        assertNotNull(withTimeoutOrNull(5_000) { controller.pullToRefresh() }, "The pull ends, so the indicator ends")
        assertSame(shown, controller.state.value.home)
        assertEquals(Screen.Home, controller.state.value.screen)
        assertNull(controller.state.value.error); assertFalse(controller.state.value.busy)
        controller.connection?.close()
    }

    @Test fun aPullWithAnExpiredSessionGoesToSignIn() = runBlocking<Unit> {
        val server = Server { json("{}", HttpStatusCode.Unauthorized) }
        val controller = controller(server)
        controller.pullToRefresh()
        assertEquals(Screen.Connect, controller.state.value.screen)
        assertEquals("Session expired. Sign in again.", controller.state.value.error)
        server.client.close()
    }

    @Test fun aPullDuringAnAutomaticCheckEndsOnlyAfterItsOwnRebuild() = runBlocking<Unit> {
        val checking = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> { checking.complete(Unit); release.await(); json(revisionsJson(base)) }
                "/api/bootstrap" -> json(bootstrap(home(7, listOf(card("new"))), base))
                else -> error("Unexpected $path")
            }
        }
        val controller = controller(server)
        controller.freshen(); checking.await()
        val pull = launch(start = CoroutineStart.UNDISPATCHED) { controller.pullToRefresh() }
        delay(100)
        assertFalse(pull.isCompleted, "The indicator stays while the check is still in flight")
        release.complete(Unit); pull.join()
        assertEquals(listOf("/api/profiles/12/revisions", "/api/bootstrap"), server.paths, "Unchanged tokens still rebuild after a pull")
        assertEquals("new", controller.state.value.home?.rows?.first()?.cards?.single()?.identity?.id)
        controller.connection?.close()
    }

    @Test fun nothingIsRefreshedUnderThePlayerOrWithoutAProfile() = runBlocking<Unit> {
        val server = Server { path -> error("Unexpected $path") }
        for (state in listOf(ApplicationState(screen = Screen.Player, selectedProfile = "12", home = shown),
            ApplicationState(screen = Screen.Profiles, selectedProfile = null))) {
            controller(server, state).pullToRefresh()
        }
        assertEquals(emptyList(), server.paths.toList())
        server.client.close()
    }
}
