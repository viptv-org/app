package org.viptv.app

import org.junit.Rule
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
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.File
import kotlin.test.*

/** Pulling down at the top of a phone page shows the Material 3 indicator and rebuilds the page (app#1). */
@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class PullToRefreshUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun card(id: String, title: String, progress: Double? = null) =
        Card(identity = MediaIdentity(id, "movie", emptyList()), provider_id = "simkl", title = title, badges = emptyList(), progress = progress)
    private fun home(revision: Long, continuing: List<Card>) = HomeSnapshot(Scope("server", "owner", "12", revision), revision,
        rows = listOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = continuing),
            HomeRow(id = "top", title = "Popular", provider_id = "addon:1", state = RowState.READY,
                cards = (1..8).map { card("p$it", "Popular title $it") })), complete = true)
    private val shown = home(6, listOf(card("old", "Watched yesterday", 0.3)))
    private val fresh = home(7, listOf(card("new", "Started on the TV", 0.4), card("old", "Watched yesterday", 0.3)))
    private val bootstrap = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
            put("profile_id", "12")
        })
        put("home", Json.encodeToJsonElement(fresh)); put("integrations", JsonArray(emptyList()))
    }.toString()

    /** One phone (or desktop/TV) App on a controller whose server holds the rebuild until [release]. */
    private inner class Fixture(scope: CoroutineScope, width: Int, private val height: Int, density: Float,
        television: Boolean = false, desktopChrome: Boolean = false,
        state: ApplicationState = ApplicationState(screen = Screen.Home, selectedProfile = "12", home = shown)) {
        val release = CompletableDeferred<Unit>()
        val bootstraps = java.util.concurrent.atomic.AtomicInteger()
        private val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/bootstrap") {
                bootstraps.incrementAndGet(); release.await()
                respond(bootstrap, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val lane = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val controller = ApplicationController(lane, store).also {
            it.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
            it.mutable.value = state.copy(profiles = listOf(Profile("12", "Parent", null)))
        }
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene = ImageComposeScene(width, height, Density(density)) {
            val context = LocalPlatformContext.current
            // Earlier tests may already own Coil's singleton: give this App its own, as StartupShellUiTest does.
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(television = television, desktopChrome = desktopChrome, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }
        private val time = java.util.concurrent.atomic.AtomicLong()
        private val center = width / 2f
        fun close() {
            release.complete(Unit); scene.close(); lane.cancel(); client.close()
            try { appLoader?.shutdown() } finally { previousLoader?.let { SingletonImageLoader.setUnsafe(it) } }
        }

        suspend fun frames(count: Int = 16) = repeat(count) { scene.render(time.addAndGet(16_666_667L)).close(); delay(4) }
        /** A finger drag straight down from near the top of the page. */
        suspend fun pull(distance: Float) {
            val start = Offset(center, height * 0.3f)
            var at = start
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(2)
            val steps = 24
            repeat(steps) {
                at = start + Offset(0f, distance * (it + 1) / steps)
                scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
                frames(1)
            }
            frames(6)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Touch, timeMillis = time.get() / 1_000_000)
            frames(30)
        }
        suspend fun await(description: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                if (System.nanoTime() > deadline) fail("$description timed out; texts=" + nodes().map(::text).filter(String::isNotBlank))
                frames(1)
            }
        }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun refreshing() = nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate }
        fun shows(title: String) = nodes().any { text(it).contains(title) || it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains(title) } == true }
        fun capture(name: String) { System.getenv("VIPTV_HOME_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(time.addAndGet(16_666_667L)).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        } }
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    @Test fun aPhonePullShowsTheIndicatorAndRebuildsHomeInPlace() = onEdt {
        // A Pixel-class phone: 412 x 915 dp.
        val phone = Fixture(this, 1080, 2400, 2.625f)
        try {
            phone.frames()
            phone.await("Home on screen") { phone.shows("Watched yesterday") }
            assertFalse(phone.refreshing())
            phone.pull(1100f)
            phone.await("Pull starts the rebuild") { phone.bootstraps.get() == 1 }
            phone.await("Indicator while the rebuild runs") { phone.refreshing() }
            assertTrue(phone.shows("Watched yesterday"), "The current Home stays under the indicator")
            assertFalse(phone.controller.state.value.busy, "No full-screen loading cover")
            phone.capture("phone-pull-to-refresh-indicator")
            phone.release.complete(Unit)
            phone.await("Refreshed Home") { phone.shows("Started on the TV") && !phone.refreshing() }
            assertEquals(Screen.Home, phone.controller.state.value.screen); assertNull(phone.controller.state.value.error)
            phone.capture("phone-pull-to-refresh-refreshed")
        } finally { phone.close() }
    }

    @Test fun myListHistoryAndDetailsRefreshOnAPhonePull() = onEdt {
        val movie = card("m1", "Saved movie")
        val list = LibrarySnapshot(Scope("server", "owner", "12", 4), 4, defaultLibraryQuery(),
            (1..9).map { card("l$it", "Saved title $it") }, complete = true, availability = Availability.AVAILABLE)
        val pages = mapOf(
            Screen.Library to ApplicationState(screen = Screen.Library, selectedProfile = "12", home = shown, libraryPage = list),
            Screen.History to ApplicationState(screen = Screen.History, selectedProfile = "12", home = shown,
                history = (1..6).map { HistoryDisplay(HistoryEntry(card("h$it", "Watched title $it"), it.toLong()), "Today") }),
            Screen.Details to ApplicationState(screen = Screen.Details, selectedProfile = "12", home = shown,
                detail = DetailSnapshot(movie, episodes = emptyList()), detailReturnScreen = Screen.Home))
        val shownTitle = mapOf(Screen.Library to "Saved title 1", Screen.History to "Watched title 1", Screen.Details to "Saved movie")
        for ((screen, state) in pages) {
            val phone = Fixture(this, 1080, 2400, 2.625f, state = state)
            try {
                phone.frames()
                phone.await("$screen on screen") { phone.shows(shownTitle.getValue(screen)) }
                phone.pull(1100f)
                phone.await("A pull on $screen starts the rebuild") { phone.bootstraps.get() == 1 }
                phone.await("Indicator on $screen") { phone.refreshing() }
                phone.release.complete(Unit)
                phone.await("Indicator ends on $screen") { !phone.refreshing() }
                assertEquals(screen, phone.controller.state.value.screen)
            } finally { phone.close() }
        }
    }

    @Test fun aShortPullDoesNothing() = onEdt {
        val phone = Fixture(this, 1080, 2400, 2.625f)
        try {
            phone.frames()
            phone.await("Home on screen") { phone.shows("Watched yesterday") }
            phone.pull(120f)
            phone.frames(30)
            assertEquals(0, phone.bootstraps.get()); assertFalse(phone.refreshing())
        } finally { phone.close() }
    }

    @Test fun desktopAndTvLayoutsHaveNoPullToRefresh() = onEdt {
        for ((television, desktop) in listOf(false to true, true to false)) {
            val fixture = Fixture(this, 1920, 1080, 1f, television = television, desktopChrome = desktop)
            try {
                fixture.frames()
                fixture.await("Home on screen") { fixture.shows("Watched yesterday") }
                fixture.pull(700f)
                fixture.frames(30)
                assertEquals(0, fixture.bootstraps.get(), "No pull-to-refresh with television=$television desktop=$desktop")
                assertFalse(fixture.refreshing())
            } finally { fixture.close() }
        }
    }
}
