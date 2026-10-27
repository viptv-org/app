package org.viptv.app

import org.junit.Rule
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.*
import kotlin.test.*

/**
 * app#45: in the phone App shell, each page's back arrow takes the same step as Android Back
 * (MainActivity: the route's own step, else [navigateBack] while [systemBackStaysInApp]).
 */
@OptIn(ExperimentalComposeUiApi::class, DelicateCoilApi::class)
class BackArrowsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val profiles = listOf(Profile("1", "Ana", null), Profile("2", "Ben", null, locked = true))

    private class Shell(start: ApplicationState, store: RemoteSessionStore) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, store).also { it.mutable.value = start }
        var routeBack: (() -> Unit)? = null
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            val context = LocalPlatformContext.current
            // App configures Coil's singleton loader; an earlier test may already have created the default one.
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            App(providedController = controller, manageControllerLifecycle = false, entryBackChanged = { routeBack = it })
            appLoader = SingletonImageLoader.get(context)
        }
        private var frame = 0L
        fun render() = repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun clickBack() {
            val node = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.firstOrNull {
                it.config.contains(SemanticsActions.OnClick) && (it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Back") == true ||
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Back" } == true)
            }
            assertNotNull(node, "no back arrow on ${controller.state.value.screen}").config[SemanticsActions.OnClick].action!!.invoke()
            render()
        }
        /** MainActivity's BackHandler. */
        fun androidBack() {
            assertTrue(routeBack != null || controller.state.value.systemBackStaysInApp(), "Android Back left the app from ${controller.state.value.screen}")
            routeBack?.invoke() ?: controller.navigateBack(); render()
        }
        override fun close() {
            scene.close(); scope.cancel()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private fun assertSameStep(name: String, start: ApplicationState, expected: Screen, check: (ApplicationState) -> Unit = {}) {
        val results = listOf("arrow", "Android Back", "navigateBack").map { input ->
            Shell(start, store).use { shell ->
                shell.render()
                when (input) {
                    "arrow" -> shell.clickBack()
                    "Android Back" -> shell.androidBack()
                    else -> { shell.controller.navigateBack(); shell.render() }
                }
                val state = shell.controller.state.value
                assertEquals(expected, state.screen, "$name: $input")
                check(state)
                Triple(state.screen, state.navBack, state.managingProfiles)
            }
        }
        assertEquals(1, results.distinct().size, "$name: the inputs disagree: $results")
    }

    private fun signedIn(screen: Screen, navBack: List<Screen>) = ApplicationState(screen = screen, navBack = navBack,
        selectedProfile = "1", profiles = profiles, localMode = true)

    @Test fun historyGoesBackToThePageItCameFrom() =
        assertSameStep("History", signedIn(Screen.History, listOf(Screen.Home, Screen.Library)), Screen.Library) {
            assertEquals(listOf(Screen.Home), it.navBack)
        }

    @Test fun theSettingsListGoesBackToThePageItCameFrom() =
        assertSameStep("Settings", signedIn(Screen.Settings, listOf(Screen.Calendar)).copy(settingsPage = SettingsPage()), Screen.Calendar)

    @Test fun theProfileSwitcherGoesBackToThePageItCameFrom() =
        assertSameStep("Profiles", signedIn(Screen.Profiles, listOf(Screen.Home, Screen.Live)), Screen.Live) {
            assertEquals(listOf(Screen.Home), it.navBack)
        }

    @Test fun theProfilePinAndEditorGoUpToProfiles() {
        assertSameStep("ProfilePin", signedIn(Screen.ProfilePin, listOf(Screen.Home)).copy(profileToOpen = "2"), Screen.Profiles) {
            assertEquals(listOf(Screen.Home), it.navBack, "the PIN pad must not become a history entry")
        }
        assertSameStep("ProfileEdit", signedIn(Screen.ProfileEdit, listOf(Screen.Home)).copy(profileToEdit = "2", managingProfiles = true), Screen.Profiles) {
            assertEquals(listOf(Screen.Home), it.navBack, "the editor must not become a history entry")
        }
    }
}
