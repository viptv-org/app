package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import org.viptv.engine.wire.CatalogPage
import java.io.File
import kotlin.test.*

/**
 * The whole phone flow in the real App shell, against a fake server: Home's Genres chip lists
 * the server's genres, choosing one opens Browse filtered to it, and Back returns to Home.
 * With VIPTV_GENRES_CAPTURE_DIR set it also writes the PR screenshots.
 */
@OptIn(ExperimentalComposeUiApi::class)
class HomeGenresAppUiTest {
    @Test fun genresChipOpensFilteredBrowseInThePhoneApp() = runBlocking {
        val discover = mutableListOf<Url>()
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/discovery-controls" -> CONTROLS
                "/api/discover" -> { synchronized(discover) { discover += request.url }
                    Json.encodeToString(CatalogPage.serializer(), CatalogPage(PreviewData.posters.take(6), complete = true)) }
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
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = PreviewData.profile.id,
            profiles = PreviewData.profiles, home = PreviewData.home)
        val capture = System.getenv("VIPTV_GENRES_CAPTURE_DIR")?.let(::File)
        val density = if (capture != null) 2f else 1f
        val scene = ImageComposeScene((390 * density).toInt(), (844 * density).toInt(), Density(density)) {
            App(providedController = controller, manageControllerLifecycle = false)
        }
        var frame = 0L
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }
        fun click(label: String) {
            val node = nodes().first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); frames(30)
        }
        fun save(name: String) {
            val dir = capture ?: return
            frames(30); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        try {
            until("the Genres chip") { nodes().any { text(it) == "Genres" } }
            assertTrue(nodes().none { description(it).any { label -> label in listOf("Cast", "Remote control") } })
            save("home-header")
            click("Genres")
            assertTrue(nodes().any { text(it) == "Science Fiction" })
            save("genre-picker")
            click("Comedy")
            until("filtered Browse") { controller.state.value.let { it.screen == Screen.Discover && !it.busy && it.browseCards.isNotEmpty() } }
            val state = controller.state.value
            assertEquals(BrowseQuery("movie", "top", "addon:1", genre = "Comedy"), state.browseQuery)
            assertTrue(synchronized(discover) { discover.isNotEmpty() && discover.all { it.parameters["genre"] == "Comedy" } })
            until("the Browse grid") { nodes().any { text(it).contains(PreviewData.posters.first().title) || description(it).any { d -> d.contains(PreviewData.posters.first().title) } } }
            save("browse-comedy")
            controller.navigateBack()
            frames()
            assertEquals(Screen.Home, controller.state.value.screen)
        } finally { scene.close(); scope.cancel(); remote.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private companion object {
        val CONTROLS = """{"provider_id":"stremio","media_types":["movie","series"],"controls":[],
            "catalogs":[{"id":"top","name":"Popular","type":"movie","connection_id":"addon:1","addon_id":1,"connection_name":"Cinemeta",
              "controls":[{"id":"genre","label":"Genre","kind":"MultiChoice","required":false,"maximum_selections":1,"choices":[
                {"value":"Action","label":"Action"},{"value":"Adventure","label":"Adventure"},{"value":"Animation","label":"Animation"},
                {"value":"Comedy","label":"Comedy"},{"value":"Crime","label":"Crime"},{"value":"Drama","label":"Drama"},
                {"value":"Horror","label":"Horror"},{"value":"Science Fiction","label":"Science Fiction"},{"value":"Thriller","label":"Thriller"}]}]}],
            "discovery":{"availability":"Available"},"search":{"availability":"Available"},"filters":{"availability":"Available"},"calendar":{"availability":"Unsupported"}}"""
    }
}
