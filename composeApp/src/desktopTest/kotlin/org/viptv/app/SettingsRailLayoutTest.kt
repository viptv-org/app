package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.settings.SettingsLayout
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.features.settings.railMetrics
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.BackEntry
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.*

private object NoRailSession : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

// T3Settings draws a 360 rail with 40/16 padding and 15 labels; D3SetGeneral a 260 rail with 28/16 padding and 14. Tablet used to take the desktop 240.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SettingsRailLayoutTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Harness(width: Int, height: Int, desktop: Boolean) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoRailSession).also {
            it.mutable.value = ApplicationState(screen = Screen.Settings, navBack = listOf(BackEntry(Screen.Home)),
                selectedProfile = "1", localMode = true)
        }
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme {
                if (state.screen == Screen.Settings) SettingsRoute(state, controller, browser = false, television = false, desktop = desktop)
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun node(label: String) = nodes().firstOrNull { text(it) == label } ?: fail("no node '$label' in ${nodes().map(::text).filter(String::isNotBlank)}")
        fun clickable(label: String) = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            ?: nodes().firstOrNull { text(it).contains(label) && it.config.contains(SemanticsActions.OnClick) }
            ?: fail("no clickable '$label'")
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
        assertTrue(abs(actual - expected) <= 1f, "$what is $actual, expected $expected")

    @Test fun theTabletRailIsT3SettingsAndTheDesktopRailIsD3SetGeneral() {
        val tablet = SettingsLayout.Tablet.railMetrics()
        val desktop = SettingsLayout.Desktop.railMetrics()
        assertEquals(360.dp, tablet.width)
        assertEquals(40.dp, tablet.horizontal)
        assertEquals(15.sp, tablet.rowLabelSize)
        assertEquals(260.dp, desktop.width)
        assertEquals(28.dp, desktop.horizontal)
        assertEquals(14.sp, desktop.rowLabelSize)
        assertNotEquals(tablet.width, desktop.width, "the two two-pane layouts no longer share one rail")
    }

    @Test fun tabletSettingsRowsSitInTheT360RailAndTheDesktopOnesInTheD260Rail() = runBlocking<Unit> {
        Harness(1194, 834, desktop = false).use { ui ->
            ui.frames()
            val row = ui.clickable("Subtitles & audio")
            closeTo(row.boundsInRoot.left, 40f, "the tablet row's left edge")
            closeTo(ui.node("Settings").boundsInRoot.left, 40f, "the tablet rail title's left edge")
            // 360 rail, 40 padding each side: the row spans 40 → 320.
            closeTo(row.boundsInRoot.right, 320f, "the tablet row's right edge")
            assertTrue(ui.nodes().any { ui.text(it) == "Add-ons" && it.boundsInRoot.left >= 360f },
                "the detail pane starts after the 360 rail")
            ui.capture("settings-tablet-1194")
        }
        Harness(1440, 900, desktop = true).use { ui ->
            ui.frames()
            val row = ui.clickable("Subtitles & audio")
            closeTo(row.boundsInRoot.left, 28f, "the desktop row's left edge")
            closeTo(row.boundsInRoot.right, 232f, "the desktop row's right edge")
            closeTo(ui.node("Settings").boundsInRoot.left, 28f, "the desktop rail title's left edge")
            assertTrue(ui.nodes().any { ui.text(it) == "Add-ons" && it.boundsInRoot.left >= 260f },
                "the detail pane starts after the 260 rail")
            ui.capture("settings-desktop-1440")
        }
    }
}
