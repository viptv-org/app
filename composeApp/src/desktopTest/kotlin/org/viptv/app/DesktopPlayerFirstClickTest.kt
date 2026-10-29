package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopPlayerFirstClickTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun theTransportTakesAPointerPressAndTheTimelineSeeksAtTheClick() {
        var toggles = 0
        val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlayerScreen(null, false, null, { toggles++ }, {}, {}, preview = PlayerFact.Progress(0, 20_000))
        } }
        try {
            sceneFrames(scene)
            scenePress(scene, sceneNode(scene, "Pause").boundsInWindow.center)
            assertEquals(1, toggles, "the Pause press toggles playback")
            val slider = sceneNodes(scene).filter { it.config.contains(SemanticsActions.SetProgress) }.maxByOrNull { it.boundsInWindow.width }!!
            scenePress(scene, slider.boundsInWindow.center)
            val shown = sceneNodes(scene).map { sceneText(it) }
            assertTrue(shown.any { it in setOf("0:09", "0:10", "0:11") }, "the timeline click seeks to about half of 0:20, saw $shown")
        } finally { scene.close() }
    }
}

// DesktopPlayerWindowFirstClickTest opens a real window, so the scene rule can't cover it; it resolves the pointer target here.
internal fun playerPausePointer(width: Int, height: Int): Offset {
    var found: Offset? = null
    EventQueue.invokeAndWait {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            PlayerScreen(null, false, null, {}, {}, {}, preview = PlayerFact.Progress(0, 20_000))
        } }
        try {
            sceneFrames(scene)
            val bounds = sceneNode(scene, "Pause").boundsInWindow
            found = Offset(bounds.center.x, bounds.center.y)
        } finally { scene.close() }
    }
    return requireNotNull(found)
}

internal fun sceneFrames(scene: ImageComposeScene) = repeat(16) { scene.render(it * 16_666_667L).close() }

internal fun scenePress(scene: ImageComposeScene, at: Offset) {
    scene.sendPointerEvent(PointerEventType.Press, at)
    scene.sendPointerEvent(PointerEventType.Release, at)
    sceneFrames(scene)
}

private fun sceneNodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { sceneFlatten(it.rootSemanticsNode) }

private fun sceneNode(scene: ImageComposeScene, label: String) = sceneNodes(scene).single {
    (it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetProgress)) &&
        (sceneText(it) == label || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true)
}

private fun sceneFlatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::sceneFlatten)

private fun sceneText(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
