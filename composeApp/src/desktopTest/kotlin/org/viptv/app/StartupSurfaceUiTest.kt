package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Rule
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.entry.StartupScreen
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.disconnect
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class StartupSurfaceUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val layouts = listOf(390 to 844, 834 to 1112, 1280 to 800, 1440 to 900)

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun described(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    private fun capture(scene: ImageComposeScene, name: String) {
        val dir = System.getenv("VIPTV_STARTUP_CAPTURE_DIR") ?: return
        java.io.File(dir).mkdirs()
        scene.render(120_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) } }
    }

    @Test fun everyNonTvLayoutDrawsTheStartupSurface() {
        for ((width, height) in layouts) {
            var changes = 0
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme { StartupScreen({ changes++ }) } }
            try {
                repeat(40) { scene.render(it * 16_666_667L).close() }
                assertTrue(nodes(scene).any { described(it) == "Starting VIPTV" }, "${width}px: the screen names itself")
                assertTrue(nodes(scene).any { text(it) == "Getting VIPTV ready…" }, "${width}px: the status line is drawn")
                val action = nodes(scene).single { text(it) == "Change connection" && it.config.contains(SemanticsActions.OnClick) }
                assertFalse(action.config.contains(SemanticsProperties.Disabled), "${width}px: the action is enabled")
                assertTrue(action.boundsInRoot.top >= 0 && action.boundsInRoot.bottom <= height,
                    "${width}px: the action is inside the viewport: ${action.boundsInRoot}")
                assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, changes)
                capture(scene, "startup-$width")
            } finally { scene.close() }
        }
    }

    @Test fun theStartingAppDrawsItInsteadOfABlankWindowAndChangeConnectionReachesConnect() {
        for ((width, height) in listOf(390 to 844, 1280 to 800)) {
            val scheduler = TestCoroutineScheduler()
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
            val controller = ApplicationController(scope, object : RemoteSessionStore {
                override suspend fun read(): SavedRemoteSession? = null
                override suspend fun save(session: SavedRemoteSession) = Unit
                override suspend fun clear() = Unit
            })
            controller.mutable.value = ApplicationState(starting = true)
            var back: (() -> Unit)? = null
            val scene = ImageComposeScene(width, height, Density(1f), coroutineContext = StandardTestDispatcher(scheduler)) {
                App(providedController = controller, manageControllerLifecycle = false, entryBackChanged = { back = it })
            }
            try {
                repeat(8) { scheduler.runCurrent(); scene.render(it * 16_666_667L).close(); scheduler.runCurrent() }
                val rendered = nodes(scene)
                assertTrue(rendered.any { text(it) == "Getting VIPTV ready…" }, "${width}px: the waiting window says what it is doing")
                assertTrue(rendered.any { described(it) == "Starting VIPTV" }, "${width}px: and is named for assistive technology")
                val action = rendered.single { text(it) == "Change connection" && it.config.contains(SemanticsActions.OnClick) }
                assertNotNull(back, "${width}px: Back reaches the same action")
                action.config[SemanticsActions.OnClick].action!!.invoke()
                scheduler.runCurrent()
                assertEquals(Screen.Connect, controller.state.value.screen, "${width}px: Change connection reaches Connect")
                assertFalse(controller.state.value.starting, "${width}px: and ends the wait")
            } finally {
                scene.close(); controller.disconnect(); scope.cancel()
            }
        }
    }
}