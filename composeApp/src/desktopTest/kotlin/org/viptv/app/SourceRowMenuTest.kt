@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.interimSourceGroups
import org.viptv.app.features.sources.SOURCE_HIDE_LABEL
import org.viptv.app.features.sources.SOURCE_MENU_TARGET_LABEL
import org.viptv.app.features.sources.SOURCE_PLAY_LABEL
import org.viptv.app.features.sources.SourceRowMenuActions
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.addonLabel
import org.viptv.app.features.sources.rowMenu
import org.viptv.app.features.sources.sourceConfigureLabel
import org.viptv.app.features.sources.sourceSettingsSection
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.junit.Rule
import kotlin.test.*

class SourceRowMenuTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun source(id: String, addon: String, name: String, height: Long = 1080) =
        SourceCandidate(id, "addon:$addon", "Stream $id", detail = "[Stream $id] Release $id",
            quality_height = height, presentation = SourcePresentation(provider_name = name, source_id = name))

    private val torrentio = source("a1", "1", "Torrentio")
    private val torrentioSecond = source("a2", "1", "Torrentio", height = 2160)
    private val comet = source("c1", "2", "Comet")
    private val sources = listOf(torrentio, torrentioSecond, comet)

    @Test fun theSettingsPageIsChosenByProducerKind(): Unit = runBlocking {
        assertEquals("Add-ons", sourceSettingsSection("addon:1"))
        assertEquals("Source integrations", sourceSettingsSection("vsources:4"))
        assertEquals("Source integrations", sourceSettingsSection("m3u:7"))
        assertEquals("Xtream connections", sourceSettingsSection("iptv:12"))
        assertNull(sourceSettingsSection("simkl"), "a producer with no Settings page offers no Configure row")
        assertNull(torrentio.copy(provider_id = "server").rowMenu(emptyList(), {}, {}).configure)
        assertNotNull(torrentio.rowMenu(emptyList(), {}, {}).configure)
    }

    @Test fun aClosingMenuStillDrawsItsExit() = runBlocking {
        Picker(sources, pointer = false).use { picker ->
            picker.hold("Stream a1")
            assertFalse(picker.menuItems().isEmpty())
            picker.clickable("Cancel").config[SemanticsActions.OnClick].action!!.invoke()
            picker.frames(2)
            assertFalse(picker.menuItems().isEmpty(), "the menu keeps drawing its 200 ms exit")
            picker.frames(25)
            assertTrue(picker.menuItems().isEmpty(), "and is gone once it ends")
        }
    }

    @Test fun configureNamesTheAddOnAndFallsBackToIt(): Unit = runBlocking {
        assertEquals("Configure Torrentio", sourceConfigureLabel(torrentio))
        assertEquals("Torrentio", torrentio.addonLabel())
        assertEquals("this add-on", torrentio.copy(provider_id = "addon:9", presentation = null).addonLabel())
        assertEquals("Comet", torrentio.copy(provider_id = "addon:2").addonLabel(listOf(org.viptv.app.connection.SourceProducerOutcome("addon:2", "Comet"))))
    }

    @Test fun aLongPressOpensTheSourceMenu(): Unit = runBlocking {
        var played: SourceCandidate? = null
        Picker(sources, pointer = false, choose = { played = it }).use { picker ->
            picker.hold("Stream a1")
            assertEquals(listOf("Play this source", "Source details", "Configure Torrentio", SOURCE_HIDE_LABEL, "Cancel"), picker.menuItems())
            assertNull(played, "a hold opens the menu instead of playing")
            picker.frames(20)
            assertNull(played, "the release that ends a hold does not also play the row")
        }
    }

    @Test fun aShortTapStillPlaysTheSource(): Unit = runBlocking {
        var played: SourceCandidate? = null
        Picker(sources, pointer = false, choose = { played = it }).use { picker ->
            picker.tap("Stream a1")
            picker.frames(10)
            assertEquals("a1", played?.id)
            assertTrue(picker.menuItems().isEmpty(), "a tap opens no menu")
        }
    }

    @Test fun theMenuPlaysItsOwnRow(): Unit = runBlocking {
        var played: SourceCandidate? = null
        Picker(sources, pointer = false, choose = { played = it }).use { picker ->
            picker.hold("Stream a1")
            picker.press(SOURCE_PLAY_LABEL)
            assertEquals("a1", played?.id)
            assertTrue(picker.menuItems().isEmpty(), "the menu closes on a pick")
        }
    }

    @Test fun configureHandsOverTheAddOnThatProducedTheSource(): Unit = runBlocking {
        var configured: String? = null
        Picker(sources, pointer = false, configure = { configured = it }).use { picker ->
            picker.hold("Stream a1")
            picker.press("Configure Torrentio")
            assertEquals("addon:1", configured)
        }
    }

    @Test fun sourceDetailsKeepsThePanelTheHoldUsedToOpen(): Unit = runBlocking {
        Picker(sources, pointer = false).use { picker ->
            picker.hold("Stream a1")
            picker.press("Source details")
            picker.frames(30)
            assertNotNull(picker.pane("Source details"), "the menu still reaches Source details")
        }
    }

    @Test fun hidingAnAddOnDropsItsRowsAndItsChipEntry(): Unit = runBlocking {
        Picker(sources, pointer = false, groups = interimSourceGroups(sources)).use { picker ->
            assertEquals(listOf("Stream a1", "Stream a2", "Stream c1"), picker.streams())
            picker.hold("Stream a1")
            picker.press(SOURCE_HIDE_LABEL)
            picker.frames(10)
            assertEquals(listOf("Stream c1"), picker.streams(), "every row of that add-on leaves this picker")
            assertTrue(picker.texts().contains("1 source from 1 add-on"), "the summary counts what is offered: ${picker.texts()}")
            picker.open("Source: All sources")
            assertEquals(listOf("All sources", "Comet"), picker.options(), "the Source chip stops listing the hidden add-on")
        }
    }

    @Test fun hidingTheLastAddOnShowsTheFilteredOutLine(): Unit = runBlocking {
        Picker(listOf(comet), pointer = false).use { picker ->
            picker.hold("Stream c1")
            picker.press(SOURCE_HIDE_LABEL)
            picker.frames(10)
            assertTrue(picker.texts().contains("No matching sources. Choose another provider or quality."),
                "hiding every add-on is the filtered-out state, not the no-sources one: ${picker.texts()}")
            assertFalse(picker.texts().contains("No sources available. Check your add-ons in Settings."))
        }
    }

    @Test fun aSecondaryClickOpensTheMenuOnDesktop(): Unit = runBlocking {
        Picker(sources, pointer = true).use { picker ->
            assertTrue(picker.menuItems().isEmpty())
            picker.rightClick("Stream a1")
            assertEquals(listOf("Play this source", "Source details", "Configure Torrentio", SOURCE_HIDE_LABEL, "Cancel"), picker.menuItems())
        }
    }

    @Test fun thePointerRowCarriesItsOwnMenuTarget(): Unit = runBlocking {
        Picker(sources, pointer = true).use { picker ->
            assertEquals(3, picker.clickables(SOURCE_MENU_TARGET_LABEL), "each pointer row has a ⋯ target")
            picker.press(SOURCE_MENU_TARGET_LABEL)
            assertEquals(listOf("Play this source", "Source details", "Configure Torrentio", SOURCE_HIDE_LABEL, "Cancel"), picker.menuItems())
        }
        Picker(sources, pointer = false).use { picker ->
            assertEquals(0, picker.clickables(SOURCE_MENU_TARGET_LABEL), "touch rows use long-press instead")
        }
    }

    @Test fun aHoldOfOkOpensTheMenuOnTv(): Unit = runBlocking {
        var played: SourceCandidate? = null
        Picker(sources, pointer = false, television = true, width = 1920, height = 1080, choose = { played = it }).use { picker ->
            picker.frames(30)
            picker.keyDown(Key.DirectionCenter)
            picker.waitFor("the source menu on hold") { picker.menuItems().isNotEmpty() }
            assertEquals(listOf("Play this source", "Source details", "Configure Torrentio", SOURCE_HIDE_LABEL, "Cancel"), picker.menuItems())
            assertNull(played, "holding OK must not start the source")
            picker.keyUp(Key.DirectionCenter)
        }
    }

    @Test fun rendersForEvidence(): Unit = runBlocking {
        val dir = System.getenv("VIPTV_MENU_CAPTURE_DIR") ?: return@runBlocking
        Picker(sources, pointer = false, width = 390, height = 844).use { picker ->
            picker.hold("Stream a1")
            picker.frames(40)
            picker.capture(dir, "menu-phone-390")
        }
        Picker(sources, pointer = false, width = 390, height = 844).use { picker ->
            picker.hold("Stream a1")
            picker.press(SOURCE_HIDE_LABEL)
            picker.frames(20)
            picker.capture(dir, "hidden-phone-390")
        }
        Picker(sources, pointer = true, width = 1280, height = 800).use { picker ->
            picker.press(SOURCE_MENU_TARGET_LABEL)
            picker.frames(40)
            picker.capture(dir, "menu-desktop-1280")
        }
        Picker(sources, pointer = true, width = 1440, height = 900).use { picker ->
            picker.press(SOURCE_MENU_TARGET_LABEL)
            picker.frames(40)
            picker.capture(dir, "menu-desktop-1440")
        }
        Picker(sources, pointer = false, television = true, width = 1920, height = 1080).use { picker ->
            picker.frames(30)
            picker.keyDown(Key.DirectionCenter)
            picker.waitFor("the source menu on hold") { picker.menuItems().isNotEmpty() }
            picker.frames(40)
            picker.capture(dir, "menu-tv-1920")
            picker.keyUp(Key.DirectionCenter)
        }
    }

    private class Picker(sources: List<SourceCandidate>, pointer: Boolean, television: Boolean = false,
        width: Int = 1440, height: Int = 900, groups: org.viptv.app.connection.SourceGroups = org.viptv.app.connection.SourceGroups(),
        choose: (SourceCandidate) -> Unit = {}, configure: (String) -> Unit = {}) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SourcesScreen(null, sources, false, null, choose, {}, television = television, groups = groups,
                    configureSource = configure)
            } }
        }.also { scene -> repeat(20) { scene.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }

        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { flatten(it) }
        private fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun all(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        private fun label(node: SemanticsNode): String =
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + text(node)).joinToString(" ").trim()
        fun texts(): List<String> = all().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        fun pane(name: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == name }
        fun clickables(name: String) = nodes().count { label(it) == name && it.config.contains(SemanticsActions.OnClick) }

        fun menuItems(): List<String> {
            if (pane(org.viptv.app.features.sources.SOURCE_MENU_PANE) == null) return emptyList()
            val rows = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Button }
                .filter { label(it).isNotBlank() }
                .sortedBy { it.boundsInRoot.top }
                .map { label(it) }
            return rows.filter { it in setOf("Play this source", "Source details", SOURCE_HIDE_LABEL, "Cancel") || it.startsWith("Configure ") }
                .distinct()
        }

        fun streams(): List<String> = all().filter { node -> text(node).matches(Regex("Stream [a-z]\\d+")) }
            .sortedBy { it.boundsInRoot.top }.map { text(it) }

        private fun row(name: String) = all().first { text(it) == name }.boundsInRoot.center

        fun tap(name: String) {
            val at = row(name)
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Touch, timeMillis = 1_000)
            frames(2)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Touch, timeMillis = 1_050)
            frames(10)
        }

        suspend fun hold(name: String) {
            val at = row(name)
            var clock = 1_000L
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Touch, timeMillis = clock)
            repeat(40) { delay(20); clock += 20; scene.render(clock * 1_000_000L).close() }
            frames(30)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Touch, timeMillis = clock)
            frames(30)
        }

        fun rightClick(name: String) {
            val at = row(name)
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Mouse, buttons = PointerButtons(isSecondaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Mouse, buttons = PointerButtons())
            frames(30)
        }

        fun press(name: String) {
            assertTrue(clickable(name).config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }

        fun clickable(name: String) = nodes().firstOrNull { label(it) == name && it.config.contains(SemanticsActions.OnClick) }
            ?: fail("no clickable $name")

        fun open(name: String) {
            val trigger = nodes().first { text(it).startsWith(name) && it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.DropdownList }
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }

        fun options() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.RadioButton }
            .sortedBy { it.boundsInRoot.top }
            .map { it.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text }

        fun keyDown(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); frames(2) }
        fun keyUp(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)); frames(2) }

        suspend fun waitFor(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 6_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what" }
                frames(2); delay(15)
            }
        }

        fun capture(dir: String, name: String) {
            java.io.File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) }
            }
        }

        override fun close() = scene.close()
    }
}