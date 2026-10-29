package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class, ExperimentalCoroutinesApi::class)
class StartupShellUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun safeStartingScreenAllowsConnectionChangeBeforeAnimationAndHidesCachedPrivateContent() {
        repeat(5) { exerciseRecreation() }
    }
    private fun exerciseRecreation() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(scope, store)
        val privateCard = Card(MediaIdentity("private", "movie", emptyList()), provider_id = "stremio", title = "Private cached title", badges = emptyList())
        fun starting() = ApplicationState(starting = true, screen = Screen.Home, selectedProfile = "private",
            home = HomeSnapshot(Scope("fixture", "account", "private", 1), 1, hero = privateCard, rows = emptyList(), complete = true))
        controller.mutable.value = starting()
        var back: (() -> Unit)? = null
        var previousLoader: ImageLoader? = null
        var appLoader: ImageLoader? = null
        var firstClosed = false
        var recreated: ImageComposeScene? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f), coroutineContext = dispatcher) {
            val context = LocalPlatformContext.current
            remember {
                // Coil's default loader may already exist in this JVM, where setSafe refuses a later factory.
                previousLoader = SingletonImageLoader.get(context)
                SingletonImageLoader.reset()
            }
            App(television = true, providedController = controller, manageControllerLifecycle = false, entryBackChanged = { back = it })
            appLoader = SingletonImageLoader.get(context)
        }
        try {
            frames(scene, 0, scheduler)
            val action = nodes(scene).single { text(it) == "Change connection" && it.config.contains(SemanticsActions.OnClick) }
            assertFalse(action.config.contains(SemanticsProperties.Disabled))
            assertTrue(action.boundsInRoot.width > 0 && action.boundsInRoot.height > 0)
            assertTrue(nodes(scene).none { text(it).contains("Private cached title") })
            assertNotNull(back)
            System.getenv("VIPTV_STARTUP_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs()
                scene.render(150_000_000L).use { image -> image.encodeToData()!!.use { File(path, "tv-safe-starting.png").writeBytes(it.bytes) } }
            }
            action.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 10, scheduler)
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertFalse(controller.state.value.starting)
            assertNull(controller.state.value.home)
            assertNull(back)
            controller.mutable.value = starting()
            frames(scene, 20, scheduler)
            requireNotNull(back).invoke()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertNull(controller.state.value.home)
            firstClosed = true; scene.close()
            recreated = ImageComposeScene(1920, 1080, Density(1f), coroutineContext = dispatcher) {
                val context = LocalPlatformContext.current
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                assertSame(appLoader, SingletonImageLoader.get(context), "App recreation must retain its initialized image loader")
            }
            frames(requireNotNull(recreated), 40, scheduler)
        } finally {
            try { recreated?.close() }
            finally {
                try { if (!firstClosed) { firstClosed = true; scene.close() } }
                finally {
                    controller.disconnect(); scope.cancel()
                    try { appLoader?.shutdown() }
                    finally { previousLoader?.let { SingletonImageLoader.setUnsafe(it) } }
                    scheduler.runCurrent()
                }
            }
        }
    }
    private fun frames(scene: ImageComposeScene, offset: Int, scheduler: TestCoroutineScheduler) = repeat(8) {
        scheduler.runCurrent()
        scene.render((offset + it) * 16_666_667L).close()
        scheduler.runCurrent()
    }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
