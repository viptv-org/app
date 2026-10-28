package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.File
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

/**
 * My List gives the grid the room (app#58): scrolling the list takes the large title and the
 * History card away with the content while the type chips and Filters stay pinned at the top.
 * Phone, tablet and the browser layout share this; TV keeps its own fixed header.
 */
@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class LibraryCollapsingHeaderUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun card(index: Int) = Card(identity = MediaIdentity("l$index", if (index % 2 == 0) "movie" else "series", emptyList()),
        provider_id = "simkl", title = "Saved title $index", badges = emptyList())
    private val types = QueryControl("type", "Type", ControlKind.SINGLECHOICE, true,
        listOf(Choice("all", "All"), Choice("movie", "Movies"), Choice("series", "Series"), Choice("anime", "Anime")))
    private val genres = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, listOf(Choice("Action", "Action")))
    private fun list(count: Int) = LibrarySnapshot(Scope("server", "owner", "12", 4), 4, defaultLibraryQuery(),
        (1..count).map(::card), complete = true, controls = listOf(types, genres), availability = Availability.AVAILABLE)

    private inner class Fixture(scope: CoroutineScope, width: Int, private val height: Int, density: Float,
        television: Boolean = false, browser: Boolean = false, cards: Int = 80, error: String? = null) {
        val bootstraps = java.util.concurrent.atomic.AtomicInteger()
        private val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/bootstrap") bootstraps.incrementAndGet()
            respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val lane = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val controller = ApplicationController(lane, store).also {
            it.connection = RemoteConnection("https://watch.example", store, client, browserMode = browser)
            it.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "12", libraryPage = list(cards),
                profiles = listOf(Profile("12", "Parent", null)), error = error)
        }
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene = ImageComposeScene(width, height, Density(density)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(television = television, browser = browser, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }
        private val time = java.util.concurrent.atomic.AtomicLong()
        private val center = width / 2f
        fun close() {
            scene.close(); lane.cancel(); client.close()
            try { appLoader?.shutdown() } finally { previousLoader?.let { SingletonImageLoader.setUnsafe(it) } }
        }

        suspend fun frames(count: Int = 16) = repeat(count) { scene.render(time.addAndGet(16_666_667L)).close(); delay(4) }
        /** A finger drag from the middle of the page; negative [distance] scrolls the list down. */
        suspend fun drag(distance: Float) {
            val start = Offset(center, height * if (distance < 0) 0.8f else 0.35f)
            var at = start
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(2)
            val steps = 30
            repeat(steps) {
                at = start + Offset(0f, distance * (it + 1) / steps)
                scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
                frames(1)
            }
            frames(10)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(60)
        }
        /** A finger tap with no movement. */
        suspend fun tap(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(2)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(10)
        }
        /** Mouse-wheel notches over the grid; positive scrolls the list down. */
        suspend fun wheel(notches: Int) {
            val at = Offset(center, height * 0.7f)
            scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse)
            repeat(abs(notches)) {
                scene.sendPointerEvent(PointerEventType.Scroll, at, type = PointerType.Mouse,
                    scrollDelta = Offset(0f, if (notches > 0) 1f else -1f), timeMillis = time.get() / 1_000_000)
                frames(2)
            }
            frames(60)
        }
        suspend fun await(description: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                if (System.nanoTime() > deadline) fail("$description timed out; texts=" + nodes().map(::text).filter(String::isNotBlank))
                frames(1)
            }
        }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun node(label: String) = nodes().firstOrNull { text(it) == label }
        fun top(label: String) = assertNotNull(node(label), "$label on screen; texts=" + nodes().map(::text).filter(String::isNotBlank)).boundsInRoot.top
        /** The History card counts as shown only while some of it is below the pinned filter bar. */
        fun historyShown(): Boolean {
            val history = nodes().firstOrNull { text(it).contains("Everything you've played") } ?: return false
            val bar = node("Type: All") ?: return false
            return history.boundsInRoot.bottom > bar.boundsInRoot.bottom + 1f && history.boundsInRoot.top < height
        }
        fun cardTops() = nodes().filter { (text(it) + it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()).contains("Saved title") }
            .map { it.boundsInRoot.top }
        fun capture(name: String) { System.getenv("VIPTV_LIBRARY_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(time.addAndGet(16_666_667L)).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        } }
    }

    @Test fun onAPhoneHistoryScrollsAwayUnderPinnedControlsAndComesBackAtTheTop() = onEdt {
        val phone = Fixture(this, 1080, 2400, 2.625f, error = "Couldn't refresh My List")
        try {
            phone.frames()
            phone.await("My List on screen") { phone.node("Type: All") != null }
            assertTrue(phone.historyShown(), "History shows at the top")
            val filters = phone.top("Type: All"); val message = phone.top("Couldn't refresh My List")
            phone.capture("phone-top")
            phone.drag(-1400f)
            assertFalse(phone.historyShown(), "History scrolled away")
            assertEquals(filters, phone.top("Type: All"), "the filter bar stays pinned")
            assertEquals(message, phone.top("Couldn't refresh My List"), "the error stays with the pinned controls")
            phone.capture("phone-scrolled")
            // Empty space in the pinned controls covers scrolled cards; a tap there must not open one.
            val row = assertNotNull(phone.node("Type: All")).boundsInRoot
            phone.tap(row.left / 3f, row.center.y)
            assertEquals(Screen.Library, phone.controller.state.value.screen, "a tap on the pinned controls stays on My List")
            phone.drag(-900f)
            assertEquals(filters, phone.top("Type: All"), "the filter bar stays pinned while the grid keeps scrolling")
            // Pulling down while the list is scrolled scrolls it back; only the very top refreshes.
            phone.drag(1000f)
            assertEquals(0, phone.bootstraps.get(), "no pull-to-refresh before the list is back at the top")
            repeat(4) { if (!phone.historyShown()) phone.drag(500f) }
            phone.await("History returns at the top") { phone.historyShown() }
            assertEquals(filters, phone.top("Type: All"))
        } finally { phone.close() }
    }

    @Test fun onATabletTheTitleAndHistoryScrollAwayAndTheControlsPinToTheTop() = onEdt {
        val tablet = Fixture(this, 1280, 800, 1f)
        try {
            tablet.frames()
            tablet.await("My List on screen") { tablet.node("Type: All") != null }
            assertNotNull(tablet.nodes().firstOrNull { text(it) == "My List" && it.boundsInRoot.top > tablet.top("Type: All") - 120f }, "large title above the controls")
            assertTrue(tablet.historyShown())
            val filters = tablet.top("Type: All")
            tablet.capture("tablet-top")
            tablet.drag(-500f)
            assertFalse(tablet.historyShown(), "History scrolled away")
            val pinned = tablet.top("Type: All")
            assertTrue(pinned < filters, "the controls take the title's place: $pinned < $filters")
            val cards = tablet.cardTops()
            tablet.capture("tablet-scrolled")
            tablet.drag(-300f)
            assertEquals(pinned, tablet.top("Type: All"), "the filter bar stays pinned while the grid scrolls")
            assertNotEquals(cards, tablet.cardTops(), "the grid scrolled under the controls")
            // Back at the very top the title returns too, so the controls sit where they started.
            repeat(6) { if (tablet.top("Type: All") != filters) tablet.drag(300f) }
            tablet.await("History returns") { tablet.historyShown() }
            assertEquals(filters, tablet.top("Type: All"))
        } finally { tablet.close() }
    }

    @Test fun theBrowserLayoutCollapsesTheSameWayWithTheMouseWheel() = onEdt {
        val web = Fixture(this, 1440, 900, 1f, browser = true)
        try {
            web.frames()
            web.await("My List on screen") { web.node("Type: All") != null }
            assertTrue(web.historyShown())
            val filters = web.top("Type: All")
            web.capture("browser-top")
            web.wheel(6)
            assertFalse(web.historyShown(), "History scrolled away")
            val pinned = web.top("Type: All")
            assertTrue(pinned < filters, "the controls take the title's place: $pinned < $filters")
            web.capture("browser-scrolled")
            web.wheel(4)
            assertEquals(pinned, web.top("Type: All"), "the filter bar stays pinned while the grid scrolls")
            web.wheel(-30)
            web.await("History returns") { web.historyShown() }
            assertEquals(filters, web.top("Type: All"))
        } finally { web.close() }
    }

    @Test fun anEmptyListKeepsItsHeaderAndEmptyState() = onEdt {
        val phone = Fixture(this, 1080, 2400, 2.625f, cards = 0)
        try {
            phone.frames()
            phone.await("Empty My List on screen") { phone.node("Type: All") != null && phone.nodes().any { text(it).contains("Browse Home") } }
            assertTrue(phone.historyShown())
            val browse = phone.nodes().first { text(it).contains("Browse Home") }
            assertTrue(browse.boundsInRoot.top > phone.top("Type: All"), "the empty state sits below the header")
        } finally { phone.close() }
    }

    @Test fun theTvLayoutKeepsItsFixedHeaderWithoutAHistoryCard() = onEdt {
        val tv = Fixture(this, 1920, 1080, 1f, television = true)
        try {
            tv.frames()
            // The TV filters are the shared chip row (app#121); the row is the fixed header.
            tv.await("TV My List on screen") { tv.node("Type: All") != null }
            assertNull(tv.nodes().firstOrNull { text(it).contains("Everything you've played") }, "TV has no History card")
            val filters = tv.top("Type: All")
            tv.wheel(6)
            assertEquals(filters, tv.top("Type: All"), "TV header stays where it was")
        } finally { tv.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
