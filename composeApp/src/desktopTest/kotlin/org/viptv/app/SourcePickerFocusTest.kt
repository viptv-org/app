@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, coil3.annotation.DelicateCoilApi::class)

package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.chooseSource
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertFalse
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class SourcePickerFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun pane(nodes: List<SemanticsNode>, name: String) =
        nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == name }

    @Test fun openingThePickerLogsNoFocusWarning() = app(browseSources = true)

    @Test fun openingTheDetailsDialogLogsNoFocusWarning() = app(browseSources = false)

    private fun app(browseSources: Boolean) = runBlocking {
        val releaseSources = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path == "/api/streams" -> """{"id":"job"}"""
                path == "/api/streams/job" -> { releaseSources.await(); """{"done":true,"events":[{"source":"addon:torrentio","streams":[{"id":"s1","name":"Torrentio 4K","quality_height":2160,"release_type":"web_dl","delivery":"direct"}]}]}""" }
                else -> return@MockEngine respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = PreviewData.profile.id,
            profiles = PreviewData.profiles, home = PreviewData.home, detail = PreviewData.detail, preferences = PreferencesSnapshot())
        var previousLoader: ImageLoader? = null
        var appLoader: ImageLoader? = null
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(browser = true, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }
        var frame = 0L
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }

        val original = System.out
        val buf = ByteArrayOutputStream()
        System.setOut(PrintStream(buf, true))
        try {
            until("the browser Details dialog") { pane(nodes(), "The End of Oak Street") != null }
            frames(40)
            if (browseSources) {
                controller.chooseSource(PreviewData.detail.item)
                until("the source drawer") { pane(nodes(), "Choose a source") != null }
                frames(60)
                releaseSources.complete(Unit)
                frames(60)
            }
        } finally {
            System.setOut(original)
            scene.close(); client.close(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
        assertFalse(buf.toString().contains("FocusRequester is not initialized"), "no focus warning, got:\n$buf")
    }
}