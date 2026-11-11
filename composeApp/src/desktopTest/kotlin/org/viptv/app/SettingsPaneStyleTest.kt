package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.features.settings.paneStyle
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.BackEntry
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*

private object NoPaneSession : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

// T3Settings draws the detail pane with 40 padding under a 26/700 title; D3SetGeneral with 28 / 48 under a 24/700 one.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SettingsPaneStyleTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Pane(width: Int, height: Int, desktop: Boolean) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoPaneSession).also {
            it.mutable.value = ApplicationState(screen = Screen.Settings, navBack = listOf(BackEntry(Screen.Home)),
                selectedProfile = "1", localMode = true)
        }
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    if (state.screen == Screen.Settings)
                        SettingsRoute(state, controller, browser = false, television = false, desktop = desktop)
                }
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        // A row with the same words sits in the rail; the detail pane's own title is the right-hand one.
        fun detailTitle(label: String) = nodes().filter { text(it) == label }.maxByOrNull { it.boundsInRoot.left }
            ?: fail("no node '$label' in ${nodes().map(::text).filter(String::isNotBlank)}")
        fun frames() = repeat(24) { scene.render(++frame * 16_666_667L).close() }
        fun capture(name: String) {
            System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$name.png").writeBytes(it.bytes) } }
            }
        }
        override fun close() { scene.close(); scope.cancel() }
    }

    private fun closeTo(actual: Float, expected: Float, what: String) =
        assertTrue(abs(actual - expected) <= 1.5f, "$what is $actual, expected $expected")

    @Test fun eachTwoPaneLayoutOwnsItsPaneStyle() {
        val tablet = SettingsLayout.Tablet.paneStyle()
        val desktop = SettingsLayout.Desktop.paneStyle()
        assertEquals(40.dp, tablet.horizontal)
        assertEquals(40.dp, tablet.vertical)
        assertEquals(26.sp, tablet.titleSize)
        assertEquals(28.dp, desktop.horizontal)
        assertEquals(48.dp, desktop.vertical)
        assertEquals(24.sp, desktop.titleSize)
    }

    @Test fun theTabletPaneUsesT3SettingsSpacingAndTheDesktopPaneD3SetGeneral() {
        runBlocking<Unit> {
            var tabletTitle: androidx.compose.ui.geometry.Rect? = null
            Pane(1194, 834, desktop = false).use { pane ->
                pane.frames()
                val title = pane.detailTitle("Add-ons")
                tabletTitle = title.boundsInRoot
                closeTo(title.boundsInRoot.left, 360f + 40f, "the tablet title's left")
                closeTo(title.boundsInRoot.top, 40f, "the tablet title's top")
                pane.capture("settings-pane-tablet-1194")
            }
            Pane(1440, 900, desktop = true).use { pane ->
                pane.frames()
                val title = pane.detailTitle("Add-ons")
                closeTo(title.boundsInRoot.left, 260f + 28f, "the desktop title's left")
                closeTo(title.boundsInRoot.top, 48f, "the desktop title's top")
                val taller = requireNotNull(tabletTitle).height - title.boundsInRoot.height
                assertTrue(taller in 1.5f..4.5f, "the tablet title is 26/700 and the desktop 24/700 (height difference $taller)")
                pane.capture("settings-pane-desktop-1440")
            }
        }
    }

    @Test fun theBodySitsInsideTheSamePanePaddingAsItsTitle() {
        runBlocking<Unit> {
            Pane(1194, 834, desktop = false).use { pane ->
                pane.frames()
                val body = pane.nodes().first { pane.text(it).startsWith("Add-ons find the movies") }
                closeTo(body.boundsInRoot.left, 400f, "the tablet body's left")
            }
            Pane(1440, 900, desktop = true).use { pane ->
                pane.frames()
                val body = pane.nodes().first { pane.text(it).startsWith("Add-ons find the movies") }
                closeTo(body.boundsInRoot.left, 288f, "the desktop body's left")
            }
        }
    }
}
