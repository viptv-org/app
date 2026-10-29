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
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class BestMatchesAppUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun phonePickerGroupsRowsWhileDiscoveryRuns() = picker(television = false, width = 390, height = 844, name = "phone")
    @Test fun tvPickerGroupsRowsWhileDiscoveryRuns() = picker(television = true, width = 1920, height = 1080, name = "tv")

    private fun picker(television: Boolean, width: Int, height: Int, name: String) = runBlocking {
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path == "/api/streams" -> """{"id":"job"}"""
                path.endsWith("/source-ranking") -> rank((request.body as TextContent).text)
                path == "/api/streams/job" && request.url.parameters["after"] == "0" -> batch(FIRST, done = false)
                path == "/api/streams/job" -> { release.await(); batch(SECOND, done = true) }
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
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = PreviewData.profile.id,
            profiles = PreviewData.profiles, home = PreviewData.home, detail = PreviewData.detail, preferences = PreferencesSnapshot())
        val capture = System.getenv("VIPTV_BEST_MATCHES_CAPTURE_DIR")?.let(::File)
        val density = if (capture != null && !television) 2f else 1f
        var previousLoader: ImageLoader? = null
        var appLoader: ImageLoader? = null
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(television = television, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }
        var frame = 0L
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }
        fun headings() = nodes().filter { it.config.contains(SemanticsProperties.Heading) }.map(::text)
        fun save(variant: String) {
            val dir = capture ?: return
            frames(40); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name-$variant.png").writeBytes(it.bytes) } }
        }
        try {
            controller.chooseSource(PreviewData.detail.item)
            until("the first ranked rows") { controller.state.value.sources.size == FIRST.size }
            until("the Best matches header") { "BEST MATCHES" in headings() }
            assertTrue(controller.state.value.findingSources)
            assertEquals(listOf("BEST MATCHES", "ALL SOURCES"), headings(), "The camera row sits under All sources")
            assertEquals(listOf("b1080", "a720"), controller.state.value.sourceGroups.bestMatches)
            save("during")
            release.complete(Unit)
            until("discovery to finish") { !controller.state.value.findingSources && controller.state.value.sources.size == FIRST.size + SECOND.size }
            frames(20)
            assertEquals(listOf("k2160", "b1080", "h1080", "s720", "a720"), controller.state.value.sourceGroups.bestMatches)
            assertEquals("k2160", controller.state.value.sourceGroups.bestMatchId)
            assertEquals("BEST MATCHES", headings().first())
            assertTrue(nodes().any { text(it).contains("Best match") && text(it).contains("Source k2160") },
                "The marker moved to the better row: " + nodes().map(::text).filter { it.contains("k2160") || it.contains("Best match") })
            save("after")
        } finally {
            release.complete(Unit)
            scene.close(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private data class Stream(val id: String, val height: Long, val releaseType: String, val provider: String, val detail: String)

    private companion object {
        val FIRST = listOf(
            Stream("a720", 720, "webrip", "addon:torrentio", "Oak.Street.2026.720p.WEBRip.x264"),
            Stream("cam", 1080, "cam", "addon:torrentio", "Oak.Street.2026.HDCAM.1080p"),
            Stream("b1080", 1080, "web_dl", "addon:comet", "Oak.Street.2026.1080p.WEB-DL.DDP5.1.H.264"))
        val SECOND = listOf(
            Stream("k2160", 2160, "web_dl", "addon:comet", "Oak.Street.2026.2160p.WEB-DL.DV.HDR10.HEVC"),
            Stream("h1080", 1080, "bluray", "addon:torrentio", "Oak.Street.2026.1080p.BluRay.x265.10bit"),
            Stream("s720", 720, "web_dl", "addon:mediafusion", "Oak.Street.2026.720p.WEB-DL.AAC2.0"),
            Stream("d480", 480, "dvd", "addon:mediafusion", "Oak.Street.2026.DVDRip.480p"))
        val ALL = (FIRST + SECOND).associateBy { it.id }
        val RELEASE_ORDER = listOf("web_dl", "bluray", "webrip", "dvd")

        fun card(stream: Stream) = buildJsonObject {
            put("id", stream.id); put("name", "Source ${stream.id}"); put("description", stream.detail)
            put("quality_height", stream.height); put("release_type", stream.releaseType); put("delivery", "direct")
        }

        fun batch(streams: List<Stream>, done: Boolean) = buildJsonObject {
            put("events", buildJsonArray { streams.groupBy { it.provider }.forEach { (provider, rows) ->
                add(buildJsonObject { put("source", provider); put("streams", JsonArray(rows.map(::card))) }) } })
            put("done", done)
        }.toString()

        fun rank(request: String): String {
            val ids = Json.parseToJsonElement(request).jsonObject.getValue("ids").jsonArray.map { it.jsonPrimitive.content }
            val ranked = ids.map { ALL.getValue(it) }.sortedWith(compareBy<Stream> { it.releaseType == "cam" }
                .thenByDescending { it.height }.thenBy { RELEASE_ORDER.indexOf(it.releaseType) })
            return buildJsonObject {
                put("streams", JsonArray(ranked.map(::card)))
                put("best_match_id", ranked.first().id)
            }.toString()
        }
    }
}
