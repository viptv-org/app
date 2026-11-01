package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.*
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class BrowserDetailsBackdropTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val libraryCards = (1..30).map { n ->
        Card(MediaIdentity("lib-$n", "movie", emptyList()), provider_id = "library", title = "My List %02d".format(n), badges = emptyList())
    }

    private inner class Harness(width: Int = 1280, height: Int = 900) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store).also {
            it.mutable.value = ApplicationState(
                screen = Screen.Library,
                selectedProfile = PreviewData.profile.id,
                profiles = PreviewData.profiles,
                home = PreviewData.home,
                library = libraryCards,
                localMode = true,
            )
        }
        private var frame = 0L
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene: ImageComposeScene = ImageComposeScene(width, height, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(browser = true, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }

        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun text(node: SemanticsNode): String = (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
        fun onScreen(title: String) = nodes().any { title in text(it) }
        fun pane(name: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == name }
        fun visibleLibrary() = (1..30).filter { onScreen("My List %02d".format(it)) }.toSet()
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }
        fun scrollDown(by: Float) {
            val node = nodes().filter {
                it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            }.maxBy { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
            node.config[SemanticsActions.ScrollBy].action?.invoke(0f, by)
            frames(); frames()
        }
        override fun close() {
            scene.close(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    @Test fun theOriginPageStaysBehindTheDetailsDialogAndKeepsItsScroll() {
        Harness().use { app ->
            app.until("My List") { app.onScreen("My List 01") }
            assertFalse(app.onScreen("Continue Watching"), "My List, not Home, is the page")

            app.scrollDown(1600f)
            app.until("the grid scrolled") { !app.onScreen("My List 01") && app.visibleLibrary().isNotEmpty() }
            val before = app.visibleLibrary()
            assertTrue(before.isNotEmpty() && 1 !in before, "the viewer scrolled into My List: $before")

            app.controller.mutable.value = app.controller.state.value.copy(
                screen = Screen.Details, detailReturnScreen = Screen.Library, detail = PreviewData.detail)
            app.until("the Details dialog") { app.pane("The End of Oak Street") != null }
            assertEquals(before, app.visibleLibrary(), "the My List grid stays composed behind the dialog")
            assertFalse(app.onScreen("Continue Watching"), "the background is not Home")

            app.controller.mutable.value = app.controller.state.value.copy(screen = Screen.Library)
            app.frames(20)
            assertEquals(before, app.visibleLibrary(), "closing the dialog returns to the same scrolled position")
        }
    }

    private companion object {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    }
}
