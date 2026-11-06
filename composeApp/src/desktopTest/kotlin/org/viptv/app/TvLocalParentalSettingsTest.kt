package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.BackEntry
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

private object NoLocalSession : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

// Local mode has no household PIN and no maturity policy: TV must say where the local PIN lives instead of drawing controls that open the profile switcher.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvLocalParentalSettingsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Tv(width: Int = 1920, height: Int = 1080) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoLocalSession).also {
            it.mutable.value = ApplicationState(screen = Screen.Settings, navBack = listOf(BackEntry(Screen.Home)),
                selectedProfile = "1", localMode = true, settingsEntrySection = "Parental controls")
        }
        var focusManager: FocusManager? = null
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme(true) {
                focusManager = LocalFocusManager.current
                if (state.screen == Screen.Settings) SettingsRoute(state, controller, browser = false, television = true)
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun shows(value: String) = nodes().any { text(it).startsWith(value) }
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text).orEmpty()
        fun click(value: String) {
            val node = nodes().firstOrNull { text(it).startsWith(value) && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value' in ${nodes().map(::text).filter(String::isNotBlank)}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun activate() {
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
            frames()
        }
        fun frames(count: Int = 16) = repeat(count) { scene.render(++frame * 16_666_667L).close() }
        suspend fun settle() = repeat(24) { scene.render(++frame * 16_666_667L).close(); delay(2) }
        fun capture(name: String) {
            System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$name.png").writeBytes(it.bytes) } }
            }
        }
        override fun close() { scene.close(); scope.cancel() }
    }

    @Test fun localModeOffersTheLocalPinPageInsteadOfControlsThatOpenTheProfileSwitcher() = runBlocking {
        Tv().use { tv ->
            tv.settle()
            assertTrue(tv.shows("Parental controls"), "the section keeps its heading")
            assertTrue(tv.shows("Manage each local profile’s PIN in Profiles."), "it says where a local profile's PIN lives")
            assertTrue(tv.shows("Manage profiles"), "it offers the page that owns the PIN")
            for (fake in listOf("Movies up to", "Series up to", "Hide titles with no rating", "PIN to leave", "PG-13", "TV-MA"))
                assertFalse(tv.shows(fake), "local mode has no household policy, so '$fake' is not drawn")
            assertEquals(Screen.Settings, tv.controller.state.value.screen, "nothing opened the profile switcher by itself")
        }
    }

    @Test fun theLocalPageRendersAt1920And1024() = runBlocking {
        for ((width, height) in listOf(1920 to 1080, 1024 to 768)) {
            Tv(width, height).use { tv ->
                tv.settle()
                tv.capture("tv-local-parental-$width")
                assertEquals(Screen.Settings, tv.controller.state.value.screen)
            }
        }
    }

    @Test fun theRemoteLandsOnManageProfilesAndItOpensTheProfilesPage() = runBlocking {
        Tv().use { tv ->
            tv.settle()
            assertTrue(tv.focusedText().startsWith("Manage profiles"), "focus opens on the only control, was '${tv.focusedText()}'")
            tv.activate()
            assertEquals(Screen.Profiles, tv.controller.state.value.screen, "OK on Manage profiles opens Profiles")
        }
        Tv().use { tv ->
            tv.settle()
            tv.click("Manage profiles")
            assertEquals(Screen.Profiles, tv.controller.state.value.screen, "a pointer press opens Profiles too")
        }
    }
}
