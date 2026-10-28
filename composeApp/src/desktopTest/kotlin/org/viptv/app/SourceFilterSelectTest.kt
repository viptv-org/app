package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.interimSourceGroups
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.sourceProviderOptions
import org.viptv.app.features.sources.sourceQualityOptions
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.junit.Rule
import kotlin.test.*

/**
 * app#59, app#117: the source picker's Quality and Source filters are the shared dropdown
 * chips on every non-TV layout, whatever the number of options. Picking filters exactly
 * like the old chips did, and "Clear" resets both.
 */
class SourceFilterSelectTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun source(id: String, height: Long, provider: String) = SourceCandidate(id, "addon:$provider", "Stream $id",
        detail = "Release $id", quality_height = height, presentation = SourcePresentation(provider_name = provider, source_id = provider))

    /** 4K ×2, 1080p ×3, 720p, 480p from two add-ons. */
    private val mixed = listOf(source("a1", 2160, "Torrentio"), source("a2", 1080, "Torrentio"), source("a3", 1080, "Comet"),
        source("a4", 720, "Comet"), source("a5", 2160, "Comet"), source("a6", 1080, "Torrentio"), source("a7", 480, "Torrentio"))

    @Test fun optionsCarryTheirCountsInFirstSeenOrder() {
        assertEquals(listOf("4K" to "2", "1080p" to "3", "720p" to "1", "480p" to "1"), sourceQualityOptions(mixed).map { it.label to it.trailing })
        assertEquals(listOf("Torrentio" to "4", "Comet" to "3"), sourceProviderOptions(mixed).map { it.label to it.trailing })
    }

    @Test fun everyLayoutShowsTheTwoChipsEvenForAFewOptions() {
        val few = listOf(source("b1", 1080, "Torrentio"), source("b2", 720, "Comet"))
        for (pointer in listOf(true, false)) Picker(few, pointer = pointer).use {
            assertEquals(listOf("Quality: All", "Source: All sources"), it.triggers().map(it::text), "pointer=$pointer")
            assertFalse(it.clickable("All 2") || it.clickable("Torrentio"), "the chip rows are gone")
        }
        Picker(few, pointer = false, width = 390, height = 844).use {
            assertEquals(listOf("Quality: All", "Source: All sources"), it.triggers().map(it::text), "phone")
        }
    }

    @Test fun pickingAQualityFiltersTheListAndClearResetsIt() {
        val groups = interimSourceGroups(mixed)
        Picker(mixed, pointer = true, groups = groups).use {
            it.open("Quality: All")
            assertEquals(listOf("All", "4K", "1080p", "720p", "480p"), it.options())
            assertTrue(it.texts().contains("7 · Current"), "All shows the total: ${it.texts()}")
            assertTrue(it.texts().contains("3"), "an option shows its count")
            it.pick("1080p")
            assertEquals(listOf("Quality: 1080p", "Source: All sources"), it.triggers().map(it::text))
            assertTrue(it.texts().contains("BEST MATCHES"), "the Best matches group survives the filter")
            assertEquals(listOf("Stream a2", "Stream a3", "Stream a6"), it.streams().sorted())
            it.press("Clear")
            assertEquals(7, it.streams().size)
            assertEquals(listOf("Quality: All", "Source: All sources"), it.triggers().map(it::text))
        }
    }

    @Test fun theSourceDropdownSearchesManyAddOnsAndAllClearsIt() {
        val many = mixed + listOf("MediaFusion", "Orion", "Peerflix", "Jackettio", "KnightCrawler", "Debridio", "Annatar")
            .mapIndexed { index, name -> source("m$index", 1080, name) }
        Picker(many, pointer = true, height = 1600).use {
            it.open("Source: All sources")
            assertEquals("All sources", it.options().first(), "All sources is the first row")
            assertTrue(it.options().size > 4, "the nine add-ons overflow into a searchable popover")
            it.type("medi")
            it.pick("MediaFusion")
            assertEquals(listOf("Stream m0"), it.streams())
            it.open("Source: MediaFusion")
            it.pick("All sources")
            assertEquals(many.size, it.streams().size)
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
            .filter { n -> text(n).matches(Regex("Release [a-z]\\d+")) }
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
            assertTrue(rows().isNotEmpty(), "the panel opened")
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
