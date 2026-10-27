package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
import kotlin.test.*

/**
 * Back from the source selector after a Continue Watching tap lands on the title's
 * Details on the continued episode's season, scrolled to that episode (app#40).
 *
 * The fixtures mirror the KMP dev host's real responses for a Stremio-imported
 * profile with Cinemeta (as in [ContinueWatchingPlayerEpisodesTest]): the Continue
 * Watching card is a `library` card with no metadata origin, and its episode is past
 * the first Details page, which holds the specials and most of season 1.
 */
@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class DetailsResumeSeasonTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt13293588"
    /** Cinemeta's season index for the show: specials, then seasons 1-3. */
    private val seasons = listOf(0L to 4L, 1L to 23L, 2L to 24L, 3L to 14L)
    private val origin = """{"connection_id":"addon:21","incarnation":"${"c".repeat(32)}","provider_id":"stremio"}"""
    private val parentIdentity = """{"edition":null,"episode":null,"id":"$show","ids":[{"namespace":"imdb","value":"$show"},""" +
        """{"namespace":"tmdb","value":"94664"},{"namespace":"tvdb","value":"371310"}],"kind":"series"}"""
    private fun episodeIdentity(season: Long, episode: Long) =
        """{"edition":null,"episode":{"episode":$episode,"order":"aired","season":$season},"id":"$show:$season:$episode","ids":[],"kind":"series"}"""
    private val all = seasons.flatMap { (season, count) -> (1L..count).map { season to it } }

    /** A Cinemeta episode card exactly as `/api/meta` returns it: no progress of its own. */
    private fun metaEpisode(season: Long, episode: Long) = """{"badges":[],"category":"series","description":null,
        "identity":${episodeIdentity(season, episode)},"landscape":null,"logo":null,"metadata_origin":$origin,
        "parent_identity":$parentIdentity,"poster":null,"progress":null,"provider_id":"addon:21","subtitle":null,
        "title":"Episode $season-$episode"}"""

    /** `/api/meta/series/<show>` paged like the host: a window over every episode, the season index on each page. */
    private fun metaPage(offset: Int, limit: Int): String {
        val window = all.drop(offset).take(limit)
        val next = (offset + window.size).takeIf { it < all.size }?.let { "\"$it\"" } ?: "null"
        var start = 0L
        val index = seasons.joinToString(",") { (season, count) ->
            """{"episode_count":$count,"offset":$start,"season":$season}""".also { start += count }
        }
        return """{"item":{"badges":[],"category":"series","identity":$parentIdentity,"metadata_origin":$origin,"parent_identity":null,
            "provider_id":"addon:21","subtitle":"2021–","title":"Mushoku Tensei: Jobless Reincarnation"},
            "episodes":[${window.joinToString(",") { (s, e) -> metaEpisode(s, e) }}],"next_cursor":$next,"recommendations":[],"seasons":[$index]}"""
    }

    /** A Continue Watching row card from `/api/bootstrap` for a Stremio-imported resume point. */
    private fun continueCard(season: Long, episode: Long) = """{"badges":[],"description":null,"identity":${episodeIdentity(season, episode)},
        "landscape":null,"logo":null,"parent_identity":{"edition":null,"episode":null,"id":"$show","ids":[],"kind":"series"},
        "poster":null,"progress":0.42,"provider_id":"library",
        "subtitle":"S$season E$episode · 14m left","title":"Mushoku Tensei: Jobless Reincarnation"}"""

    private fun bootstrap(resume: Pair<Long, Long>) = """{"contract_version":3,
        "identity":{"account_id":"1","profile_id":"2","profiles":[{"id":"2","name":"Stremio import QA"}]},
        "home":{"complete":true,"featured":[],"hero":null,"revision":1818,
            "scope":{"account_id":"1","connection_id":"server","profile_id":"2","revision":0},
            "rows":[{"browse":{"query":[],"target":"Continue"},"cards":[${continueCard(resume.first, resume.second)}],"id":"continue",
                "message":null,"next_cursor":null,"provider_id":"library","state":"Ready","title":"Continue Watching"}]}}"""

    private val metaOffsets = mutableListOf<Int>()

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    private fun MockRequestHandleScope.host(request: HttpRequestData, bootstrapJson: String) = when (request.url.encodedPath) {
        "/api/bootstrap" -> json(bootstrapJson)
        "/api/meta/series/$show" -> {
            val offset = request.url.parameters["offset"]?.toInt() ?: 0
            synchronized(metaOffsets) { metaOffsets += offset }
            json(metaPage(offset, request.url.parameters["limit"]?.toInt() ?: 24))
        }
        // The QA profile has no stream add-ons: discovery finishes with nothing.
        "/api/streams" -> json("""{"id":"job-1"}""")
        "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
        else -> if (request.url.encodedPath.endsWith("/source-ranking")) json("""{"streams":[]}""")
            else respond("{\"error\":\"not in this fixture\"}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    /** The real phone App shell over the fake host, signed into the QA profile with its Continue Watching row. */
    private inner class PhoneApp(resume: Pair<Long, Long>, val capture: String? = null) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request -> host(request, bootstrap(resume)) }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        private val captureDir = System.getenv("VIPTV_DETAILS_RESUME_CAPTURE_DIR")?.let(::File)
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
            scene = ImageComposeScene((390 * density).toInt(), (844 * density).toInt(), Density(density)) {
                val context = LocalPlatformContext.current
                remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
                App(providedController = controller, manageControllerLifecycle = false)
                appLoader = SingletonImageLoader.get(context)
            }
        }

        val continueCard get() = controller.state.value.home!!.rows.single { it.id == "continue" }.cards.single()
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }
        fun node(label: String) = nodes().firstOrNull { text(it) == label }
        fun save(name: String) {
            val dir = captureDir ?: return
            frames(30); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        /** Tap the Continue Watching card (Sources open), let the title's Details land behind it, then press Back. */
        fun continueThenBack() {
            controller.playTitle(continueCard)
            until("Details behind the selector") { controller.state.value.let { it.screen == Screen.Sources && it.detail != null && !it.findingSources } }
            frames()
            controller.backSources()
            frames()
            assertEquals(Screen.Details, controller.state.value.screen)
        }
        override fun close() {
            scene.close(); controller.job?.cancel(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    /** The episode row's top edge sits just under the phone's back bar: it is the first thing in the list. */
    private fun PhoneApp.assertEpisodeAtTop(label: String) {
        val episode = assertNotNull(node(label), "$label is composed: ${nodes().map(::text).filter { it.isNotBlank() }}")
        val top = episode.boundsInRoot.top / density
        assertTrue(top in 48f..72f, "$label is the list's first item below the 48 dp back bar, not at ${top}dp")
    }

    @Test fun backFromContinueWatchingSourcesShowsTheEpisodesLaterSeasonScrolledToIt() {
        PhoneApp(2L to 8L).use { app ->
            app.until("Home") { app.nodes().any { text(it).contains("Continue Watching") } }
            app.continueThenBack()
            app.until("season 2's episodes") { app.node("8. Episode 2-8") != null }
            app.frames(20)
            assertEquals(1, app.controller.state.value.detail!!.episodes.count { it.identity.episode?.season == 2L && it.identity.episode?.episode == 8L },
                "S2 E8 came in through the Details pages")
            assertTrue(app.nodes().none { text(it).startsWith("Episode 1-") || text(it).contains(". Episode 1-") },
                "no season 1 episode is on screen: the list shows season 2")
            app.assertEpisodeAtTop("8. Episode 2-8")
            assertTrue(synchronized(metaOffsets) { metaOffsets.toList() }.contains(27),
                "season 2 (offset 27: 4 specials + 23 episodes) came through Details paging: $metaOffsets")
            app.save("phone-details-resume-season")
        }
    }

    @Test fun backFromThePlayerKeepsDetailsOnTheContinuedEpisode() {
        PhoneApp(2L to 8L).use { app ->
            app.until("Home") { app.nodes().any { text(it).contains("Continue Watching") } }
            app.continueThenBack()
            app.until("season 2's episodes") { app.node("8. Episode 2-8") != null }
            app.frames(20)
            // Sources -> player -> Back -> Back: Details leave composition while the player shows.
            app.controller.mutable.value = app.controller.state.value.copy(screen = Screen.Player)
            app.frames()
            app.controller.mutable.value = app.controller.state.value.copy(screen = Screen.Sources)
            app.frames()
            app.controller.backSources()
            app.frames(20)
            assertEquals(Screen.Details, app.controller.state.value.screen)
            app.assertEpisodeAtTop("8. Episode 2-8")
        }
    }

    @Test fun detailsOpenedStraightFromAContinueWatchingCardLandOnTheEpisode() {
        PhoneApp(2L to 8L).use { app ->
            app.until("Home") { app.nodes().any { text(it).contains("Continue Watching") } }
            // Desktop's preview Info opens Details for the Continue Watching card itself.
            app.controller.open(app.continueCard)
            app.until("season 2's episodes") { app.node("8. Episode 2-8") != null }
            app.frames(20)
            app.assertEpisodeAtTop("8. Episode 2-8")
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
