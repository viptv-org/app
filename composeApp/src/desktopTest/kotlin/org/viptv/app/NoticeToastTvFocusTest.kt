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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.ListRemovalHost.Companion.libraryCard
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NoticeToastTvFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val host = ListRemovalHost()
    private val store: RemoteSessionStore get() = host.sessionStore

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
            controller.connection = host.remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1",
                profiles = listOf(Profile("1", "QA", null)), home = home())
            scene = ImageComposeScene(1920, 1080, Density(1f)) {
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                focusManager = LocalFocusManager.current
            }
        }

        val state get() = controller.state.value
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
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
        override fun close() { scene.close(); controller.job?.cancel(); scope.cancel(); host.close() }
    }

    private fun TvApp.showRemovalNotice() {
        controller.mutable.value = state.copy(notice = AppNotice("Removed from My List", NoticeAction.Undo, 1L),
            myListUndo = MyListUndo("1", libraryCard(2), 1))
        frames()
    }

    @Test fun theNoticeNeverTakesFocusAndUpFromTheTopReachesItsUndo() {
        TvApp().use { app ->
            app.frames()
            app.until("the TV Home") { app.shows("Top") && app.shows("Movies") }
            app.focus("Movies")
            assertEquals("Movies", app.focused(), "the viewer is on the header rail")

            app.showRemovalNotice()
            app.until("the notice") { app.shows("Removed from My List") }
            assertEquals("Movies", app.focused(), "the notice appears without taking focus from the page")

            app.press(Key.DirectionUp)
            assertEquals("Undo", app.focused(), "Up from the topmost control reaches the notice's Undo")

            app.press(Key.DirectionDown)
            assertNotEquals("Undo", app.focused(), "Down leaves the notice instead of trapping the viewer")
            assertTrue(app.shows("Removed from My List"), "the notice stays until it is used or its time is up")

            app.focus("Home")
            app.press(Key.DirectionUp)
            assertEquals("Undo", app.focused())
            app.press(Key.Enter)
            app.until("the notice to go") { !app.shows("Removed from My List") }
            assertNotEquals("Undo", app.focused(), "with the notice gone focus is back on the page, not lost")
            assertTrue(app.focused().isNotBlank(), "the D-pad lands on a control the viewer can use")
            assertNull(app.state.notice)
        }
    }

    @Test fun aConfirmationOnTvTakesNoFocusAndOffersNoAction() {
        TvApp().use { app ->
            app.frames()
            app.until("the TV Home") { app.shows("Movies") }
            app.focus("Movies")
            app.controller.mutable.value = app.state.copy(notice = AppNotice("Added to My List", null, 1L))
            app.frames()
            app.until("the notice") { app.shows("Added to My List") }
            assertEquals("Movies", app.focused(), "a text-only notice never moves the D-pad")
            app.press(Key.DirectionUp)
            assertEquals("Movies", app.focused(), "with nothing to reach, Up stays on the page's own control")
        }
    }
}