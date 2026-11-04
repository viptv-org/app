package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.state.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class)
class BrowseBackRestoreRenderTest {
    @get:Rule val uiThread = UiThreadRule()

    private val captureDir = File("/tmp/viptv-back-restore").apply { mkdirs() }

    private fun card(n: Int) =
        """{"identity":{"id":"browse-$n","kind":"movie","ids":[]},"provider_id":"addon:1","title":"Browse $n","badges":[]}"""

    private inner class Shell : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path == "/api/discovery-controls" ->
                    """{"provider_id":"stremio","media_types":["movie"],
                        "controls":[{"id":"genre","label":"Genre","kind":"SingleChoice","required":false,
                            "choices":[{"value":"Comedy","label":"Comedy"},{"value":"Drama","label":"Drama"}]}],
                        "catalogs":[],"discovery":{"availability":"Available"},"search":{"availability":"Available"},
                        "filters":{"availability":"Available"},"calendar":{"availability":"Unsupported"}}"""
                path == "/api/discover" -> """{"cards":[${(1..12).joinToString(",") { card(it) }}],"complete":true}"""
                path.startsWith("/api/meta/") -> """{"item":${card(1)},"episodes":[]}"""
                else -> """{}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        private val scene = ImageComposeScene(390, 844, Density(1f)) {
            App(providedController = controller, manageControllerLifecycle = false)
        }
        private var frame = 0L

        init {
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, profiles = listOf(Profile("12", "Parent", null)),
                selectedProfile = "12")
        }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what (screen=${controller.state.value.screen})" }
                frames(2)
            }
        }

        fun scrollDown() {
            val scroll = nodes().filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller: ${nodes().map(::text)}")
            repeat(3) { scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f); frames(6) }
        }

        fun cardOnScreen(n: Int) = nodes().any {
            it.config.contains(SemanticsActions.OnClick) && "Browse $n" in text(it)
        }

        fun clickCard(n: Int) {
            val node = nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) && "Browse $n" in text(it) }
                ?: fail("no card Browse $n on screen: ${nodes().map(::text).filter { it.contains("Browse ") }}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), "Browse $n is clickable")
        }

        fun capture(name: String) = scene.render(frame++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(captureDir, "$name.png").writeBytes(it.bytes) }
        }

        override fun close() {
            scene.close()
            controller.disconnect()
            scope.cancel()
            remote.close()
        }
    }

    @Test fun browseFiltersAndScrollSurviveTheDetailsRoundTrip() = Shell().use { app ->
        app.frames()
        app.controller.navigate(Screen.Discover)
        app.until("Browse loads") { app.controller.state.value.browseCards.isNotEmpty() && !app.controller.state.value.busy }
        app.controller.browseFilter("genre", "Comedy")
        app.until("genre applies") { app.controller.state.value.browseQuery.genre == "Comedy" && !app.controller.state.value.busy }
        app.scrollDown()
        assertTrue(app.cardOnScreen(8), "scrolling reaches a later card: ${app.nodes().map(app::text).filter { it.contains("Browse ") }}")
        app.capture("browse-before-details")

        app.clickCard(8)
        app.until("Details opens") { app.controller.state.value.screen == Screen.Details }
        app.controller.navigateBack()
        app.until("Browse returns") { app.controller.state.value.screen == Screen.Discover && !app.controller.state.value.busy }

        assertEquals("Comedy", app.controller.state.value.browseQuery.genre, "the genre filter survives Back")
        assertTrue(app.cardOnScreen(8), "the scrolled card is still on screen after Back")
        app.capture("browse-after-back")
    }
}