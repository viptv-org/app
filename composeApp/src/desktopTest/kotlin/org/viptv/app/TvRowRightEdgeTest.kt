package org.viptv.app

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
import org.junit.Rule
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvRowRightEdgeTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun focused(scene: ImageComposeScene): SemanticsNode? = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(true) }.firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun text(node: SemanticsNode?): String = listOf(
        node?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node?.config?.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")

    private var focusManager: FocusManager? = null
    private var frame = 0L
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(20) }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        val direction = when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        }
        if (!consumed) requireNotNull(focusManager).moveFocus(direction)
        settle(scene)
    }

    @Test fun rightAtTheEndOfAHomeRowStaysInTheContent() = onEdt {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            focusManager = LocalFocusManager.current
            ViptvTheme(true) { UiPreview(television = true, screen = Screen.Home) }
        }
        try {
            settle(scene)
            val contentTop = 200f
            repeat(3) { row ->
                val start = requireNotNull(focused(scene)) { "nothing focused on row $row" }
                assertTrue(start.boundsInRoot.top >= contentTop, "row $row: focused '${text(start)}' at ${start.boundsInRoot}")
                val visited = mutableListOf(text(start))
                repeat(40) {
                    press(scene, Key.DirectionRight)
                    val node = requireNotNull(focused(scene)) { "row $row: focus lost after ${visited.size} Right presses" }
                    assertTrue(node.boundsInRoot.top >= contentTop, "row $row: Right left the content for '${text(node)}' at ${node.boundsInRoot} after '${visited.last()}'")
                    visited += text(node)
                }
                assertEquals(visited.last(), visited[visited.lastIndex - 1], "row $row: Right at the end stays on the last item")
                press(scene, Key.DirectionDown)
            }
        } finally { scene.close() }
    }
}
