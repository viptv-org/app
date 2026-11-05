package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
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
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.BottomBarHeight
import org.viptv.app.components.BottomNavigation
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.disconnect
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalComposeUiApi::class)
class SearchPhoneNavTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val profile = Profile("12", "Parent", null)
    private val query = "monster"

    @Test fun thePhoneShellKeepsTheBottomNavOnSearch() {
        for ((width, height) in listOf(390 to 844, 360 to 640)) shell(width, height) { app ->
            app.frames()
            val bar = app.barTop()
            for (label in listOf("Home", "Live", "Calendar", "My List")) {
                val tab = assertNotNull(app.clickable(label), "${width}dp: the bottom nav offers $label on Search: ${app.texts()}")
                assertTrue(tab.boundsInRoot.top >= bar && tab.boundsInRoot.bottom <= app.height + 1f,
                    "${width}dp: $label sits in the bar at the bottom of the page: ${tab.boundsInRoot}")
            }
            assertNotNull(app.searchButton(), "${width}dp: the bottom nav keeps its Search button: ${app.texts()}")

            app.scrollToEnd()
            app.frames()
            assertTrue(app.barHidden(), "${width}dp: the bar hides at the end of Search (Home tab top=${app.homeTabTop().roundToInt()}, page bottom=${app.height})")
            val results = app.nodes().filter { app.text(it).startsWith("Result ") }
            assertTrue(results.isNotEmpty(), "${width}dp: Search shows results: ${app.texts()}")
            val lowest = results.maxOf { it.boundsInRoot.bottom }
            assertTrue(lowest >= bar, "${width}dp: the last row fills the former bar region once the bar is gone (${lowest.roundToInt()} vs bar ${bar.roundToInt()})")

            app.scrollBy(-48f); app.frames(80)
            assertFalse(app.barHidden(), "${width}dp: a 48 dp scroll up brings the bar back (Home tab top=${app.homeTabTop().roundToInt()}, page bottom=${app.height})")
            val homeTop = app.homeTabTop()
            assertTrue(homeTop >= bar - 1f && homeTop <= app.height - 1f,
                "${width}dp: the bar sits back in its slot with the 80 dp padding restored (${homeTop.roundToInt()} vs bar ${bar.roundToInt()})")
        }
    }

    @Test fun theBottomNavLeavesSearchAndComesBackToTheSameQuery() = shell { app ->
        app.frames()
        app.click("My List")
        app.until("My List opens") { app.controller.state.value.screen == Screen.Library }
        app.frames()
        assertNotNull(app.searchButton(), "My List keeps the Search button: ${app.texts()}")

        app.clickSearchButton()
        app.until("Search returns") { app.controller.state.value.screen == Screen.Search }
        app.frames()
        assertEquals(query, app.controller.state.value.searchTerm, "the query survives the round trip")
        assertTrue(app.nodes().any { app.text(it) == "Result 1" }, "the results are back: ${app.texts()}")
    }

    @Test fun theSearchButtonReadsAsCurrentOnlyOnSearch() {
        val onSearch = searchButtonPixel(Screen.Search)
        val onHome = searchButtonPixel(Screen.Home)
        assertTrue(brightness(onSearch) - brightness(onHome) >= 12,
            "the Search button is filled while Search is the page: ${hex(onSearch)} vs ${hex(onHome)}")
    }

    @Test fun theBottomBarStaysOnSearchAndNoOverlayPage() {
        for (screen in listOf(Screen.Home, Screen.Discover, Screen.Live, Screen.Calendar, Screen.Library, Screen.Search))
            assertTrue(bottomBarShown(screen, compact = true, browser = false, desktopChrome = false), "$screen keeps the phone bottom bar")
        for (screen in listOf(Screen.Details, Screen.Sources, Screen.Settings))
            assertFalse(bottomBarShown(screen, compact = true, browser = false, desktopChrome = false), "$screen opens without the bottom bar")
        assertFalse(bottomBarShown(Screen.Search, compact = false, browser = false, desktopChrome = false), "a wide layout has no bottom bar")
        assertFalse(bottomBarShown(Screen.Search, compact = true, browser = true, desktopChrome = false), "the browser shell has no bottom bar")
        assertFalse(bottomBarShown(Screen.Search, compact = true, browser = false, desktopChrome = true), "the desktop shell has no bottom bar")
    }

    private fun results() = (1..30).map { index ->
        Card(MediaIdentity("search-$index", "movie", emptyList()), provider_id = "addon:1",
            title = "Result $index", badges = emptyList())
    }

    private inner class Shell(val width: Int = 390, val height: Int = 844) : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val client = HttpClient(MockEngine {
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        val scene: ImageComposeScene
        private var frame = 0L

        init {
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Search, profiles = listOf(profile),
                selectedProfile = profile.id, searchTerm = query, results = results())
            scene = ImageComposeScene(width, height, Density(1f)) {
                App(providedController = controller, manageControllerLifecycle = false)
            }
        }

        fun barTop() = height - BottomBarHeight.value.roundToInt().toFloat()
        fun homeTabTop() = (clickable("Home") ?: fail("no Home tab: ${texts()}")).boundsInRoot.top
        fun barHidden() = homeTabTop() >= height - 1f
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun texts() = nodes().map(::text).filter(String::isNotBlank)

        fun clickable(label: String) = nodes().singleOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        fun click(label: String) = assertTrue(assertNotNull(clickable(label), "no clickable $label: ${texts()}")
            .config[SemanticsActions.OnClick].action!!.invoke(), "$label is clickable")

        fun searchButton() = nodes().singleOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && "Search" in description(node) && node.boundsInRoot.top >= barTop()
        }
        fun clickSearchButton() = assertTrue(assertNotNull(searchButton(), "no Search button in the bar: ${texts()}")
            .config[SemanticsActions.OnClick].action!!.invoke(), "the Search button is clickable")

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what (screen=${controller.state.value.screen})" }
                frames(2)
            }
        }

        fun scrollBy(delta: Float) {
            val scroll = nodes().filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller: ${texts()}")
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta)
        }

        fun scrollToEnd() {
            var before = -1f
            repeat(60) {
                val scroll = nodes().filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                    .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller: ${texts()}")
                val value = scroll.config[SemanticsProperties.VerticalScrollAxisRange].value()
                if (value <= before + 0.5f) return
                before = value
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
                frames(6)
            }
            fail("the results never reached their end")
        }

        override fun close() {
            scene.close()
            controller.disconnect()
            scope.cancel()
            remote.close()
        }
    }

    private fun shell(width: Int = 390, height: Int = 844, test: (Shell) -> Unit) = Shell(width, height).use(test)

    // Inside the 52 dp disc and clear of the glyph.
    private fun searchButtonPixel(selected: Screen): Int {
        var frame = 0L
        val scene = ImageComposeScene(390, BottomBarHeight.value.roundToInt(), Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { BottomNavigation(selected, {}) } }
        }
        try {
            repeat(20) { scene.render(frame++ * 16_666_667L).close() }
            val icon = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
                .filter { "Search" in description(it) }.minByOrNull { it.boundsInRoot.width }
                ?: fail("no Search icon in the bar")
            val at = icon.boundsInRoot.center
            val pixels = Bitmap.makeFromImage(scene.render(frame++ * 16_666_667L))
            return pixels.use { it.getColor(floor(at.x).toInt(), floor(at.y + 24f).toInt()) }
        } finally { scene.close() }
    }

    private fun description(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun brightness(color: Int) = (0..2).sumOf { (color shr (it * 8)) and 0xFF } / 3
    private fun hex(color: Int) = "#" + color.toUInt().toString(16).padStart(8, '0')
}
