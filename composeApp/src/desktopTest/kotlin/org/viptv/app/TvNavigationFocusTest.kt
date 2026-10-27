package org.viptv.app

import org.junit.Rule
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.components.TvNavigation
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

/** The profile menu is the only remote path to Settings, so Right from the last tab must reach it. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvNavigationFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun focused(scene: ImageComposeScene) = nodes(scene).singleOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private var focusManager: FocusManager? = null
    private var frame = 0L
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(if (key == Key.DirectionLeft) FocusDirection.Left else FocusDirection.Right)
        settle(scene)
    }

    @Test fun rightFromTheLastTabReachesTheProfileMenuAndStopsThere() = onEdt {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                focusManager = LocalFocusManager.current
                TvNavigation(Screen.Library, null) {}
            }
        }
        try {
            settle(scene)
            assertTrue(text(requireNotNull(focused(scene)) { "nothing focused" }).contains("My List"))
            press(scene, Key.DirectionRight)
            assertTrue(text(requireNotNull(focused(scene)) { "focus lost" }).contains("Profile menu"), "focused ${focused(scene)?.let(::text)}")
            press(scene, Key.DirectionRight)
            assertTrue(text(requireNotNull(focused(scene))).contains("Profile menu"), "Right at the end stays on the profile menu")
            press(scene, Key.DirectionLeft)
            assertTrue(text(requireNotNull(focused(scene))).contains("My List"), "Left returns to the last tab")
        } finally { scene.close() }
    }
}
