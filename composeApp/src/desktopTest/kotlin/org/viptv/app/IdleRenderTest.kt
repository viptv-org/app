package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.viptv.app.connection.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * app#95: an idle desktop window stops asking for frames. The desktop shell
 * (title bar and App) runs on Home with a rotating hero and on Search. The scene
 * is polled on wall-clock time and rendered only when something invalidated it,
 * as the desktop window's frame dispatcher does, and the frames are counted.
 *
 * The hero's per-pixel progress fill and slide dissolves were the only idle frame
 * source: ~6 frames/s at density 1 (12 at density 2), forever. The hero now
 * rotates while the viewer is present and holds still once they are idle.
 */
@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class IdleRenderTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun idleDesktopHomeAndSearchStopRequestingFrames() = runBlocking {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        // The production idle threshold is 20 s; 2 s keeps the test short.
        val controller = ApplicationController(scope, store, freshness = Freshness(idleAfter = 2.seconds))
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = PreviewData.profile.id,
            profiles = PreviewData.profiles, home = PreviewData.home.copy(featured = listOf(PreviewData.hero) + PreviewData.posters.take(3)))
        var previousLoader: ImageLoader? = null
        var appLoader: ImageLoader? = null
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            val state by controller.state.collectAsState()
            Column {
                DesktopTitleBar(state, false, controller::navigate, controller::search, {}, {}, {}, {}, Modifier)
                Box(Modifier.weight(1f)) { App(desktopChrome = true, providedController = controller, manageControllerLifecycle = false) }
            }
            appLoader = SingletonImageLoader.get(context)
        }
        val start = System.nanoTime()
        /**
         * Runs [millis] of wall time, rendering whenever the scene asks for a frame,
         * with the mouse nudged every 250 ms when [active]. The pointer stays in the gutter
         * right of the hero, clear of cards and their hover previews. Returns the frame count.
         */
        var nudge = 0
        fun run(millis: Long, active: Boolean = false): Int {
            var frames = 0
            var nextInput = 0L
            val until = System.nanoTime() + millis * 1_000_000
            while (System.nanoTime() < until) {
                if (active && System.nanoTime() >= nextInput) {
                    scene.sendPointerEvent(PointerEventType.Move, Offset(1425f, 200f + nudge++ % 2))
                    nextInput = System.nanoTime() + 250_000_000
                }
                if (scene.hasInvalidations()) { scene.render(System.nanoTime() - start).close(); frames++ }
                Thread.sleep(4)
            }
            return frames
        }
        fun featured() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.single { it.startsWith("Featured ") }
        try {
            // A present viewer still sees the hero rotate: past one 8 s slide, frames keep coming.
            val first = run(1_000, active = true).let { featured() }
            assertEquals("Featured 1 of 4", first)
            val rotating = run(9_000, active = true)
            assertEquals("Featured 2 of 4", featured(), "the hero advances while the viewer is present")
            assertTrue(rotating > 20, "the progress fill draws while rotating: $rotating frames")

            // Idle: the hero holds its slide and the window stops drawing.
            run(4_000)
            val held = featured()
            val home = run(10_000)
            assertTrue(home < 10, "idle Home asked for $home frames in 10 s")
            assertEquals(held, featured(), "the hero holds its slide while the viewer is idle")

            // Input resumes the rotation where it stopped.
            repeat(4) { println("[DEBUG-95] resume ${it}: frames=${run(500, active = true)} present=${controller.viewerPresent} ${featured()}") }
            assertTrue(run(1_000, active = true) > 2, "input resumes the hero's progress")

            controller.navigate(Screen.Search)
            run(4_000)
            val search = run(10_000)
            assertTrue(search < 10, "idle Search asked for $search frames in 10 s")
        } finally {
            scene.close(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
