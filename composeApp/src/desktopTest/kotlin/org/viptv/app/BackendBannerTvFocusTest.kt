package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class BackendBannerTvFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val started = 1_760_000_000_000L

    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "fixture", title = id, badges = emptyList())
    private fun home() = HomeSnapshot(Scope("server", "owner", "1", 1), 1, hero = card("hero"), featured = listOf(card("hero")),
        rows = listOf(HomeRow(id = "top", title = "Top", provider_id = "addon:1", state = RowState.READY, cards = (1..6).map { card("c$it") })),
        complete = true)

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private inner class TvApp : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store)
        private var focusManager: FocusManager? = null
        private var frame = 0L
        private val scene: ImageComposeScene

        init {
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1",
                profiles = listOf(Profile("1", "QA", null)), home = home())
            scene = ImageComposeScene(1920, 1080, Density(1f)) {
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                focusManager = LocalFocusManager.current
            }
        }

        val state get() = controller.state.value
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun focused(): String = nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.joinToString(" | ") { label(it) }
        fun shows(text: String) = nodes().any { label(it).contains(text) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focused()})" }; frames(2) }
        }
        fun focus(text: String) {
            val node = nodes().first { label(it) == text && it.config.contains(SemanticsActions.RequestFocus) }
            assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke(), "$text takes focus")
            frames(4)
        }
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(4)
        }
        override fun close() { scene.close(); controller.job?.cancel(); scope.cancel() }
    }

    @Test fun theBannerNeverTakesFocusAndTheHeaderReachesItWithUp() {
        TvApp().use { app ->
            app.frames()
            app.until("the TV Home") { app.shows("Top") && app.shows("Movies") }
            app.focus("Movies")
            assertEquals("Movies", app.focused(), "the viewer is on the header rail")

            app.controller.mutable.value = app.state.copy(backendUnreachableSince = started, backendRetrySeconds = 20)
            app.frames()
            app.until("the banner") { app.shows("Can't reach the backend") }
            assertEquals("Movies", app.focused(), "the banner appears without taking focus from the page")

            app.press(Key.DirectionUp)
            assertEquals("Dismiss", app.focused(), "Up from the header rail reaches the banner's Dismiss")

            app.press(Key.DirectionDown)
            assertNotEquals("Dismiss", app.focused(), "Down leaves the banner instead of trapping the viewer")
            assertTrue(app.shows("Can't reach the backend"), "the banner stays until it is dismissed or the backend answers")

            app.press(Key.DirectionUp)
            assertEquals("Dismiss", app.focused())
            app.press(Key.Enter)
            app.until("the banner to go") { !app.shows("Can't reach the backend") }
            assertEquals("Home", app.focused(), "dismissing from the banner returns focus to the header rail")
        }
    }

    @Test fun thePageKeepsItsFocusWhenTheBannerClearsItself() {
        TvApp().use { app ->
            app.frames()
            app.until("the TV Home") { app.shows("Top") && app.shows("Movies") }
            app.focus("Movies")
            app.controller.mutable.value = app.state.copy(backendUnreachableSince = started, backendRetrySeconds = 10)
            app.frames()
            app.until("the banner") { app.shows("Can't reach the backend") }
            app.press(Key.DirectionUp)
            assertEquals("Dismiss", app.focused())

            app.controller.mutable.value = app.state.copy(backendUnreachableSince = null, backendRetrySeconds = 0)
            app.frames()
            app.until("the banner to clear") { !app.shows("Can't reach the backend") }
            assertEquals("Home", app.focused(), "a banner that clears itself hands focus back to the header rail")
        }
    }
}