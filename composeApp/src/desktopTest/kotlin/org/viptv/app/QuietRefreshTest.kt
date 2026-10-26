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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class QuietRefreshTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")
    private fun card(id: String, progress: Double? = null, kind: String = "movie", episode: EpisodeCoordinates? = null) =
        Card(identity = MediaIdentity(id, kind, emptyList(), episode), provider_id = "simkl", title = id, badges = emptyList(), progress = progress)
    private fun home(revision: Long, continuing: List<Card>, catalog: List<Card> = listOf(card("c1"), card("c2"))) = HomeSnapshot(
        Scope("server", "owner", "12", revision), revision, hero = catalog.first(), featured = catalog, rows = listOf(
            HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = continuing),
            HomeRow(browse = HomeBrowseAction(HomeBrowseTarget.DISCOVER, listOf(
                QueryValue("type", listOf("movie")), QueryValue("catalog", listOf("top")))),
                id = "top", title = "Top", provider_id = "addon:1", state = RowState.READY, cards = catalog, next_cursor = "2")),
        complete = true)
    private val shown = home(6, listOf(card("old")))
    private fun bootstrap(home: HomeSnapshot, revisions: Map<String, String>?) = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(home)); put("integrations", JsonArray(emptyList()))
        revisions?.let { put("revisions", JsonObject(it.mapValues { (_, value) -> JsonPrimitive(value) })) }
    }.toString()

    private inner class Server(val handle: suspend MockRequestHandleScope.(String) -> HttpResponseData) {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request -> paths += request.url.encodedPath; handle(request.url.encodedPath) }) { configureTransport() }
    }
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    private fun revisionsJson(value: Map<String, String>) = JsonObject(value.mapValues { (_, v) -> JsonPrimitive(v) }).toString()

    private fun CoroutineScope.controller(server: Server, time: TestTimeSource = TestTimeSource(), state: ApplicationState =
        ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown)): ApplicationController {
        val controller = ApplicationController(this, store, freshness = Freshness(time))
        controller.connection = RemoteConnection("https://watch.example", store, server.client, browserMode = false)
        controller.mutable.value = state
        controller.freshness.homeBuilt(base)
        return controller
    }
    /** Android stop, a change made elsewhere, then start and resume of the same return. */
    private suspend fun ApplicationController.returnAfter(elsewhere: () -> Unit) {
        appForeground(false); elsewhere()
        appForeground(true); freshen(); settle()
    }
    private suspend fun ApplicationController.settle() { while (true) { val running = quietJob ?: return; running.join(); if (quietJob === running) return } }

    @Test fun unchangedRevisionsMakeOneRevisionsRequestAndNeverRebuildHome() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/profiles/12/revisions") json(revisionsJson(base)) else error("Unexpected $path") }
        val controller = controller(server)
        controller.freshen(); controller.settle()
        assertEquals(listOf("/api/profiles/12/revisions"), server.paths)
        assertSame(shown, controller.state.value.home)
        controller.connection?.close()
    }

    @Test fun changedRevisionsReplaceHomeSilentlyAndKeepTheViewersPlace() = runBlocking<Unit> {
        val moved = base + ("library" to "5")
        val fresh = home(7, listOf(card("new", 0.4), card("old")))
        val busyDuringBuild = mutableListOf<Boolean>()
        lateinit var controller: ApplicationController
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(moved))
                "/api/bootstrap" -> { busyDuringBuild += controller.state.value.busy; json(bootstrap(fresh, moved)) }
                else -> error("Unexpected $path")
            }
        }
        val time = TestTimeSource()
        controller = controller(server, time, ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown,
            navBack = listOf(Screen.Library), searchTerm = "draft"))
        controller.freshen(); controller.settle()
        val state = controller.state.value
        assertEquals(listOf(false), busyDuringBuild, "A background refresh never shows a busy Home")
        assertEquals(listOf("new", "old"), state.home?.rows?.first()?.cards?.map { it.identity.id })
        assertEquals(Screen.Home, state.screen); assertEquals(listOf(Screen.Library), state.navBack)
        assertEquals("draft", state.searchTerm); assertNull(state.error); assertFalse(state.busy)
        server.paths.clear()
        time += 15.seconds
        controller.freshnessTick(); controller.settle()
        assertEquals(listOf("/api/profiles/12/revisions"), server.paths, "The rebuilt Home's revisions become the baseline")
        controller.connection?.close()
    }

    @Test fun backgroundRefreshNeverCancelsTheViewersOperation() = runBlocking<Unit> {
        val moved = base + ("addons" to "a2")
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(moved))
                "/api/bootstrap" -> json(bootstrap(home(9, listOf(card("old"))), moved))
                else -> error("Unexpected $path")
            }
        }
        val controller = controller(server)
        val gate = CompletableDeferred<Unit>()
        controller.run { gate.await() }
        val operation = requireNotNull(controller.job)
        controller.freshen(); controller.settle()
        assertTrue(operation.isActive, "The viewer's operation keeps running")
        assertTrue(controller.state.value.busy)
        assertEquals(9L, controller.state.value.home?.revision)
        gate.complete(Unit); operation.join()
        controller.connection?.close()
    }

    @Test fun failedRefreshKeepsHomeWithoutAnErrorAndRetriesAtTheNextCheck() = runBlocking<Unit> {
        val moved = base + ("library" to "5")
        var fail = true
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(moved))
                "/api/bootstrap" -> if (fail) json("{}", HttpStatusCode.InternalServerError) else json(bootstrap(home(7, listOf(card("new"))), moved))
                else -> error("Unexpected $path")
            }
        }
        val time = TestTimeSource()
        val controller = controller(server, time)
        controller.freshen(); controller.settle()
        assertSame(shown, controller.state.value.home); assertNull(controller.state.value.error)
        fail = false; time += 15.seconds
        controller.freshnessTick(); controller.settle()
        assertEquals("new", controller.state.value.home?.rows?.first()?.cards?.single()?.identity?.id)
        controller.connection?.close()
    }

    @Test fun aResultForAReplacedProfileIsDropped() = runBlocking<Unit> {
        val moved = base + ("library" to "5")
        lateinit var controller: ApplicationController
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(moved))
                "/api/bootstrap" -> {
                    controller.mutable.value = controller.mutable.value.copy(selectedProfile = "13", home = null)
                    json(bootstrap(home(7, listOf(card("new"))), moved))
                }
                else -> error("Unexpected $path")
            }
        }
        controller = controller(server)
        controller.freshen(); controller.settle()
        assertNull(controller.state.value.home); assertEquals("13", controller.state.value.selectedProfile)
        controller.connection?.close()
    }

    @Test fun aRequestDuringARefreshRunsAFollowUpCheck() = runBlocking<Unit> {
        val first = base + ("library" to "5")
        val second = base + ("library" to "6")
        val building = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var revisions = first
        var builds = 0
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(revisions))
                "/api/bootstrap" -> {
                    builds++
                    if (builds == 1) { building.complete(Unit); release.await() }
                    json(bootstrap(home(6L + builds, listOf(card("v$builds"))), if (builds == 1) first else second))
                }
                else -> error("Unexpected $path")
            }
        }
        val controller = controller(server)
        controller.freshen(); building.await()
        revisions = second
        controller.requestQuiet(QuietWork.Check)
        release.complete(Unit); controller.settle()
        assertEquals(2, builds, "A change committed during the build is not acknowledged as rendered")
        assertEquals("v2", controller.state.value.home?.rows?.first()?.cards?.single()?.identity?.id)
        controller.connection?.close()
    }

    @Test fun olderServersFallBackToRefreshingAStaleHomeWhenTheViewerIsIdle() = runBlocking<Unit> {
        var builds = 0
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json("{}", HttpStatusCode.NotFound)
                "/api/bootstrap" -> { builds++; json(bootstrap(home(7, listOf(card("new"))), null)) }
                else -> error("Unexpected $path")
            }
        }
        val time = TestTimeSource()
        val controller = controller(server, time)
        controller.freshen(); controller.settle()
        assertEquals(0, builds)
        time += 5.minutes
        controller.userActivity()
        controller.freshnessTick(); controller.settle()
        assertEquals(0, builds, "An active viewer is never interrupted by an age-based rebuild")
        time += 20.seconds
        controller.freshnessTick(); controller.settle()
        assertEquals(1, builds)
        assertEquals(listOf("/api/profiles/12/revisions", "/api/bootstrap"), server.paths, "No revisions polling after a 404")
        controller.connection?.close()
    }

    @Test fun theOpenPlayerIsNeverRefreshedUnder() = runBlocking<Unit> {
        val server = Server { path -> error("Unexpected $path") }
        val time = TestTimeSource()
        val controller = controller(server, time, ApplicationState(screen = Screen.Player, selectedProfile = "12", home = shown))
        time += 10.minutes
        controller.freshen(); controller.freshnessTick(); controller.settle()
        assertEquals(emptyList(), server.paths)
        controller.connection?.close()
    }

    @Test fun anExpiredSessionDuringACheckReturnsToConnect() = runBlocking<Unit> {
        val server = Server { path -> json("{}", HttpStatusCode.Unauthorized) }
        val controller = controller(server)
        controller.freshen(); controller.settle()
        assertEquals(Screen.Connect, controller.state.value.screen)
        assertEquals("Session expired. Sign in again.", controller.state.value.error)
    }

    @Test fun closingThePlayerRefreshesContinueWatchingAndTheDetailsItReturnsTo() = runBlocking<Unit> {
        val episode = EpisodeCoordinates("aired", 2, 3)
        val series = card("tt1", kind = "series")
        fun ep(n: Long, progress: Double?) = card("tt1:2:$n", progress, "series", EpisodeCoordinates("aired", 2, n)).copy(parent_identity = series.identity)
        val detail = DetailSnapshot(series, episodes = listOf(ep(1, null), ep(2, null), ep(3, null)), next_cursor = null,
            seasons = listOf(EpisodeSeason(1, 8, 0), EpisodeSeason(2, 3, 8)))
        val saved = CompletableDeferred<Unit>()
        val fresh = home(7, listOf(ep(3, 0.5)))
        val server = Server { path ->
            when (path) {
                "/api/bootstrap" -> { assertTrue(saved.isCompleted, "Home is rebuilt after the final progress save"); json(bootstrap(fresh, base + ("library" to "5"))) }
                "/api/meta/series/tt1" -> json(Json.encodeToString(detail.copy(episodes = listOf(ep(1, null), ep(2, null), ep(3, 0.5)))))
                else -> error("Unexpected $path")
            }
        }
        val controller = controller(server, state = ApplicationState(screen = Screen.Sources, selectedProfile = "12", home = shown,
            detail = detail, sourceTitle = ep(3, null)))
        controller.progressJob = launch { saved.await() }
        controller.closePlayer()
        saved.complete(Unit); controller.settle()
        val state = controller.state.value
        assertEquals(listOf("tt1:2:3"), state.home?.rows?.first()?.cards?.map { it.identity.id })
        assertEquals(listOf(null, null, 0.5), state.detail?.episodes?.map { it.progress })
        assertTrue(server.paths.any { it == "/api/meta/series/tt1" })
        controller.connection?.close()
    }

    @Test fun returningToHomeChecksImmediatelyAndBackgroundStopsChecks() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/profiles/12/revisions") json(revisionsJson(base)) else error("Unexpected $path") }
        val time = TestTimeSource()
        val controller = controller(server, time, ApplicationState(screen = Screen.Library, selectedProfile = "12", home = shown))
        controller.navigate(Screen.Home); controller.settle()
        assertEquals(listOf("/api/profiles/12/revisions"), server.paths)
        controller.freshness.foreground(false)
        time += 1.minutes
        controller.freshnessTick(); controller.freshen(); controller.settle()
        assertEquals(1, server.paths.size, "A background client makes no requests")
        controller.connection?.close()
    }

    @Test fun aQuickReturnShowsAContinueWatchingChangeMadeElsewhere() = runBlocking<Unit> {
        val moved = base + ("library" to "5")
        var revisions = base
        val server = Server { path ->
            when (path) {
                "/api/profiles/12/revisions" -> json(revisionsJson(revisions))
                "/api/bootstrap" -> json(bootstrap(home(7, listOf(card("new", 0.4), card("old"))), moved))
                else -> error("Unexpected $path")
            }
        }
        val time = TestTimeSource()
        val controller = controller(server, time)
        controller.returnAfter {}
        time += 3.seconds
        controller.returnAfter { revisions = moved }
        assertEquals(listOf("new", "old"), controller.state.value.home?.rows?.first()?.cards?.map { it.identity.id })
        assertEquals(listOf("/api/profiles/12/revisions", "/api/profiles/12/revisions", "/api/bootstrap"), server.paths)
        assertFalse(controller.state.value.busy); assertNull(controller.state.value.error)
        controller.appForeground(false); controller.connection?.close()
    }

    @Test fun theStartAndResumeOfOneReturnMakeOneCheck() = runBlocking<Unit> {
        val server = Server { path -> if (path == "/api/profiles/12/revisions") json(revisionsJson(base)) else error("Unexpected $path") }
        val controller = controller(server)
        controller.returnAfter {}
        assertEquals(listOf("/api/profiles/12/revisions"), server.paths)
        controller.appForeground(false); controller.connection?.close()
    }

    @Test fun aReturnWithAMovedLibraryTokenReloadsTheScreenOnDisplay() = runBlocking<Unit> {
        val moved = base + ("library" to "5")
        var revisions = base
        val movie = card("m1")
        val query = defaultLibraryQuery()
        val list = LibrarySnapshot(Scope("server", "owner", "12", 4), 4, query, listOf(card("old")), complete = true,
            availability = Availability.AVAILABLE)
        for (screen in listOf(Screen.Library, Screen.History, Screen.Details)) {
            revisions = base
            val server = Server { path ->
                when (path) {
                    "/api/profiles/12/revisions" -> json(revisionsJson(revisions))
                    "/api/bootstrap" -> json(bootstrap(home(7, listOf(card("new", 0.4))), moved))
                    "/api/profiles/12/library" -> json(Json.encodeToString(list.copy(cards = listOf(card("new"), card("old")))))
                    "/api/profiles/12/history" -> json(Json.encodeToString(listOf(HistoryEntry(card("new", 0.4), 1L))))
                    "/api/meta/movie/m1" -> json(Json.encodeToString(DetailSnapshot(movie.copy(progress = 0.4), episodes = emptyList())))
                    else -> error("Unexpected $path")
                }
            }
            val controller = controller(server, state = ApplicationState(screen = screen, selectedProfile = "12", home = shown,
                libraryQuery = query, libraryPage = list, detail = DetailSnapshot(movie, episodes = emptyList())))
            controller.returnAfter { revisions = moved }
            val state = controller.state.value
            assertEquals(screen, state.screen); assertFalse(state.busy); assertNull(state.error)
            assertEquals(listOf("new"), state.home?.rows?.first()?.cards?.map { it.identity.id }, "Home behind $screen is current")
            when (screen) {
                Screen.Library -> assertEquals(listOf("new", "old"), state.libraryPage?.cards?.map { it.identity.id })
                Screen.History -> assertEquals(listOf("new"), state.history.map { it.entry.card.identity.id })
                else -> assertEquals(0.4, state.detail?.item?.progress, "Details on display shows the new progress")
            }
            controller.appForeground(false); controller.connection?.close()
        }
    }

    @Test fun aRefreshKeepsTheTailOfARowTheViewerPaged() {
        val paged = shown.copy(rows = shown.rows.map { if (it.id == "top") it.copy(cards = (1..6).map { n -> card("c$n") }, next_cursor = "6") else it })
        val fresh = home(7, listOf(card("new")), catalog = listOf(card("c0"), card("c1")))
        val merged = requireNotNull(mergeHome(paged, fresh))
        val top = merged.rows.single { it.id == "top" }
        assertEquals(listOf("c0", "c1", "c2", "c3", "c4", "c5", "c6"), top.cards.map { it.identity.id })
        assertEquals("6", top.next_cursor)
        assertEquals(listOf("new"), merged.rows.first().cards.map { it.identity.id }, "Continue Watching takes the fresh row")
        val unpaged = requireNotNull(mergeHome(shown, fresh))
        assertEquals(fresh.rows, unpaged.rows)
        val otherProfile = fresh.copy(scope = fresh.scope.copy(profile_id = "13"))
        assertSame(otherProfile, mergeHome(paged, otherProfile))
    }
}
