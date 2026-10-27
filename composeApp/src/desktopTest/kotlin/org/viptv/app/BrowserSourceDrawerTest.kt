package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

/**
 * The web source drawer opens over the title's Details dialog instead of replacing it
 * (app#60). The real browser App shell at 1440x900 over a fake host shaped like the KMP
 * dev host (a Stremio-imported Cinemeta series, as in [DetailsResumeSeasonTest]): Details
 * open on the continued season (app#40), the viewer scrolls, picks an episode, and the
 * drawer slides in over the still-visible dialog. Closing it leaves the dialog exactly as
 * it was, with no Details refetch. With VIPTV_SOURCE_DRAWER_CAPTURE_DIR set it writes the
 * PR screenshots.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class BrowserSourceDrawerTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt13293588"
    private val title = "Mushoku Tensei: Jobless Reincarnation"
    private val seasons = listOf(0L to 4L, 1L to 23L, 2L to 24L, 3L to 14L)
    private val origin = """{"connection_id":"addon:21","incarnation":"${"c".repeat(32)}","provider_id":"stremio"}"""
    private val parentIdentity = """{"edition":null,"episode":null,"id":"$show","ids":[{"namespace":"imdb","value":"$show"},""" +
        """{"namespace":"tmdb","value":"94664"}],"kind":"series"}"""
    private fun episodeIdentity(season: Long, episode: Long) =
        """{"edition":null,"episode":{"episode":$episode,"order":"aired","season":$season},"id":"$show:$season:$episode","ids":[],"kind":"series"}"""
    private val all = seasons.flatMap { (season, count) -> (1L..count).map { season to it } }

    private fun metaEpisode(season: Long, episode: Long) = """{"badges":[],"category":"series","description":"Rudeus learns a little more about the world.",
        "identity":${episodeIdentity(season, episode)},"landscape":null,"logo":null,"metadata_origin":$origin,
        "parent_identity":$parentIdentity,"poster":null,"progress":null,"provider_id":"addon:21","subtitle":null,
        "title":"Episode $season-$episode"}"""

    private fun metaPage(offset: Int, limit: Int): String {
        val window = all.drop(offset).take(limit)
        val next = (offset + window.size).takeIf { it < all.size }?.let { "\"$it\"" } ?: "null"
        var start = 0L
        val index = seasons.joinToString(",") { (season, count) ->
            """{"episode_count":$count,"offset":$start,"season":$season}""".also { start += count }
        }
        return """{"item":{"badges":[],"category":"series","identity":$parentIdentity,"metadata_origin":$origin,"parent_identity":null,
            "provider_id":"addon:21","subtitle":"2021–","title":"$title","description":"A man reincarnated in a world of magic."},
            "episodes":[${window.joinToString(",") { (s, e) -> metaEpisode(s, e) }}],"next_cursor":$next,"recommendations":[],"seasons":[$index]}"""
    }

    private val bootstrap = """{"contract_version":3,
        "identity":{"account_id":"1","profile_id":"2","profiles":[{"id":"2","name":"Stremio import QA"}]},
        "home":{"complete":true,"featured":[],"hero":null,"revision":1818,
            "scope":{"account_id":"1","connection_id":"server","profile_id":"2","revision":0},
            "rows":[{"browse":{"query":[],"target":"Continue"},"cards":[{"badges":[],"description":null,"identity":${episodeIdentity(2, 8)},
                "landscape":null,"logo":null,"parent_identity":{"edition":null,"episode":null,"id":"$show","ids":[],"kind":"series"},
                "poster":null,"progress":0.42,"provider_id":"library","subtitle":"S2 E8 · 14m left","title":"$title"}],"id":"continue",
                "message":null,"next_cursor":null,"provider_id":"library","state":"Ready","title":"Continue Watching"}]}}"""

    /** Torrent-style source names with the long release lines that wrapped in the 460 dp drawer. */
    private val streams = (1..4).joinToString(",") { n ->
        """{"id":"s$n","name":"Torrentio ${if (n == 1) "4K" else "1080p"} RD+","quality_height":${if (n == 1) 2160 else 1080},
            "description":"Mushoku.Tensei.Jobless.Reincarnation.S02E10.Turning.Point.$n.1080p.CR.WEB-DL.DUAL.AAC2.0.H.264.MSubs-ToonsHub 👤 412 💾 1.4 GB ⚙️ ThePirateBay",
            "release_type":"web_dl","delivery":"direct"}"""
    }

    private val requests = mutableListOf<String>()
    private fun requested() = synchronized(requests) { requests.toList() }

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    private fun MockRequestHandleScope.host(request: HttpRequestData) = when (val path = request.url.encodedPath) {
        "/api/bootstrap" -> json(bootstrap)
        "/api/meta/series/$show" -> {
            val offset = request.url.parameters["offset"]?.toInt() ?: 0
            synchronized(requests) { requests += "meta@$offset" }
            json(metaPage(offset, request.url.parameters["limit"]?.toInt() ?: 24))
        }
        "/api/streams" -> { synchronized(requests) { requests += "streams" }; json("""{"id":"job-1"}""") }
        "/api/streams/job-1" -> json("""{"done":true,"events":[{"source":"addon:torrentio","streams":[$streams]}]}""")
        else -> if (path.endsWith("/source-ranking")) json("""{"streams":[$streams],"best_match_id":"s1"}""")
            else respond("{\"error\":\"not in this fixture\"}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    /** The browser App shell at [width]x[height] dp over the fake host, signed into the QA profile. */
    private inner class BrowserApp(val width: Int = 1440, val height: Int = 900) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request -> host(request) }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        private val captureDir = System.getenv("VIPTV_SOURCE_DRAWER_CAPTURE_DIR")?.let(::File)
        val density = if (captureDir != null) 2f else 1f
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene: ImageComposeScene
        private var frame = 0L

        init {
            val boot = runBlocking { remote.bootstrap() }
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = boot.selected ?: "2",
                profiles = boot.profiles, home = boot.home, preferences = PreferencesSnapshot())
            scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
                val context = LocalPlatformContext.current
                remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
                App(browser = true, providedController = controller, manageControllerLifecycle = false)
                appLoader = SingletonImageLoader.get(context)
            }
        }

        val continueCard get() = controller.state.value.home!!.rows.single { it.id == "continue" }.cards.single()
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what: " + controller.state.value.let { state ->
                "${state.screen} busy=${state.busy} error=${state.error} episodes=${state.detail?.episodes?.size} requests=${requested()} " +
                    nodes().map(::text).filter { it.isNotBlank() }.take(40) } }; frames(2) }
        }
        /** Bounds in dp, whatever the capture density. */
        fun bounds(node: SemanticsNode) = node.boundsInRoot.let { Rect(it.left / density, it.top / density, it.right / density, it.bottom / density) }
        fun pane(name: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == name }
        /** The Details dialog's episode row for [episode], merged with its number and description. */
        fun episodeRow(episode: String) = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == episode } }
        /** Where each composed Details episode row sits: the dialog's season and scroll position. */
        fun episodeTops() = all.filter { it.first == 2L }.mapNotNull { (s, e) -> episodeRow("Episode $s-$e")?.let { "Episode $s-$e" to bounds(it).top } }.toMap()
        fun focused() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); frames(2)
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)); frames()
        }
        fun click(x: Float, y: Float) {
            val at = Offset(x * density, y * density)
            scene.sendPointerEvent(PointerEventType.Move, at); scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at); frames()
        }
        fun save(name: String) {
            val dir = captureDir ?: return
            frames(40); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }

        /** Details open on S2 E8 (the Continue Watching episode), then the viewer scrolls the dialog a little. */
        fun openDetailsAndScroll() {
            until("Home") { nodes().any { text(it).contains("Continue Watching") } }
            controller.open(continueCard)
            until("season 2's episodes in the Details dialog") { episodeTops().isNotEmpty() && !controller.state.value.busy }
            frames(20)
            // Into the middle of season 2, whether or not the dialog already landed on S2 E8 (app#40).
            until("the viewer's scroll to S2 E10") {
                episodeRow("Episode 2-1") == null && episodeRow("Episode 2-10") != null ||
                    run { scene.sendPointerEvent(PointerEventType.Scroll, Offset(500f * density, 600f * density), scrollDelta = Offset(0f, 1f)); false }
            }
            frames(20)
        }

        /** The viewer picks S2 E10 in the dialog; its sources finish loading in the drawer. */
        fun chooseEpisode() {
            val row = assertNotNull(nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) && text(it).contains("Episode 2-10") },
                "S2 E10 is clickable in the dialog: " + nodes().map(::text).filter { it.isNotBlank() })
            assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke())
            until("the episode's sources") { controller.state.value.let { it.screen == Screen.Sources && !it.findingSources && it.sources.size == 4 } }
            frames(20)
        }

        override fun close() {
            scene.close(); controller.job?.cancel(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private fun BrowserApp.assertDetailsUnderTheDrawer(before: Map<String, Float>) {
        val dialog = assertNotNull(pane(title), "the Details dialog stays composed under the drawer")
        val window = Rect(0f, 0f, width.toFloat(), height.toFloat())
        assertTrue(bounds(dialog).overlaps(window) && bounds(dialog).width > 600f, "the dialog is on screen: ${bounds(dialog)}")
        assertEquals(before, episodeTops(), "the dialog keeps its season and scroll position under the drawer")
    }

    private fun BrowserApp.assertDetailsUnchanged(before: Map<String, Float>, metaBefore: List<String>) {
        assertEquals(Screen.Details, controller.state.value.screen, "closing the drawer returns to Details")
        assertNull(pane("Choose a source"), "the drawer is gone")
        assertNotNull(pane(title), "the Details dialog is still open")
        assertEquals(before, episodeTops(), "same season, same scroll position")
        assertTrue(episodeTops().keys.none { it.startsWith("Episode 1-") })
        assertEquals(metaBefore, requested().filter { it.startsWith("meta") }, "no Details refetch when the drawer opens or closes")
    }

    @Test fun theDrawerOpensOverTheDetailsDialogAndEscapeReturnsToItUnchanged() {
        BrowserApp().use { app ->
            app.openDetailsAndScroll()
            val before = app.episodeTops()
            assertTrue(before.isNotEmpty() && "Episode 2-1" !in before, "the viewer scrolled into season 2: $before")
            val metaBefore = requested().filter { it.startsWith("meta") }
            app.save("app-details-before-drawer")

            app.chooseEpisode()
            assertEquals(Screen.Details, app.controller.state.value.sourceReturnScreen)
            app.assertDetailsUnderTheDrawer(before)
            val drawer = assertNotNull(app.pane("Choose a source"), "the drawer is open")
            assertTrue(app.bounds(drawer).width >= 640f, "at 1440x900 the drawer is at least 640 dp wide, not ${app.bounds(drawer).width}")
            assertEquals(1440f, app.bounds(drawer).right, "the drawer is docked to the right edge")
            assertTrue(app.nodes().none { text(it).contains("Play always starts") }, "the best-match hint is gone")
            val first = assertNotNull(app.focused(), "focus starts in the drawer")
            assertEquals("Close", text(first), "on the drawer's Close button")
            assertTrue(app.bounds(drawer).contains(app.bounds(first).center))
            // Keyboard focus walks the drawer only: the dialog under it is out of reach.
            repeat(12) {
                app.key(Key.Tab)
                val now = assertNotNull(app.focused(), "Tab focuses something")
                assertTrue(app.bounds(drawer).contains(app.bounds(now).center), "Tab stays in the drawer, not the dialog under it: ${text(now)} ${app.bounds(now)}")
            }
            app.save("app-drawer-over-details")

            app.key(Key.Escape)
            app.assertDetailsUnchanged(before, metaBefore)
            app.frames(20)
            assertEquals(Screen.Details, app.controller.state.value.screen, "one Escape closes only the drawer")
        }
    }

    @Test fun escapeStraightAfterOpeningClosesOnlyTheDrawer() {
        BrowserApp().use { app ->
            app.openDetailsAndScroll()
            val before = app.episodeTops()
            val metaBefore = requested().filter { it.startsWith("meta") }
            app.chooseEpisode()
            app.key(Key.Escape)
            app.assertDetailsUnchanged(before, metaBefore)
            app.frames(20)
            assertEquals(Screen.Details, app.controller.state.value.screen, "the Details dialog did not take the same Escape")
        }
    }

    @Test fun aClickOnTheScrimClosesTheDrawerWithoutReachingTheDialogUnderIt() {
        BrowserApp().use { app ->
            app.openDetailsAndScroll()
            val before = app.episodeTops()
            val metaBefore = requested().filter { it.startsWith("meta") }
            app.chooseEpisode()
            val streams = requested().count { it == "streams" }
            // Over the dialog's episode list, left of the drawer: without the scrim this picks another episode.
            val under = assertNotNull(app.episodeRow("Episode 2-12") ?: app.episodeRow("Episode 2-11"), "an episode row is under the scrim")
            val point = app.bounds(under).center.copy(x = 400f)
            assertTrue(point.x < app.bounds(app.pane("Choose a source")!!).left)
            app.click(point.x, point.y)
            app.assertDetailsUnchanged(before, metaBefore)
            assertEquals(streams, requested().count { it == "streams" }, "the click never reached the episode under the scrim")
        }
    }

    @Test fun theCloseButtonReturnsToTheDialogAndItKeepsWorking() {
        BrowserApp().use { app ->
            app.openDetailsAndScroll()
            val before = app.episodeTops()
            val metaBefore = requested().filter { it.startsWith("meta") }
            app.chooseEpisode()
            val close = app.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Close") &&
                app.bounds(it).left >= app.bounds(app.pane("Choose a source")!!).left }
            assertTrue(close.config[SemanticsActions.OnClick].action!!.invoke())
            app.frames(20)
            app.assertDetailsUnchanged(before, metaBefore)
            // The dialog under the drawer still answers: another episode opens the drawer again.
            app.chooseEpisode()
            app.assertDetailsUnderTheDrawer(before)
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
}
