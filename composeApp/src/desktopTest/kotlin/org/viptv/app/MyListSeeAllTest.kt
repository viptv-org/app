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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.viptv.app.connection.*
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.MY_LIST_ROW
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class MyListSeeAllTest {
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

    private fun savedCard(number: Int) = Card(MediaIdentity("saved-%02d".format(number), "movie", emptyList()),
        provider_id = "library", title = "Saved %02d".format(number), badges = emptyList())

    private fun home(rows: List<HomeRow>) = HomeSnapshot(Scope("viptv:remote", "account", "1", 1), 1, rows = rows, complete = true)

    private fun myListRow(cards: List<Card>) = HomeRow(id = MY_LIST_ROW, title = "My List", provider_id = "library",
        state = if (cards.isEmpty()) RowState.EMPTY else RowState.READY, cards = cards)

    private fun state(saved: Int, shown: Int = 12) = ApplicationState(selectedProfile = "1",
        home = home(listOf(myListRow((1..shown).map(::savedCard)))), library = (1..saved).map(::savedCard))

    private fun seeAll(saved: Int) = RowSeeAll(Screen.Library, "My List", "$saved titles saved", saved)

    @Test fun theCardAppearsOnlyWhenMyListHoldsMoreThanTheRowShows() {
        val row = myListRow((1..12).map(::savedCard))
        assertEquals(seeAll(20), state(20).rowSeeAll(row), "20 saved titles and a row of 12")
        assertNull(state(12).rowSeeAll(row), "the row is the whole list")
        assertNull(state(0).rowSeeAll(myListRow(emptyList())), "an empty list")
        assertNull(state(20).rowSeeAll(row.copy(id = "addon:1")), "only My List and Continue Watching carry the card")
    }

    @Test fun theCardOpensThePagedListAndBackReturnsHome() = runBlocking<Unit> {
        MyListHost(30).use { host ->
            val controller = ApplicationController(this, host.sessionStore)
            controller.connection = host.remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1",
                profiles = listOf(Profile("1", "QA", null)), home = home(listOf(myListRow((1..12).map(::savedCard)))),
                library = (1..30).map(::savedCard), preferences = PreferencesSnapshot())
            val card = controller.state.value.rowSeeAll(controller.state.value.home!!.rows.single())
            assertEquals(seeAll(30), card)
            controller.navigate(card!!.screen)
            until("the first page") { controller.state.value.libraryPage != null && !controller.state.value.busy }
            assertEquals(24, controller.state.value.libraryPage?.cards?.size)
            assertEquals("24", controller.state.value.libraryPage?.next_cursor)
            controller.libraryMore()
            until("the second page") { controller.state.value.libraryPage?.cards?.size == 30 && !controller.state.value.busy }
            assertEquals((1..30).map(::savedCard), controller.state.value.libraryPage?.cards)
            assertNull(controller.state.value.libraryPage?.next_cursor, "the whole list is here")
            assertEquals(listOf("0+100", "24+100"), host.reads.toList(), "one read per page")
            assertTrue(controller.state.value.canNavigateBack())
            controller.navigateBack()
            assertEquals(Screen.Home, controller.state.value.screen)
            assertEquals(seeAll(30), controller.state.value.rowSeeAll(controller.state.value.home!!.rows.single()),
                "Back on Home the row still knows My List holds more")
            controller.job?.cancel()
        }
    }

    @Test fun homeEndsMyListWithSeeAllThatOpensTheFullList() {
        for ((width, name) in listOf(390 to "phone", 1280 to "desktop")) {
            var opened = 0
            val scene = ImageComposeScene(width, 900, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(home(listOf(myListRow((1..2).map(::savedCard)))), television = false, open = {}, retry = {}, busy = false,
                        seeAllFor = { seeAll(20) }, openSeeAll = { opened++ })
                }
            } }
            try {
                frames(scene)
                tap(scene, "See all My List, 20 titles")
                assertEquals(1, opened, "$name: the end card opens the full list")
                if (width == 1280) {
                    val link = nodes(scene).first { it.config.contains(SemanticsActions.OnClick) && label(it) == "See all" }
                    link.config[SemanticsActions.OnClick].action?.invoke()
                    assertEquals(2, opened, "$name: the title's See all opens it too")
                }
            } finally { scene.close() }
        }
        val scene = ImageComposeScene(1280, 900, Density(1f)) { ViptvTheme {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                HomeScreen(home(listOf(myListRow((1..2).map(::savedCard)))), television = false, open = {}, retry = {}, busy = false,
                    seeAllFor = { null })
            }
        } }
        try {
            frames(scene)
            assertNull(clickable(scene, "See all My List"), "the row is the whole list")
        } finally { scene.close() }
    }

    @Test fun tvShelfEndsMyListWithSeeAllAndOkOpensThePage() = runBlocking {
        var opened = 0
        val first = FocusRequester()
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeShelf(myListRow((1..12).map(::savedCard)), 1f, first, up = null, focus = {}, open = {}, play = {},
                    seeAllFor = { seeAll(20) }, openSeeAll = { opened++ })
            }
        } }
        try {
            frames(scene); delay(30)
            first.requestFocus(); frames(scene)
            repeat(12) {
                val consumed = scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
                if (!consumed) requireNotNull(focusManager).moveFocus(FocusDirection.Right)
                frames(scene)
            }
            val focused = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::label)
            assertTrue(focused.any { it.contains("See all My List") }, "Right past the last saved title reaches See all: $focused")
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
            frames(scene)
            assertEquals(1, opened)
        } finally { scene.close() }
    }

    @Test fun tvBackFromMyListLandsOnTheEndCard() = runBlocking {
        MyListHost(20).use { host ->
            TvApp(host).use { app ->
                app.frames()
                app.focusOn("Saved 01", "Home opens on the My List shelf")
                app.press(Key.DirectionRight, 12)
                app.focusOn("See all My List", "Right past the 12th saved title")
                app.press(Key.Enter)
                app.until("the My List page") { app.state.screen == Screen.Library }
                app.until("its first page") { app.state.libraryPage != null && !app.state.busy }
                app.controller.navigateBack()
                app.until("Home") { app.state.screen == Screen.Home }
                app.focusOn("See all My List", "Back to Home lands on the end card that opened the page")
            }
        }
    }

    private suspend fun until(what: String, condition: () -> Boolean) =
        withTimeout(10_000) { while (!condition()) delay(10) }.also { assertTrue(condition(), what) }

    private inner class TvApp(host: MyListHost) : AutoCloseable {
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
                profiles = listOf(Profile("1", "QA", null)), home = home(listOf(myListRow((1..12).map(::savedCard)))),
                library = (1..20).map(::savedCard), preferences = PreferencesSnapshot())
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

    private class MyListHost(saved: Int) : AutoCloseable {
        val reads = CopyOnWriteArrayList<String>()
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val cards = (1..saved).map { number -> Card(MediaIdentity("saved-%02d".format(number), "movie", emptyList()),
            provider_id = "library", title = "Saved %02d".format(number), badges = emptyList()) }
        private val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath != "/api/profiles/1/library") respond("""{"error":"not in this fixture"}""", HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"))
            else {
                val offset = request.url.parameters["offset"]?.toLong() ?: 0L
                val limit = (request.url.parameters["limit"]?.toLong() ?: 100L).coerceAtMost(24L)
                reads += "$offset+${request.url.parameters["limit"] ?: 100}"
                val page = cards.drop(offset.toInt()).take(limit.toInt())
                val end = offset + page.size
                val next = (end < cards.size).takeIf { it }?.let { end.toString() }
                val snapshot = LibrarySnapshot(Scope("viptv:remote", "account", "1", 1), 1,
                    defaultLibraryQuery().copy(offset = offset, limit = request.url.parameters["limit"]?.toLong() ?: 100L),
                    page, next_cursor = next, complete = next == null, availability = Availability.AVAILABLE)
                respond(Json.encodeToString(snapshot), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val sessionStore: RemoteSessionStore get() = store
        override fun close() = remote.close()
    }
}
