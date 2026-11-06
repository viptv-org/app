package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class TapTargetTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun everyClickableTargetOnEveryPageIsAtLeast44dp() {
        val uncaught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
        val violations = try {
            buildList {
                for ((width, height) in listOf(390 to 844, 1280 to 900)) {
                    AppShell(width, height).use { shell ->
                        for ((name, state) in a11yPages) {
                            shell.show(state)
                            addAll(shell.violations().map { "$name at ${width}px: $it" })
                        }
                    }
                }
                addAll(surfaceViolations())
            }
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
        assertTrue(uncaught.isEmpty(), "a page crashed while it was rendered:\n${uncaught.joinToString("\n")}")
        assertTrue(violations.isEmpty(), "clickable targets under ${TapTargetPixels.toInt()}dp:\n${violations.joinToString("\n")}")
    }

    private fun surfaceViolations(): List<String> = buildList {
        for (surface in a11ySurfaces) {
            val scene = ImageComposeScene(surface.width, surface.height, Density(1f)) {
                ViptvTheme(surface.television) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) { surface.content() }
                }
            }
            try {
                scene.renderFrames(count = 80)
                addAll(scene.semanticsNodes().tapTargetViolations(surface.width, surface.height).map { "${surface.name}: $it" })
            } finally { scene.close() }
        }
    }

    private class AppShell(private val width: Int, private val height: Int) : AutoCloseable {
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val controller = ApplicationController(scope, store)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }

        fun show(state: ApplicationState) { controller.mutable.value = state }

        fun violations(): List<String> {
            frame = scene.renderFrames(frame, count = 80)
            return scene.semanticsNodes().tapTargetViolations(width, height)
        }

        override fun close() {
            scene.close(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }
}
