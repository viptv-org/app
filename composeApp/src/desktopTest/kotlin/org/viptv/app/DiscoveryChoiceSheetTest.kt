package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import org.viptv.app.connection.*
import org.viptv.app.features.browse.DiscoveryFilters
import org.viptv.app.features.browse.DiscoveryMultiChoiceSheet
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

/** Actual Compose input/focus checks; no host or provider request is involved. */
@OptIn(ExperimentalComposeUiApi::class)
class DiscoveryChoiceSheetTest {
    private val control = QueryControl("genre", "Genres", ControlKind.MULTICHOICE, true,
        (0 until 40).map { Choice("g$it", "Genre $it") }, maximum_selections = 2)

    @Test fun searchOnlyCatalogExposesItsRequiredInputAndAppliesTheDraftOnce() {
        val catalog = DiscoveryCatalog("search", "Search catalog", "movie", "addon:1", controls = listOf(
            QueryControl("search", "Search", ControlKind.TEXT, true, emptyList())))
        val controls = DiscoveryControls("stremio", listOf("movie"), emptyList(), catalogs = listOf(catalog),
            discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
            filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))
        val query = BrowseQuery("movie", catalog.id, catalog.connection_id)
        val applied = mutableListOf<Pair<String, String>>()
        val scene = ImageComposeScene(1100, 720, Density(1f)) {
            ViptvTheme { DiscoveryFilters(controls, query, false, { id, value -> applied += id to value }) }
        }
        try {
            val probe = Probe(scene); probe.frames(12)
            val field = probe.nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Naruto")))
            probe.frames(6); assertTrue(applied.isEmpty())
            probe.click("Apply"); assertEquals(listOf("search" to "Naruto"), applied)
            assertEquals(listOf("Search"), controls.missingRequired(query))
            assertTrue(controls.missingRequired(query.copy(search = "Naruto")).isEmpty())
        } finally { scene.close() }
    }

    @Test fun tvSheetFocusesAnEnabledSelectionOutsideTheInitialViewport() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { DiscoveryMultiChoiceSheet(control, listOf("g28", "g29"), true, 1f, {}, {}) }
        }
        try {
            val probe = Probe(scene); probe.frames(24)
            val focused = probe.nodes().filter { it.config.contains(SemanticsProperties.Focused) && it.config[SemanticsProperties.Focused] }
            assertTrue(focused.any { probe.text(it) == "Genre 28" }, "Saturated selections must receive attached, enabled focus")
        } finally { scene.close() }
    }

    @Test fun checkboxEditsStayLocalUntilOneApplyAndRespectTheProviderLimit() {
        val submitted = mutableListOf<List<String>>()
        var closed = 0
        val scene = ImageComposeScene(1100, 720, Density(1f)) {
            ViptvTheme { DiscoveryMultiChoiceSheet(control, emptyList(), false, 1f, { closed++ }, submitted::add) }
        }
        try {
            val probe = Probe(scene); probe.frames(24)
            probe.click("Genre 0"); probe.click("Genre 1"); probe.click("Genre 2")
            assertTrue(submitted.isEmpty(), "Editing selections must not request a catalog")
            probe.click("Apply", repeats = 2); probe.frames(24)
            assertEquals(listOf(listOf("g0", "g1")), submitted)
            assertEquals(1, closed)
        } finally { scene.close() }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { owner -> flatten(owner.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = if (node.config.contains(SemanticsProperties.Text))
            node.config[SemanticsProperties.Text].joinToString(" ") { it.text } else ""
        fun click(label: String, repeats: Int = 1) {
            val node = nodes().first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            val at = node.boundsInRoot.center
            repeat(repeats) {
                scene.sendPointerEvent(PointerEventType.Press, at)
                scene.sendPointerEvent(PointerEventType.Release, at)
            }
            frames(6)
        }
    }
}
