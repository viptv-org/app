package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
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
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.home.CONTINUE_ROW
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import org.junit.Rule
import java.io.File
import java.time.Instant
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class, InternalComposeUiApi::class)
class CaughtUpNextEpisodeTest {
    @get:Rule val uiThread = UiThreadRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val scope = Scope("viptv:remote", "account", "1", 1)
    private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
    private val airdate = Instant.parse("2027-11-04T12:00:00Z").toEpochMilli()

    private fun show(id: String, title: String) = Card(MediaIdentity(id, "series", listOf(ExternalId("imdb", id))),
        provider_id = "addon:4", title = title, badges = emptyList(), metadata_origin = origin)
    private fun episode(series: Card, season: Long, number: Long) = series.copy(
        identity = MediaIdentity("${series.identity.id}:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = series.title)
    private fun caughtUp(series: Card, season: Long, number: Long) = episode(series, season, number).copy(badges = listOf("Watched"))
    private fun next(series: Card, season: Long, number: Long, releasedAt: Long? = null) =
        NextEpisode(identity = episode(series, season, number).identity, released_at_millis = releasedAt)

    private fun entry(card: Card, next: NextEpisode?) = ContinuationEntry(card,
        PlaybackRecord(identity = card.identity, provider_id = card.provider_id, title = card.title,
            position_millis = 1_400_000, duration_millis = 1_420_000, updated_at_millis = 1_000, completed_at_millis = 1_000,
            parent_identity = card.parent_identity, metadata_origin = card.metadata_origin),
        ContinuationStatus.CAUGHTUP, next_episode = next)

    private val dated = show("tt10000001", "Dated Finale")
    private val undated = show("tt10000002", "Undated Finale")
    private val finished = show("tt10000003", "Finished Show")
    private val entries = listOf(
        entry(caughtUp(dated, 1, 1), next(dated, 1, 2, airdate)),
        entry(caughtUp(undated, 2, 1), next(undated, 2, 2)),
        entry(caughtUp(finished, 3, 1), null),
    )

    private val bootstrap = Json.encodeToString(buildJsonObject {
        put("contract_version", 3)
        putJsonObject("identity") {
            put("account_id", "1"); put("profile_id", "1")
            putJsonArray("profiles") { add(buildJsonObject { put("id", "1"); put("name", "Caught up QA") }) }
        }
        put("home", Json.encodeToJsonElement(HomeSnapshot(scope, 1,
            rows = listOf(HomeRow(browse = HomeBrowseAction(HomeBrowseTarget.CONTINUE), id = CONTINUE_ROW,
                title = "Continue Watching", provider_id = "library", state = RowState.READY,
                cards = entries.map { it.card })), complete = true)))
        put("continuation", Json.encodeToJsonElement(ContinuationPage(scope, 1, entries, complete = true)))
        put("library", buildJsonObject { put("cards", JsonArray(emptyList())); put("complete", true) })
        put("preferences", buildJsonObject {})
    })

    private val continuePage = Json.encodeToString(ContinuationPage(scope, 1, entries, complete = true))

    private fun MockRequestHandleScope.json(text: String) =
        respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    private fun MockRequestHandleScope.fakeHost(request: HttpRequestData) = when (request.url.encodedPath) {
        "/api/bootstrap" -> json(bootstrap)
        "/api/profiles/1/continue" -> json(continuePage)
        else -> respond("""{"error":"not in this fixture"}""", HttpStatusCode.NotFound,
            headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private inner class Shell(val television: Boolean) : AutoCloseable {
        private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request -> fakeHost(request) }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(jobs, store)
        private val captureDir = System.getenv("VIPTV_CAUGHT_UP_CAPTURE_DIR")?.let(::File)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        private var frame = 0L
        val scene: ImageComposeScene

        init {
            val boot = runBlocking { remote.bootstrap() }
            controller.connection = remote
            controller.accept(boot)
            val density = if (captureDir != null) 2f else 1f
            scene = ImageComposeScene(if (television) 1920 else (390 * density).toInt(),
                if (television) 1080 else (844 * density).toInt(), Density(if (television) 1f else density)) {
                val context = LocalPlatformContext.current
                remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
                App(television = television, providedController = controller, manageControllerLifecycle = false)
                appLoader = SingletonImageLoader.get(context)
            }
        }

        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun labels(): List<String> = nodes().mapNotNull { node ->
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        }.filter { it.isNotBlank() }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }
        fun save(name: String) {
            val dir = captureDir ?: return
            frames(30); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        override fun close() {
            scene.close(); controller.job?.cancel(); jobs.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private fun Shell.tvCard(title: String): List<String> = labels().filter { it.contains(title) }

    @Test fun theHomeRowNamesTheNextEpisodeFromTheBootstrapsContinuationPage() {
        Shell(television = false).use { app ->
            app.until("Home's Continue Watching row") { app.labels().any { it.contains("Dated Finale") } }
            val card = assertNotNull(app.labels().firstOrNull { it.contains("Dated Finale") },
                "the caught-up card is on Home's row: ${app.labels()}")
            assertTrue(card.contains("S1 E1 · Watched"), "the finished episode keeps its app#149 label: $card")
            assertTrue(card.contains("S1 E2 · Nov 4"), "the next episode and the day it airs: $card")
            app.save("phone-caught-up-next-episode")
        }
    }

    @Test fun thePageNamesTheDatedAndUndatedNextEpisodesAndLeavesTheRestAlone() {
        Shell(television = false).use { app ->
            app.until("Home") { app.labels().any { it.contains("Dated Finale") } }
            app.controller.navigate(Screen.ContinueWatching)
            app.until("the Continue Watching page") {
                app.controller.state.value.continueWatching?.cards?.size == 3 && !app.controller.state.value.busy
            }
            app.until("the page's cards") {
                val labels = app.labels()
                labels.count { it.contains("More actions") } == 3 && labels.count { it.contains("· Watched") } == 3
            }
            val labels = app.labels()
            fun card(title: String) = assertNotNull(labels.firstOrNull { it.contains(title) },
                "$title is on the page: $labels")
            val dated = card("Dated Finale")
            assertTrue(dated.contains("S1 E1 · Watched") && dated.contains("S1 E2 · Nov 4"),
                "the announced episode and its day follow the finished one: $dated")
            val undated = card("Undated Finale")
            assertTrue(undated.contains("S2 E1 · Watched") && undated.contains("S2 E2 coming soon"),
                "no date is known, so the card says so: $undated")
            val finished = card("Finished Show")
            assertTrue(finished.contains("S3 E1 · Watched"), "the finished episode keeps its app#149 label: $finished")
            assertFalse(finished.contains("coming soon") || finished.contains("Nov 4"),
                "nothing announced a next episode for this show: $finished")
            assertEquals(2, labels.count { it.contains("coming soon") || it.contains("· Nov 4") },
                "only the two announced cards name an episode they wait for: $labels")
            app.save("page-caught-up-next-episode")
        }
    }

    @Test fun theTvBoardNamesTheNextEpisodeWhereItDrawsTheCoordinates() {
        Shell(television = true).use { app ->
            app.until("TV Home's Continue Watching shelf") { app.labels().any { it.contains("Dated Finale") } }
            val dated = app.tvCard("Dated Finale")
            assertTrue(dated.any { it.contains("S1 E1") }, "TV keeps the board's coordinate caption: $dated")
            assertTrue(dated.any { it.contains("S1 E2 · Nov 4") }, "the next episode and its day: $dated")
            val undated = app.tvCard("Undated Finale")
            assertTrue(undated.any { it.contains("S2 E2 coming soon") }, "the next episode, undated: $undated")
            val finished = app.tvCard("Finished Show")
            assertTrue(finished.any { it.contains("S3 E1") }, "today's caption stays: $finished")
            assertFalse(finished.any { it.contains("coming soon") || it.contains("Nov 4") }, "nothing was announced: $finished")
            app.save("tv-caught-up-next-episode")
        }
    }
}
