package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
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

/**
 * The add-on list is lazy: rows below the screen are not composed. D-pad Down
 * must still walk every add-on to "Add an add-on", scrolling each into view,
 * and neither end may let focus fall to the sidebar.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvAddonSettingsFocusTest {
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
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        settle(scene)
    }
    private fun name(index: Int) = "Add-on %02d".format(index)
    private val addons = (1..15).map {
        IntegrationConnection("addon:$it", "stremio", "7", null, name(it), true, 1, listOf(Capability.SOURCES))
    }
    private fun scene(logos: AddonLogoSource? = null, list: List<IntegrationConnection> = addons) = ImageComposeScene(1920, 1080, Density(1f)) {
        ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalAddonLogos provides logos) {
                TvSettingsScreen(false, list, setOf("addon:1"), false, null, null, {}, { _, _ -> }, {}, {}, null,
                    initialSection = TvSettingsSection.Addons)
            }
        }
    }
    private fun assertVisible(node: SemanticsNode, label: String) {
        val bounds = node.boundsInRoot
        // The 8 px focus ring must fit too, clear of the TV's bottom edge.
        assertTrue(bounds.top >= 8f && bounds.bottom <= 1080f - 8f && bounds.height > 0f, "$label is not fully on screen: $bounds")
    }

    @Test fun downWalksEveryAddonToTheAddRowAndUpReturnsWithoutLeavingTheList() = onEdt {
        val scene = scene()
        try {
            settle(scene)
            assertTrue(text(requireNotNull(focused(scene)) { "nothing focused" }).contains(name(1)), "initial focus is on the first add-on")
            assertFalse(nodes(scene).any { text(it).contains(name(15)) }, "the fixture must overflow the screen")
            for (index in 2..15) {
                press(scene, Key.DirectionDown)
                val node = requireNotNull(focused(scene)) { "focus lost moving to ${name(index)}" }
                assertTrue(text(node).contains(name(index)), "expected ${name(index)}, focused ${text(node)}")
                assertVisible(node, name(index))
            }
            press(scene, Key.DirectionDown)
            val add = requireNotNull(focused(scene))
            assertTrue(text(add).contains("Add an add-on"), "focused ${text(add)}")
            assertVisible(add, "Add an add-on")
            press(scene, Key.DirectionDown)
            assertTrue(text(requireNotNull(focused(scene))).contains("Add an add-on"), "Down at the end stays on the last row")

            for (index in 15 downTo 1) {
                press(scene, Key.DirectionUp)
                val node = requireNotNull(focused(scene)) { "focus lost moving up to ${name(index)}" }
                assertTrue(text(node).contains(name(index)), "expected ${name(index)}, focused ${text(node)}")
                assertVisible(node, name(index))
            }
            press(scene, Key.DirectionUp)
            assertTrue(text(requireNotNull(focused(scene))).contains(name(1)), "Up at the top stays on the first add-on")

            press(scene, Key.DirectionLeft)
            val sidebar = requireNotNull(focused(scene))
            assertFalse(text(sidebar).contains("Add-on 0"), "Left goes to the sidebar, focused ${text(sidebar)}")
        } finally { scene.close() }
    }

    @Test fun anEmptyListFocusesTheAddRow() = onEdt {
        val scene = scene(list = emptyList())
        try {
            settle(scene)
            assertTrue(text(requireNotNull(focused(scene))).contains("Add an add-on"))
            press(scene, Key.DirectionDown); press(scene, Key.DirectionUp)
            assertTrue(text(requireNotNull(focused(scene))).contains("Add an add-on"))
        } finally { scene.close() }
    }

    @Test fun rowsLoadOnlyTheServerLogoRouteAndKeepTheLetterTileWhenItFails() = onEdt {
        AddonLogoCache.clear()
        val requested = mutableListOf<String>()
        val list = listOf(addons[0].copy(logo = "/api/addons/1/logo?v=ab12"), addons[1])
        val scene = scene(AddonLogoSource { path -> requested += path; error("offline") }, list)
        try {
            settle(scene)
            assertEquals(listOf("/api/addons/1/logo?v=ab12"), requested)
            // The failed logo leaves the letter tile in place.
            assertTrue(nodes(scene).any { text(it).contains("A") && text(it).contains(name(1)) })
        } finally { scene.close() }
    }

    @Test fun aLoadedLogoReplacesTheLetterTile() = onEdt {
        AddonLogoCache.clear()
        val png = org.jetbrains.skia.Surface.makeRasterN32Premul(16, 16).run {
            canvas.clear(0xFFE0A030.toInt()); makeImageSnapshot().encodeToData()!!.bytes
        }
        val scene = scene(AddonLogoSource { png }, listOf(addons[0].copy(logo = "/api/addons/1/logo?v=cd34")))
        fun row() = nodes(scene).single { text(it).contains(name(1)) }
        try {
            settle(scene)
            repeat(20) { if (text(row()).startsWith("A ")) settle(scene) }
            assertEquals("${name(1)} Sources", text(row()), "the letter tile gives way to the logo")
        } finally { scene.close() }
    }
}
