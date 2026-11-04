package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
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
import org.viptv.app.components.AddonFailureNotice
import org.viptv.app.components.addonFailureMessage
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.AddonFailure
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AddonFailureNoticeTest {
    @get:Rule val uiThread = UiThreadRule()

    private val captureDir = File("/tmp/viptv-addon-failure").apply { mkdirs() }

    private fun failure(id: Long = 21L, name: String = "Torrentio", code: String = "addon_timeout") =
        """{"addon_id":$id,"addon_name":"$name","error_code":"$code"}"""

    private fun card(n: Int) =
        """{"identity":{"id":"browse-$n","kind":"movie","ids":[]},"provider_id":"addon:1","title":"Browse $n","badges":[]}"""

    private inner class Shell(private val television: Boolean) : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/discovery-controls" -> DISCOVERY_CONTROLS
                "/api/discover" -> """{"cards":[${(1..9).joinToString(",") { card(it) }}],"complete":true,"failures":[${failure()}]}"""
                else -> if (request.url.encodedPath.startsWith("/api/meta/"))
                    """{"item":${card(1)},"episodes":[],"failures":[${failure()}]}"""
                else """{}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        private val size = if (television) 1920 to 1080 else 390 to 844
        private val scene = ImageComposeScene(size.first, size.second, Density(1f)) {
            App(television = television, providedController = controller, manageControllerLifecycle = false)
        }
        private var frame = 0L

        init {
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Home,
                profiles = listOf(Profile("12", "Parent", null)), selectedProfile = "12")
        }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun texts() = nodes().map(::text)
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what (screen=${controller.state.value.screen})" }
                frames(2)
            }
        }
        fun capture(name: String) = scene.render(frame++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(captureDir, "$name.png").writeBytes(it.bytes) }
        }
        fun openDetails() {
            val card = controller.state.value.browseCards.first()
            controller.open(card)
            until("Details loads") {
                controller.state.value.screen == Screen.Details &&
                    controller.state.value.detail?.failures?.isNotEmpty() == true && !controller.state.value.busy
            }
            frames(10)
        }

        override fun close() {
            scene.close(); controller.disconnect(); scope.cancel(); remote.close()
        }
    }

    @Test fun phoneShowsNoticesAboveTheGridAndNearMetadata() {
        Shell(television = false).use { app ->
            app.frames()
            app.controller.navigate(Screen.Discover)
            app.until("Browse loads") {
                app.controller.state.value.browseCards.isNotEmpty() &&
                    app.controller.state.value.browseFailures.isNotEmpty() && !app.controller.state.value.busy
            }
            assertTrue(app.texts().any { "Torrentio didn’t respond" in it }, "the browse notice — ${app.texts()}")
            assertTrue(app.texts().any { "Manage add-ons" in it }, "the browse notice offers Manage add-ons")
            app.capture("browse-390")

            app.openDetails()
            assertTrue(app.texts().any { "Torrentio didn’t respond" in it }, "the details notice — ${app.texts()}")
            assertTrue(app.texts().any { "Manage add-ons" in it }, "the details notice offers Manage add-ons")
            app.capture("details-390")
        }
    }

    @Test fun tvShowsNoticesAboveTheGridAndNearMetadata() {
        Shell(television = true).use { app ->
            app.frames()
            app.controller.navigate(Screen.Discover)
            app.until("Browse loads") {
                app.controller.state.value.browseCards.isNotEmpty() &&
                    app.controller.state.value.browseFailures.isNotEmpty() && !app.controller.state.value.busy
            }
            app.frames(10)
            assertTrue(app.texts().any { "Torrentio didn’t respond" in it }, "the TV browse notice — ${app.texts()}")
            assertTrue(app.texts().any { "Manage add-ons" in it }, "the TV browse notice offers Manage add-ons")
            app.capture("browse-tv")

            app.openDetails()
            assertTrue(app.texts().any { "Torrentio didn’t respond" in it }, "the TV details notice — ${app.texts()}")
            app.capture("details-tv")
        }
    }

    @Test fun aNoticeShowsTheMessageAndFiresManageAddons() {
        var managed = 0
        val failures = listOf(AddonFailure(21, "Torrentio", "addon_timeout"))
        val phone = page(390, 844, television = false) {
            AddonFailureNotice(failures, manageAddons = { managed++ }, television = false)
        }
        try {
            frames(phone)
            assertTrue(texts(phone).any { "Torrentio didn’t respond" in it }, "the message is drawn")
            click(phone, "Manage add-ons")
            assertEquals(1, managed, "Manage add-ons fires the shared action")
        } finally { phone.close() }
        val tv = page(1920, 1080, television = true) {
            AddonFailureNotice(failures, manageAddons = { managed++ }, television = true)
        }
        try {
            frames(tv)
            assertTrue(texts(tv).any { "Torrentio didn’t respond" in it }, "the TV message is drawn")
            click(tv, "Manage add-ons")
            assertEquals(2, managed, "the TV Manage add-ons fires the shared action")
        } finally { tv.close() }
    }

    @Test fun theMessageJoinsFailedAddonNames() {
        val torrentio = AddonFailure(21L, "Torrentio", "addon_timeout")
        val debrid = AddonFailure(22L, "AllDebrid", "addon_unavailable")
        val cine = AddonFailure(23L, "Cinemeta", "addon_access_denied")
        assertEquals("Torrentio didn’t respond", addonFailureMessage(listOf(torrentio)))
        assertEquals("Torrentio and AllDebrid didn’t respond", addonFailureMessage(listOf(torrentio, debrid)))
        assertEquals("Torrentio and 2 more didn’t respond", addonFailureMessage(listOf(torrentio, debrid, cine)))
        assertEquals("An add-on didn’t respond", addonFailureMessage(listOf(AddonFailure(24L, "", "addon_timeout"))))
    }

    private fun page(width: Int, height: Int, television: Boolean, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television, pointer = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 30) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private fun texts(scene: ImageComposeScene): List<String> = nodes(scene).map(::label)

    private fun click(scene: ImageComposeScene, text: String) {
        val node = nodes(scene).first {
            it.config.contains(SemanticsActions.OnClick) &&
                (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
        }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "$text is clickable")
        frames(scene)
    }

    private companion object {
        val DISCOVERY_CONTROLS =
            """{"provider_id":"stremio","media_types":["movie"],
                "controls":[{"id":"genre","label":"Genre","kind":"SingleChoice","required":false,
                    "choices":[{"value":"Comedy","label":"Comedy"},{"value":"Drama","label":"Drama"}]}],
                "catalogs":[],"discovery":{"availability":"Available"},"search":{"availability":"Available"},
                "filters":{"availability":"Available"},"calendar":{"availability":"Unsupported"}}"""
    }
}