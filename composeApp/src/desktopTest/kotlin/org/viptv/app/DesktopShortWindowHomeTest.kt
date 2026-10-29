@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.viptv.app.connection.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import kotlin.test.*

class DesktopShortWindowHomeTest {
    @get:Rule val uiThread = UiThreadRule()

    private val windowWidth = 800
    private val windowHeight = 600
    private val profile = Profile("12", "Parent", null)
    private val home = PreviewData.home
    private val revisions = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0",
        "preferences" to "1", "profiles" to "7")

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun bootstrap() = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(home)); put("integrations", JsonArray(emptyList()))
        put("revisions", JsonObject(revisions.mapValues { (_, value) -> JsonPrimitive(value) }))
    }.toString()

    private fun text(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private inner class Shell(val scene: ImageComposeScene) {
        private var time = 0L
        fun frames(count: Int = 40) = repeat(count) { scene.render(time).close(); time += 16_666_667L }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged = false) }
        fun title(label: String) = nodes().firstOrNull { text(it) == label }
        fun titles() = nodes().map { text(it) }.filter(String::isNotBlank)
        fun wheel(at: Offset, notches: Int = 1) {
            scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse, timeMillis = time / 1_000_000)
            repeat(notches) {
                scene.sendPointerEvent(PointerEventType.Scroll, at, type = PointerType.Mouse,
                    scrollDelta = Offset(0f, 1f), timeMillis = time / 1_000_000)
                frames(2)
            }
            frames(30)
        }
    }

    private fun shell(test: suspend (Shell) -> Unit) = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/profiles/12/revisions" -> JsonObject(revisions.mapValues { (_, value) -> JsonPrimitive(value) }).toString()
                "/api/bootstrap" -> bootstrap()
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, profiles = listOf(profile),
            selectedProfile = profile.id, home = home)
        controller.freshness.homeBuilt(revisions)
        val scene = ImageComposeScene(windowWidth, windowHeight, Density(1f)) {
            DesktopShell(controller, GlobalBack(controller), mac = false,
                DesktopWindowControls(minimize = {}, maximize = {}, close = {}), manageControllerLifecycle = false)
        }
        try { Shell(scene).apply { frames() }.let { test(it) } }
        finally { scene.close(); controller.disconnect(); scope.cancel(); remote.close() }
    }

    @Test fun theFirstRailTitleAndACardAreOnScreenInAShortWindow() = shell { shell ->
        val rail = assertNotNull(shell.title("Continue Watching"), "the first rail has a title: " + shell.titles())
        assertTrue(rail.boundsInRoot.top < windowHeight - 40, "the rail title sits above the fold: ${rail.boundsInRoot}")
        assertTrue(rail.boundsInRoot.bottom <= windowHeight, "the rail title is inside the window: ${rail.boundsInRoot}")
        val card = shell.nodes().filter { text(it) == PreviewData.watching.first().title }.map { it.boundsInRoot }
        assertTrue(card.isNotEmpty(), "the first rail's first card is composed: " + shell.titles())
        assertTrue(card.minOf { it.top } < windowHeight, "a card's top edge is on screen: $card")
    }

    @Test fun theWheelScrollsHomeAndMountsTheNextRail() = shell { shell ->
        val rail = assertNotNull(shell.title("Continue Watching"), "the first rail has a title: " + shell.titles())
        assertTrue(shell.nodes().any { text(it) == "Featured" }, "the fold starts on the hero: " + shell.titles())
        shell.wheel(Offset(400f, 300f))
        val moved = assertNotNull(shell.title("Continue Watching"), "the first rail is still composed")
        assertTrue(moved.boundsInRoot.top < rail.boundsInRoot.top, "the wheel scrolls Home up: ${moved.boundsInRoot.top} vs ${rail.boundsInRoot.top}")

        var notches = 1
        while (shell.title("New this week")?.boundsInRoot?.top?.let { it >= 0f && it < windowHeight } != true && notches < 12) {
            shell.wheel(Offset(400f, 300f)); notches++
        }
        val next = assertNotNull(shell.title("New this week"), "the wheel reaches the next rail after $notches notches: " + shell.titles())
        assertTrue(next.boundsInRoot.top < windowHeight, "the next rail's title is on screen: ${next.boundsInRoot}")
    }
}
