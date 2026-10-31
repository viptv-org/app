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
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Capability
import org.viptv.engine.wire.IntegrationConnection
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvAddonFilterFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun focused(scene: ImageComposeScene) = nodes(scene).singleOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun focusedText(scene: ImageComposeScene) = text(requireNotNull(focused(scene)) { "nothing focused" })
    private var focusManager: FocusManager? = null
    private var frame = 0L
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        settle(scene)
    }
    private suspend fun activate(scene: ImageComposeScene) {
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        settle(scene)
    }
    private fun name(index: Int) = "Add-on %02d".format(index)
    private val addons = (1..25).map {
        IntegrationConnection("addon:$it", "stremio", "7", null, name(it), true, 1,
            if (it % 2 == 0) listOf(Capability.SOURCES) else listOf(Capability.SUBTITLES))
    }
    private fun scene() = ImageComposeScene(1920, 1080, Density(1f)) {
        ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            TvSettingsScreen(false, addons, setOf("addon:1"), false, null, null, {}, { _, _ -> }, {}, {}, null,
                initialSection = TvSettingsSection.Addons)
        }
    }

    @Test fun upFromTheListReachesTheFilterRowAndDownReturnsToTheList() = onEdt {
        val scene = scene()
        try {
            settle(scene)
            assertTrue(focusedText(scene).contains(name(1)), "initial focus is on the first add-on")
            press(scene, Key.DirectionUp)
            assertFalse(focusedText(scene).contains("Add-on"), "Up from the first add-on reaches the filter row, focused ${focusedText(scene)}")
            press(scene, Key.DirectionDown)
            assertTrue(focusedText(scene).contains(name(1)), "Down returns to the first add-on, focused ${focusedText(scene)}")
        } finally { scene.close() }
    }

    @Test fun aProvidesChipFiltersTheListAndDownEntersIt() = onEdt {
        val scene = scene()
        try {
            settle(scene)
            press(scene, Key.DirectionUp)
            repeat(3) { if (!focusedText(scene).contains("Streams")) press(scene, Key.DirectionRight) }
            assertEquals("Streams (12)", focusedText(scene), "the chips carry what each one keeps")
            activate(scene)
            assertTrue(nodes(scene).any { text(it).contains(name(2)) }, "a streams add-on stays")
            assertFalse(nodes(scene).any { text(it).contains(name(1)) }, "a subtitles add-on is filtered out")
            press(scene, Key.DirectionDown)
            assertTrue(focusedText(scene).contains(name(2)), "Down enters the filtered list, focused ${focusedText(scene)}")
        } finally { scene.close() }
    }

    @Test fun theSearchChipOpensTheKeyboardAndTheTermFiltersTheList() = onEdt {
        val scene = scene()
        try {
            settle(scene)
            press(scene, Key.DirectionUp)
            repeat(3) { if (focusedText(scene) != "Search") press(scene, Key.DirectionLeft) }
            assertEquals("Search", focusedText(scene), "the search chip opens the row")
            activate(scene)
            assertTrue(nodes(scene).any { text(it) == "Search add-ons" }, "the keyboard panel opens")
            assertEquals("h", focusedText(scene), "the keyboard takes the D-pad")
            activate(scene)
            assertTrue(nodes(scene).any { text(it).contains("No add-ons match") }, "the typed term filters the list")
        } finally { scene.close() }
    }
}
