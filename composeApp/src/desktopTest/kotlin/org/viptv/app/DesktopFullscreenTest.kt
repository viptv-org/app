package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopFullscreenTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun episode(number: Long) = Card(
        MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        provider_id = "addon:7", title = "Episode $number", badges = emptyList())

    @Test fun fullscreenEntersAndExitsFromFloating() {
        val model = DesktopWindowPlacement()
        val bounds = DpSize(1000.dp, 600.dp) to DpOffset(20.dp, 30.dp)
        assertFalse(model.fullscreen)
        model.toggleFullscreen(bounds)
        assertTrue(model.fullscreen)
        assertEquals(WindowPlacement.Floating, model.preFullscreen)
        assertEquals(bounds, model.floatingBounds)
        model.toggleFullscreen(bounds)
        assertEquals(WindowPlacement.Floating, model.placement)
        assertNull(model.preFullscreen)
        assertEquals(bounds, model.takeFloatingBounds())
        assertNull(model.floatingBounds)
    }

    @Test fun fullscreenFromMaximizedReturnsToMaximized() {
        val model = DesktopWindowPlacement()
        val bounds = DpSize(1000.dp, 600.dp) to DpOffset(20.dp, 30.dp)
        model.maximize(bounds)
        assertTrue(model.maximized)
        assertEquals(bounds, model.floatingBounds)
        model.toggleFullscreen(bounds)
        assertTrue(model.fullscreen)
        assertEquals(WindowPlacement.Maximized, model.preFullscreen)
        assertEquals(bounds, model.floatingBounds, "fullscreen from maximized keeps the pre-maximize bounds")
        model.toggleFullscreen(bounds)
        assertEquals(WindowPlacement.Maximized, model.placement)
        assertNull(model.preFullscreen)
        model.maximize(bounds)
        assertEquals(WindowPlacement.Floating, model.placement)
        assertEquals(bounds, model.takeFloatingBounds())
    }

    @Test fun maximizeTogglesAndKeepsFloatingBounds() {
        val model = DesktopWindowPlacement()
        val bounds = DpSize(1000.dp, 600.dp) to DpOffset(20.dp, 30.dp)
        model.maximize(bounds)
        assertTrue(model.maximized)
        assertEquals(bounds, model.floatingBounds)
        model.maximize(bounds)
        assertEquals(WindowPlacement.Floating, model.placement)
        assertEquals(bounds, model.takeFloatingBounds())
        assertNull(model.floatingBounds)
    }

    @Test fun titleBarDoubleClickRoutesFullscreenDuringPlaybackAndMaximizeElsewhere() {
        var fullscreens = 0
        var maximizes = 0
        val controls = DesktopWindowControls(minimize = {}, maximize = { maximizes++ }, close = {},
            toggleFullscreen = { fullscreens++ })
        titleBarDoubleClick(Screen.Player, controls)()
        assertEquals(1, fullscreens)
        assertEquals(0, maximizes)
        titleBarDoubleClick(Screen.Home, controls)()
        assertEquals(1, fullscreens)
        assertEquals(1, maximizes)
        titleBarDoubleClick(Screen.Settings, controls)()
        assertEquals(1, fullscreens)
        assertEquals(2, maximizes)
    }

    @Test fun theDesktopTransportDrawsFullscreenAfterFitFill() {
        var fullscreens = 0
        val scene = playerScene(fullscreen = false, toggleFullscreen = { fullscreens++ })
        try {
            frames(scene)
            val fill = action(scene, "Fill video")
            val fullscreen = action(scene, "Enter fullscreen")
            assertTrue(fullscreen.boundsInRoot.left > fill.boundsInRoot.right,
                "Fullscreen follows Fit/Fill (${fill.boundsInRoot}) then (${fullscreen.boundsInRoot})")
            assertEquals(ToggleableState.Off, toggleState(scene, "Enter fullscreen"))
            click(scene, "Enter fullscreen")
            frames(scene)
            assertEquals(1, fullscreens, "the Fullscreen button toggles fullscreen")
        } finally { scene.close() }
    }

    @Test fun theDesktopTransportOmitsFullscreenWithoutAToggle() {
        val scene = playerScene(fullscreen = false, toggleFullscreen = null)
        try {
            frames(scene)
            assertNull(actionOrNull(scene, "Enter fullscreen"), "no fullscreen button when no toggle is wired")
        } finally { scene.close() }
    }

    @Test fun escapeExitsFullscreenWithoutClosingThePlayer() {
        var fullscreens = 0
        var closed = 0
        val scene = playerScene(fullscreen = true, toggleFullscreen = { fullscreens++ }, close = { closed++ })
        try {
            frames(scene)
            sendEscape(scene)
            assertEquals(1, fullscreens, "Escape exits fullscreen")
            assertEquals(0, closed, "Escape in fullscreen must not close the player")
        } finally { scene.close() }
    }

    @Test fun escapeStillClosesTheWindowedPlayer() {
        var fullscreens = 0
        var closed = 0
        val scene = playerScene(fullscreen = false, toggleFullscreen = { fullscreens++ }, close = { closed++ })
        try {
            frames(scene)
            sendEscape(scene)
            assertEquals(0, fullscreens)
            assertEquals(1, closed, "Escape closes the windowed player")
        } finally { scene.close() }
    }

    private fun playerScene(fullscreen: Boolean, toggleFullscreen: (() -> Unit)?, close: () -> Unit = {}) =
        ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, close, title = episode(1), phone = false,
                fullscreen = fullscreen, toggleFullscreen = toggleFullscreen)
        } }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close() }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun action(scene: ImageComposeScene, label: String) = actionOrNull(scene, label) ?: fail("$label is not on screen")

    private fun actionOrNull(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == label } == true &&
            it.config.contains(SemanticsActions.OnClick)
    }

    private fun toggleState(scene: ImageComposeScene, label: String) =
        action(scene, label).config.getOrNull(SemanticsProperties.ToggleableState)

    private fun click(scene: ImageComposeScene, label: String) {
        assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke())
    }

    private fun focus(scene: ImageComposeScene, label: String) {
        assertTrue(action(scene, label).config[SemanticsActions.RequestFocus].action!!.invoke(), "focus $label")
    }

    private fun sendEscape(scene: ImageComposeScene) {
        focus(scene, "Play")
        frames(scene)
        scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
        frames(scene)
    }
}