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
import org.viptv.app.state.ContinueWatchingList
import org.viptv.app.state.Screen
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
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
    private val continueCards = (1..30).map { n ->
        Card(MediaIdentity("cw-$n", "series", emptyList()), provider_id = "library", title = "In Progress %02d".format(n), badges = emptyList())
    }
    private val relatedDetail = DetailSnapshot(
        item = Card(MediaIdentity("related", "movie", emptyList()), provider_id = "preview", title = "A Related Title", badges = emptyList()),
        episodes = emptyList())

    private fun libraryState() = ApplicationState(
        screen = Screen.Library, selectedProfile = PreviewData.profile.id, profiles = PreviewData.profiles,
        home = PreviewData.home, library = libraryCards, localMode = true)

    private fun continueWatchingState() = ApplicationState(
        screen = Screen.ContinueWatching, selectedProfile = PreviewData.profile.id, profiles = PreviewData.profiles,
        home = PreviewData.home, continueWatching = ContinueWatchingList(PreviewData.profile.id, continueCards, next = null),
        localMode = true)

    private inner class Harness(initial: ApplicationState, width: Int = 1280, height: Int = 900) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store).also { it.mutable.value = initial }
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
        fun visibleTitles(prefix: String, count: Int) = (1..count).filter { onScreen(prefix.format(it)) }.toSet()
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
        Harness(libraryState()).use { app ->
            app.until("My List") { app.onScreen("My List 01") }
            assertFalse(app.onScreen("Continue Watching"), "My List, not Home, is the page")

            app.scrollDown(1600f)
            app.until("the grid scrolled") { !app.onScreen("My List 01") && app.visibleTitles("My List %02d", 30).isNotEmpty() }
            val before = app.visibleTitles("My List %02d", 30)
            assertTrue(before.isNotEmpty() && 1 !in before, "the viewer scrolled into My List: $before")

            app.controller.mutable.value = app.controller.state.value.copy(
                screen = Screen.Details, detailReturnScreen = Screen.Library, detail = PreviewData.detail)
            app.until("the Details dialog") { app.pane("The End of Oak Street") != null }
            assertEquals(before, app.visibleTitles("My List %02d", 30), "the My List grid stays composed behind the dialog")
            assertFalse(app.onScreen("Continue Watching"), "the background is not Home")

            app.controller.mutable.value = app.controller.state.value.copy(screen = Screen.Library)
            app.frames(20)
            assertEquals(before, app.visibleTitles("My List %02d", 30), "closing the dialog returns to the same scrolled position")
        }
    }

    @Test fun theContinueWatchingPageStaysBehindTheDialogAndKeepsItsScroll() {
        Harness(continueWatchingState()).use { app ->
            app.until("Continue Watching") { app.onScreen("In Progress 01") }

            app.scrollDown(1600f)
            app.until("the list scrolled") { !app.onScreen("In Progress 01") && app.visibleTitles("In Progress %02d", 30).isNotEmpty() }
            val before = app.visibleTitles("In Progress %02d", 30)
            assertTrue(before.isNotEmpty() && 1 !in before, "the viewer scrolled into Continue Watching: $before")

            app.controller.mutable.value = app.controller.state.value.copy(
                screen = Screen.Details, detailReturnScreen = Screen.ContinueWatching, detail = PreviewData.detail)
            app.until("the Details dialog") { app.pane("The End of Oak Street") != null }
            assertEquals(before, app.visibleTitles("In Progress %02d", 30), "the Continue Watching page stays composed behind the dialog")

            app.controller.mutable.value = app.controller.state.value.copy(screen = Screen.ContinueWatching)
            app.frames(20)
            assertEquals(before, app.visibleTitles("In Progress %02d", 30), "closing the dialog returns to the same scrolled position")
        }
    }

    @Test fun openingARelatedTitleKeepsTheSameOriginPageBehind() {
        Harness(libraryState()).use { app ->
            app.until("My List") { app.onScreen("My List 01") }
            app.controller.mutable.value = app.controller.state.value.copy(
                screen = Screen.Details, detailReturnScreen = Screen.Library, detail = PreviewData.detail)
            app.until("the first dialog") { app.pane("The End of Oak Street") != null }
            val before = app.visibleTitles("My List %02d", 30)

            app.controller.mutable.value = app.controller.state.value.copy(
                detail = relatedDetail, detailReturnScreen = Screen.Library, detailHistory = listOf(PreviewData.detail))
            app.until("the related dialog") { app.pane("A Related Title") != null }
            assertNull(app.pane("The End of Oak Street"), "the first dialog is replaced, not stacked behind")
            assertEquals(before, app.visibleTitles("My List %02d", 30), "the origin page stays behind the related title")
            assertEquals(Screen.Library, app.controller.state.value.detailReturnScreen)
            assertFalse(app.onScreen("Continue Watching"), "the background is not Home")
        }
    }

    private companion object {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    }
}