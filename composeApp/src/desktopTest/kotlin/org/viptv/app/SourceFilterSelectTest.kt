package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.components.SelectOption
import org.viptv.app.connection.interimSourceGroups
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.sourceFilterUsesSelect
import org.viptv.app.features.sources.sourceProviderOptions
import org.viptv.app.features.sources.sourceQualityOptions
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.test.*

/**
 * app#59: on the web and desktop source picker a Quality or Source filter with more
 * than three options ("All" included) is the shared searchable dropdown (app#57);
 * three or fewer stay chips. Picking through the dropdown filters exactly like the chip.
 */
class SourceFilterSelectTest {
    private fun source(id: String, height: Long, provider: String) = SourceCandidate(id, "addon:$provider", "Stream $id",
        detail = "Release $id", quality_height = height, presentation = SourcePresentation(provider_name = provider, source_id = provider))

    /** 4K ×2, 1080p ×3, 720p, 480p from two add-ons: five quality options and three source options. */
    private val mixed = listOf(source("a1", 2160, "Torrentio"), source("a2", 1080, "Torrentio"), source("a3", 1080, "Comet"),
        source("a4", 720, "Comet"), source("a5", 2160, "Comet"), source("a6", 1080, "Torrentio"), source("a7", 480, "Torrentio"))

    @Test fun theSourcePickerSwitchesAboveThreeOptionsWhateverTheLabelLength() {
        fun options(vararg labels: String) = labels.map { SelectOption(it, it) }
        assertFalse(sourceFilterUsesSelect(options("All", "1080p", "720p")), "three options stay chips")
        assertTrue(sourceFilterUsesSelect(options("All", "4K", "1080p", "720p")), "four options use the dropdown")
        assertFalse(sourceFilterUsesSelect(options("All sources", "Torrentio 4K RD+ (long add-on name)", "MediaFusion | ElfHosted")),
            "long add-on names alone never force the dropdown; the chips scroll")
        assertEquals(listOf("4K" to "2", "1080p" to "3", "720p" to "1", "480p" to "1"), sourceQualityOptions(mixed).map { it.label to it.trailing })
        assertEquals(listOf("Torrentio" to "4", "Comet" to "3"), sourceProviderOptions(mixed).map { it.label to it.trailing })
    }

    @Test fun threeOptionsKeepTheChipsOnThePointerLayout() {
        val few = listOf(source("b1", 1080, "Torrentio"), source("b2", 720, "Comet"))
        Picker(few, pointer = true).use {
            assertNull(it.triggers().firstOrNull(), "no dropdown: ${it.triggers()}")
            assertTrue(it.clickable("All 2") && it.clickable("1080p 1") && it.clickable("720p 1"))
            assertTrue(it.clickable("All sources") && it.clickable("Torrentio") && it.clickable("Comet"))
        }
    }

    @Test fun fourOrMoreOptionsBecomeDropdownsSideBySide() {
        val three = mixed + source("a8", 1080, "MediaFusion")
        Picker(three, pointer = true).use {
            assertEquals(listOf("Quality: All", "Source: All sources"), it.triggers().map(it::text))
            val (quality, provider) = it.triggers()
            assertEquals(quality.boundsInRoot.top, provider.boundsInRoot.top, "both dropdowns share one line")
            assertFalse(it.clickable("All 8"), "the quality chips are gone")
        }
    }

    @Test fun typingNarrowsTheQualityDropdownAndPickingFiltersLikeTheChip() {
        val groups = interimSourceGroups(mixed)
        val chipped = Picker(mixed, pointer = false, groups = groups).use {
            assertNull(it.triggers().firstOrNull(), "touch layouts keep the chips")
            it.press("1080p 3")
            it.streams()
        }
        val selected = Picker(mixed, pointer = true, groups = groups).use {
            assertEquals(listOf("Quality: All"), it.triggers().map(it::text), "two add-ons plus All stay chips")
            assertTrue(it.clickable("All sources"))
            it.open("Quality: All")
            assertEquals(listOf("All", "4K", "1080p", "720p", "480p"), it.options())
            it.type("1080")
            assertEquals(listOf("1080p"), it.options())
            assertTrue(it.texts().contains("3"), "the option shows its count")
            it.pick("1080p")
            assertEquals(listOf("Quality: 1080p"), it.triggers().map(it::text))
            assertTrue(it.texts().contains("BEST MATCHES"), "the Best matches group survives the filter")
            it.streams()
        }
        assertEquals(listOf("Stream a2", "Stream a3", "Stream a6"), selected.sorted())
        assertEquals(chipped, selected, "the dropdown and the chip filter the list the same way")
    }

    @Test fun theSourceDropdownFiltersByAddOnAndAllClearsIt() {
        val four = mixed + source("a8", 1080, "MediaFusion") + source("a9", 720, "Orion")
        Picker(four, pointer = true, height = 1600).use {
            it.open("Source: All sources")
            assertEquals(listOf("All sources", "Torrentio", "Comet", "MediaFusion", "Orion"), it.options())
            it.type("medi")
            it.pick("MediaFusion")
            assertEquals(listOf("Stream a8"), it.streams())
            it.open("Source: MediaFusion")
            it.pick("All sources")
            assertEquals(9, it.streams().size)
        }
    }

    @Test fun tvKeepsItsOwnFilters() {
        Picker(mixed + source("a8", 1080, "MediaFusion"), pointer = true, television = true, width = 1920, height = 1080).use {
            assertNull(it.triggers().firstOrNull(), "TV has no dropdowns")
        }
    }

    private class Picker(sources: List<SourceCandidate>, pointer: Boolean, television: Boolean = false, width: Int = 1440, height: Int = 900,
        groups: org.viptv.app.connection.SourceGroups = org.viptv.app.connection.SourceGroups()) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SourcesScreen(null, sources, false, null, {}, {}, television = television, groups = groups)
            } }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
        fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun texts() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        fun triggers() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }.sortedBy { it.boundsInRoot.left }
        fun clickable(label: String) = nodes().any { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        /** The source rows on screen, top to bottom, by their release line ("Release a2" is "Stream a2"). */
        fun streams() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .filter { n -> text(n).matches(Regex("Release a\\d+")) }
            .sortedBy { it.boundsInRoot.top }.map { text(it).replace("Release", "Stream") }
        private fun rows() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }.sortedBy { it.boundsInRoot.top }
        fun options() = rows().map { it.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text }

        fun press(label: String) {
            assertTrue(nodes().first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        fun open(label: String) {
            val trigger = triggers().single { text(it) == label }
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
            assertTrue(nodes().any { it.config.contains(SemanticsActions.SetText) }, "the panel opened")
        }
        fun type(query: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            frames(10)
        }
        fun pick(label: String) {
            val row = rows().single { it.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text == label }
            assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        override fun close() = scene.close()
    }
}
