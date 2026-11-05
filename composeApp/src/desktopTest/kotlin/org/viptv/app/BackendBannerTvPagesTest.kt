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
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.ContinueWatchingList
import org.viptv.app.state.Screen
import org.viptv.app.state.backSources
import org.viptv.app.state.dismissBackendBanner
import kotlin.test.*

/**
 * On TV the outage banner takes no focus, so the D-pad reaches Dismiss by pressing Up at the top of
 * whatever page the viewer is on. The pages without a header rail (Details, Sources, Settings,
 * Continue Watching) have no rail to move out of, so App owns that Up, and the focus the viewer
 * had on the page comes back when the banner goes.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class BackendBannerTvPagesTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val started = 1_760_000_000_000L

    private fun stateFor(screen: Screen) = ApplicationState(screen = screen, selectedProfile = PreviewData.profile.id,
        profiles = PreviewData.profiles, home = PreviewData.home, preferences = PreferencesSnapshot(),
        detail = PreviewData.detail, sourceTitle = PreviewData.detail.item, sources = PreviewData.sources, sourceReturnScreen = Screen.Details,
        continueWatching = ContinueWatchingList(PreviewData.profile.id, PreviewData.home.rows.first().cards, null))

    private inner class TvApp(screen: Screen) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store)
        private var focusManager: FocusManager? = null
        private var frame = 0L
        private val scene: ImageComposeScene

        init {
            controller.mutable.value = stateFor(screen)
            scene = ImageComposeScene(1920, 1080, Density(1f)) {
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                focusManager = LocalFocusManager.current
            }
        }

        val state get() = controller.state.value

        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun label(node: SemanticsNode): String =
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        fun focused(): String = nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            .joinToString(" | ") { label(it) }
        fun shows(text: String) = nodes().any { label(it).contains(text) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focused()})" }; frames(2) }
        }
        fun focus(text: String) {
            val node = nodes().first { label(it).contains(text) && it.config.contains(SemanticsActions.RequestFocus) }
            assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke(), "$text takes focus")
            frames(6)
        }
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(6)
        }
        fun showBanner(seconds: Int = 20) {
            controller.mutable.value = state.copy(backendUnreachableSince = started, backendRetrySeconds = seconds)
            frames(8)
            until("the banner") { shows("Can't reach the backend") }
        }
        override fun close() { scene.close(); controller.job?.cancel(); scope.cancel() }
    }

    @Test fun upFromTheTopOfEveryHeaderlessPageReachesDismissAndThePageKeepsItsFocus() {
        for ((screen, start) in listOf(Screen.Details to "Play",
            Screen.Settings to "Profile", Screen.ContinueWatching to "Monster")) {
            TvApp(screen).use { app ->
                app.frames()
                app.focus(start)
                val before = app.focused()
                app.showBanner()
                assertEquals(before, app.focused(), "$screen: the banner appears without taking focus")

                var presses = 0
                while (!app.focused().contains("Dismiss") && presses < 8) { app.press(Key.DirectionUp); presses++ }
                assertTrue(app.focused().contains("Dismiss"), "$screen: Up reaches Dismiss (focus '${app.focused()}' after $presses)")

                app.press(Key.Enter)
                app.until("the banner to go") { !app.shows("Can't reach the backend") }
                assertEquals(screen, app.state.screen, "$screen: dismissing the banner stays on the page")
                assertTrue(app.focused().contains(start), "$screen: focus returns to the page ($start, got '${app.focused()}')")
            }
        }
    }

    /** The source sheet is a full-screen focusable popup: it owns the remote until Back closes it, and the banner is underneath. */
    @Test fun theSourceSheetKeepsTheRemoteUntilBackAndTheBannerIsThereAfterwards() {
        TvApp(Screen.Sources).use { app ->
            app.frames()
            app.focus("Selected source")
            app.showBanner()
            assertEquals(Screen.Sources, app.state.screen)
            app.press(Key.DirectionUp)
            assertFalse(app.focused().contains("Dismiss"), "Up in the sheet moves inside the sheet, not to the banner behind it")
            assertEquals(Screen.Sources, app.state.screen, "the sheet stays open")

            app.controller.backSources()
            app.until("the sheet to close") { app.state.screen == Screen.Details }
            app.frames()
            app.until("the banner over Details") { app.shows("Can't reach the backend") }
            var presses = 0
            while (!app.focused().contains("Dismiss") && presses < 8) { app.press(Key.DirectionUp); presses++ }
            assertTrue(app.focused().contains("Dismiss"), "Up reaches Dismiss once the sheet is gone (focus '${app.focused()}')")
            app.press(Key.Enter)
            app.until("the banner to go") { !app.shows("Can't reach the backend") }
            assertEquals(Screen.Details, app.state.screen, "dismissing the banner stays on the page")
        }
    }

    @Test fun upStillMovesInsideAPageWhileTheBannerShows() {
        TvApp(Screen.Settings).use { app ->
            app.frames()
            app.focus("Profile")
            app.showBanner()
            val top = app.focused()
            app.press(Key.DirectionDown)
            assertFalse(app.focused().contains("Dismiss"), "Down leaves the rail row for the page, not the banner")
            assertNotEquals(top, app.focused(), "Down moves off Profile")
            app.press(Key.DirectionUp)
            assertFalse(app.focused().contains("Dismiss"), "Up inside the page does not jump to the banner")
            assertEquals(top, app.focused(), "Up returns to Profile")
        }
    }

    @Test fun theBannerClearingItselfLeavesTheViewersFocusAlone() {
        TvApp(Screen.Details).use { app ->
            app.frames()
            app.focus("Play")
            val before = app.focused()
            app.showBanner()
            assertEquals(before, app.focused())
            app.controller.mutable.value = app.state.copy(backendUnreachableSince = null, backendRetrySeconds = 0)
            app.frames()
            app.until("the banner to clear") { !app.shows("Can't reach the backend") }
            assertEquals(before, app.focused(), "an answer that clears the banner leaves the viewer on Play")
        }
    }

    @Test fun dismissKeepsThePageAndTheRestoredFocusAfterASecondOutage() {
        TvApp(Screen.Settings).use { app ->
            app.frames()
            app.focus("Profile")
            app.showBanner()
            app.press(Key.DirectionUp); app.press(Key.Enter)
            app.until("the banner to go") { !app.shows("Can't reach the backend") }
            app.controller.mutable.value = app.state.copy(backendBannerDismissed = false, backendUnreachableSince = started, backendRetrySeconds = 40)
            app.frames(8)
            app.until("the banner again") { app.shows("Can't reach the backend") }
            assertTrue(app.focused().contains("Profile"), "the next outage does not move the viewer (${app.focused()})")
            app.press(Key.DirectionUp)
            assertEquals("Dismiss", app.focused(), "the next outage is dismissible the same way")
            app.controller.dismissBackendBanner()
            app.frames()
            app.until("the banner to go") { !app.shows("Can't reach the backend") }
            assertTrue(app.focused().contains("Profile"), "focus returns to the page again (${app.focused()})")
        }
    }
}