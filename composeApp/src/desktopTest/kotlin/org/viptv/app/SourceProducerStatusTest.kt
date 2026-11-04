package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableStateOf
import org.viptv.app.connection.SOURCE_FORMAT_UNSUPPORTED
import org.viptv.app.connection.SourceProducerOutcome
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.displayLabel
import org.viptv.app.features.sources.sourceProducerNotice
import org.viptv.app.features.sources.sourceProviderOptions
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.junit.Rule
import kotlin.test.*

class SourceProducerStatusTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun source(id: String, providerId: String, label: String) = SourceCandidate(id, providerId, "Stream $id",
        detail = "[Stream $id] Release $id", quality_height = 1080, presentation = SourcePresentation(provider_name = label))
    private val torrentio = source("a1", "addon:1", "Torrentio")
    private val comet = SourceProducerOutcome("addon:2", "Comet")
    private val unsupported = SourceProducerOutcome("addon:9", "Annatar", errorCode = SOURCE_FORMAT_UNSUPPORTED)
    private val failing = SourceProducerOutcome("addon:8", "Debridio", errorCode = "addon_unavailable",
        errorMessage = "The addon could not return sources. Try again or choose another addon.")

    @Test fun theProviderChoiceCarriesProducersWithNoRows() {
        val options = sourceProviderOptions(listOf(torrentio), listOf(comet, unsupported))
        assertEquals(listOf("Torrentio", "Comet", "Annatar"), options.map { it.label })
        assertEquals(listOf<String?>("1", null, null), options.map { it.trailing })
        assertEquals(options.size, options.map { it.id }.distinct().size, "each producer keeps its own filter")
    }

    @Test fun aProducerWithRowsDoesNotAddASecondChoice() {
        val options = sourceProviderOptions(listOf(torrentio), listOf(SourceProducerOutcome("addon:1", "Torrentio")))
        assertEquals(listOf("Torrentio"), options.map { it.label })
    }

    @Test fun producersWithoutAConfiguredNameStillRead() {
        assertEquals("this add-on", SourceProducerOutcome("addon:4").displayLabel())
        assertEquals("this provider", SourceProducerOutcome("iptv:2").displayLabel())
        assertEquals("this source integration", SourceProducerOutcome("vsources:5").displayLabel())
    }

    @Test fun theNoticeFollowsDiscoveryAndTheObservedOutcome() {
        assertEquals("Still checking Comet", sourceProducerNotice(comet, discoveryPending = true, hasRows = false))
        assertEquals("No playable sources from Comet", sourceProducerNotice(comet, discoveryPending = false, hasRows = false))
        assertNull(sourceProducerNotice(comet, discoveryPending = false, hasRows = true), "a producer with rows needs no explanation")
        assertEquals("Annatar returned formats this app cannot play. Choose another source.",
            sourceProducerNotice(unsupported, discoveryPending = false, hasRows = false))
        assertEquals("Annatar returned formats this app cannot play. Choose another source.",
            sourceProducerNotice(unsupported, discoveryPending = true, hasRows = true), "a settled format failure explains itself beside usable rows")
        assertEquals(failing.errorMessage, sourceProducerNotice(failing, discoveryPending = false, hasRows = false),
            "only the server-projected message is shown for other failures")
        assertNull(sourceProducerNotice(null, discoveryPending = false, hasRows = false))
    }

    @Test fun statusReadsFindingThenStillCheckingSources() = Scene().use { scene ->
        scene.render(rows = emptyList(), producers = emptyList(), pending = true)
        assertTrue(scene.allTexts().any { it == "Finding sources" }, scene.allTexts().toString())
        scene.render(rows = listOf(torrentio), producers = emptyList(), pending = true)
        assertTrue(scene.allTexts().any { it == "Still checking sources" }, scene.allTexts().toString())

        scene.render(rows = listOf(torrentio), producers = emptyList(), pending = false)
        assertFalse(scene.allTexts().any { it == "Finding sources" || it == "Still checking sources" }, scene.allTexts().toString())
        assertTrue(scene.allTexts().any { it.startsWith("1 source from") }, "the finished summary returns")
    }

    @Test fun aZeroResultProducerIsSelectableAndExplainsItself() = Scene().use { scene ->
        scene.render(rows = listOf(torrentio), producers = listOf(comet), pending = true)
        scene.openSources()
        assertTrue(scene.options().any { it.startsWith("Comet") }, scene.options().toString())
        scene.pick("Comet")
        assertTrue(scene.allTexts().any { it == "Still checking Comet" }, scene.allTexts().toString())

        scene.render(rows = listOf(torrentio), producers = listOf(comet), pending = false)
        assertTrue(scene.allTexts().any { it == "No playable sources from Comet" }, scene.allTexts().toString())
        assertFalse(scene.allTexts().any { it.startsWith("No streams found") })

        scene.render(rows = listOf(torrentio, source("a2", "addon:2", "Comet")), producers = listOf(comet), pending = false)
        assertTrue(scene.allTexts().any { it == "Stream a2" }, "the producer's own row takes over the filtered view")
        assertFalse(scene.allTexts().any { it == "No playable sources from Comet" }, scene.allTexts().toString())
    }

    @Test fun theSelectionSurvivesEventsAndClearsWhenItsProducerDisappears() = Scene().use { scene ->
        scene.render(rows = listOf(torrentio), producers = listOf(comet), pending = true)
        scene.openSources()
        scene.pick("Comet")
        assertTrue(scene.allTexts().any { it == "Still checking Comet" })

        scene.render(rows = listOf(torrentio, source("a3", "addon:1", "Torrentio")), producers = listOf(comet), pending = true)
        assertTrue(scene.allTexts().any { it == "Still checking Comet" }, "arriving rows keep the selected provider")
        assertEquals("Source: Comet", scene.triggers().map(scene::nodeText).last())

        scene.render(rows = listOf(torrentio), producers = emptyList(), pending = false)
        assertEquals("Source: All sources", scene.triggers().map(scene::nodeText).last(), "a vanished producer leaves no stale filter")
        assertTrue(scene.allTexts().any { it == "Stream a1" }, "the rows are visible again")
    }

    private class Scene : AutoCloseable {
        private var frame = 0L
        private val rows = mutableStateOf(emptyList<SourceCandidate>())
        private val producers = mutableStateOf(emptyList<SourceProducerOutcome>())
        private val pending = mutableStateOf(false)
        val scene = ImageComposeScene(1440, 900, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SourcesScreen(null, rows.value, false, null, {}, {},
                    loadingMore = pending.value, producers = producers.value)
            } }
        }

        fun render(rows: List<SourceCandidate>, producers: List<SourceProducerOutcome>, pending: Boolean) {
            this.rows.value = rows
            this.producers.value = producers
            this.pending.value = pending
            frames(30)
        }

        private fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
        fun nodeText(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun allTexts() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        fun triggers() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            .sortedBy { it.boundsInRoot.left }
        fun options() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
            .map { it.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { text -> text.text } }

        fun openSources() {
            val trigger = triggers().single { nodeText(it) == "Source: All sources" }
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
            assertTrue(options().isNotEmpty(), "the source panel opened")
        }

        fun pick(label: String) {
            val row = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                .first { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.startsWith(label) } }
            assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }

        override fun close() = scene.close()
    }
}