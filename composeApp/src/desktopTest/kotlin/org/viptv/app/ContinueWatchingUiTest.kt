package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.*
import org.junit.Rule
import org.viptv.app.ContinueWatchingHost.Companion.card
import org.viptv.app.ContinueWatchingHost.Companion.home
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.Profile
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class ContinueWatchingUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun clickable(scene: ImageComposeScene, text: String) = nodes(scene).firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
    }
    private fun tap(scene: ImageComposeScene, text: String) =
        assertTrue(requireNotNull(clickable(scene, text)) { "$text is not on screen" }.config[SemanticsActions.OnClick].action?.invoke() == true)
    private fun frames(scene: ImageComposeScene, count: Int = 8) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_CONTINUE_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }

    @Test fun homeEndsTheRowWithSeeAllOnlyWhenTheListHoldsMore() {
        for ((width, name) in listOf(390 to "phone", 1280 to "desktop")) {
            for (more in listOf(RowSeeAll(Screen.ContinueWatching, "Continue Watching", "40 titles in progress", 40), null)) {
                var opened = 0
                val scene = ImageComposeScene(width, 900, Density(1f)) { ViptvTheme {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        HomeScreen(home(1), television = false, open = {}, retry = {}, busy = false,
                            seeAllFor = { more }, openSeeAll = { opened++ })
                    }
                } }
                try {
                    frames(scene)
                    if (more == null) {
                        assertNull(clickable(scene, "See all"), "$name: the row is the whole list")
                        continue
                    }
                    if (width == 390) capture(scene, "home-row-phone")
                    tap(scene, "See all Continue Watching, 40 titles")
                    assertEquals(1, opened, "$name: the end card opens the page")
                    val link = nodes(scene).first { it.config.contains(SemanticsActions.OnClick) && label(it) == "See all" }
                    link.config[SemanticsActions.OnClick].action?.invoke()
                    assertEquals(2, opened, "$name: the title's See all opens the page")
                } finally { scene.close() }
            }
        }
    }

    @Test fun tvShelfEndsWithSeeAllAndOkOpensThePage() = runBlocking {
        var opened = 0
        val first = FocusRequester()
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeShelf(home(2).rows[0], 1f, first, up = null, focus = {}, open = {}, play = {},
                    seeAllFor = { RowSeeAll(Screen.ContinueWatching, "Continue Watching", "Everything in progress") },
                    openSeeAll = { opened++ })
            }
        } }
        try {
            frames(scene); delay(30)
            first.requestFocus(); frames(scene)
            repeat(2) {
                val consumed = scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
                if (!consumed) requireNotNull(focusManager).moveFocus(FocusDirection.Right)
                frames(scene)
            }
            val focused = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::label)
            assertTrue(focused.any { it.contains("See all Continue Watching") }, "Right past the last card reaches See all: $focused")
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
            frames(scene)
            assertEquals(1, opened)
        } finally { scene.close() }
    }

    @Test fun thePageShowsTheCardsLoadsMoreAndResumesTheTappedOne() {
        for ((width, name) in listOf(390 to "phone", 1280 to "desktop")) {
            val played = mutableListOf<Card>(); val opened = mutableListOf<Card>(); var more = 0
            val page = ContinueWatchingPage((1..24).map(::card), loaded = true)
            val scene = ImageComposeScene(width, 900, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    ContinueWatchingScreen(page, play = { played += it }, open = { opened += it }, more = { more++ },
                        retry = {}, back = {}, home = {})
                }
            } }
            try {
                frames(scene)
                capture(scene, "page-$name")
                tap(scene, "Show 03")
                assertEquals(listOf(card(3)), played, "$name: the card resumes that exact episode")
                assertTrue(opened.isEmpty(), "$name: never a detour through Details")
                val grid = nodes(scene).first { it.config.contains(SemanticsActions.ScrollToIndex) }
                grid.config[SemanticsActions.ScrollToIndex].action?.invoke(24)
                frames(scene)
                assertEquals(1, more, "$name: one request for the next page")
            } finally { scene.close() }
        }
    }

    @Test fun thePageHasLoadingEmptyAndFailedStates() {
        for ((page, expected) in listOf(ContinueWatchingPage(emptyList(), loaded = true) to "Browse Home",
            ContinueWatchingPage(emptyList(), loaded = false, error = "Could not reach your server.") to "Try again")) {
            var home = 0; var retry = 0
            val scene = ImageComposeScene(390, 900, Density(1f)) { ViptvTheme {
                ContinueWatchingScreen(page, play = {}, open = {}, more = null, retry = { retry++ }, back = {}, home = { home++ })
            } }
            try {
                frames(scene)
                tap(scene, expected)
                assertEquals(if (expected == "Browse Home") 1 to 0 else 0 to 1, home to retry)
            } finally { scene.close() }
        }
    }

    private inner class TvApp(host: ContinueWatchingHost) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, host.sessionStore)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        private var focusManager: FocusManager? = null
        private var frame = 0L
        val scene: ImageComposeScene

        init {
            controller.connection = host.remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1",
                profiles = listOf(Profile("1", "QA", null)), home = home(12), preferences = PreferencesSnapshot())
            scene = ImageComposeScene(1920, 1080, Density(1f)) {
                val context = LocalPlatformContext.current
                remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                focusManager = LocalFocusManager.current
                appLoader = SingletonImageLoader.get(context)
            }
        }

        val state get() = controller.state.value
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun focused() = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::label)
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focused()})" }; frames(2) }
        }
        // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
        fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(6)
        }
        fun focusOn(text: String, why: String) = until("$why: focus on $text") { focused().any { it.startsWith(text) } }

        override fun close() {
            scene.close(); controller.job?.cancel(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    @Test fun tvWalksTheEndCardThePageGridAndBackWithFocusRestored() {
        ContinueWatchingHost(40).use { host ->
            TvApp(host).use { app ->
                app.frames()
                app.focusOn("Show 01", "Home opens on the shelf")
                app.until("Home reads the full list") { app.state.continueWatching != null }
                app.press(Key.DirectionRight, 12)
                app.focusOn("See all Continue Watching", "Right past the 12th card")
                capture(app.scene, "tv-home-row")
                app.press(Key.Enter)
                app.until("the page") { app.state.screen == Screen.ContinueWatching }
                app.focusOn("Show 01", "the page opens on its first card")
                capture(app.scene, "tv-page")
                app.press(Key.DirectionDown); app.focusOn("Show 05", "Down")
                app.press(Key.DirectionRight); app.focusOn("Show 06", "Right")
                app.press(Key.DirectionUp); app.focusOn("Show 02", "Up")
                app.press(Key.DirectionUp); app.focusOn("Show 02", "Up from the first row stays in the grid")
                app.press(Key.DirectionDown, 5)
                app.until("the second page") { app.state.continueWatching?.cards?.size == 40 }
                assertTrue("24+24" in host.reads, "${host.reads}")
                app.focusOn("Show 22", "five rows down")
                app.press(Key.Enter)
                app.until("the source selector") { app.state.screen == Screen.Sources }
                assertEquals(card(22), app.state.sourceTitle)
                app.frames(30)
                app.controller.backSources()
                assertEquals(Screen.Details, app.state.screen)
                app.controller.backDetails()
                app.until("the page") { app.state.screen == Screen.ContinueWatching }
                app.focusOn("Show 22", "Back from the title's Details returns to the card the viewer left")
                app.controller.navigateBack()
                assertEquals(Screen.Home, app.state.screen)
                app.focusOn("See all Continue Watching", "Back to Home lands on the end card that opened the page")
            }
        }
    }
}
