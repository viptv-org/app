package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.detailFacts
import org.viptv.app.features.details.groupedCount
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsInformationTest {
    @get:Rule val uiThread = UiThreadRule()
    private val description = "After a cosmic event lifts their street out of the suburbs, the Platt family " +
        "learns what their neighbours were really hiding."
    private val enriched = Card(MediaIdentity("oak-street", "series", emptyList()), provider_id = "addon:21",
        title = "The End of Oak Street", description = description, badges = listOf("4K"), metadata = CardMetadata(
            year = 2026, runtime_minutes = 44, genres = listOf("Drama", "Mystery"), rating = 8.0, rating_votes = 8_431,
            certification = "TV-MA", alternate_titles = listOf("Oak Street", "The End of Oak Street (US)")),
        metadata_origin = MetadataOrigin("simkl", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92"))
    private val metadataLine = "Series · 2026 · 44m · Drama / Mystery · ★ 8.0"
    private fun episode(season: Long, number: Long) = enriched.copy(
        identity = MediaIdentity("oak-street:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = enriched.identity, title = "Episode $season-$number", description = null, metadata = null)
    private val enrichedDetail = DetailSnapshot(enriched, episodes = (1L..10L).map { episode(1, it) },
        seasons = listOf(EpisodeSeason(1, 10, 0), EpisodeSeason(2, 8, 10)))
    private val bare = Card(MediaIdentity("brand-new-day", "movie", emptyList()), provider_id = "addon:21",
        title = "Brand New Day", description = description, badges = emptyList())
    private val bareDetail = DetailSnapshot(bare, episodes = emptyList(), recommendations =
        listOf(bare.copy(identity = MediaIdentity("obsession", "movie", emptyList()), title = "Obsession")))

    @Test fun factsAreOnlyWhatTheHeroDoesNotShow() {
        val facts = enrichedDetail.detailFacts()
        assertEquals(listOf("Seasons" to "2", "Episodes" to "18", "Votes" to "8,431",
            "Also known as" to "Oak Street · The End of Oak Street (US)"), facts.map { it.label to it.value })
        assertTrue(facts.none { it.value.contains("2026") || it.value.contains("44m") || it.value.contains("Drama") },
            "the hero's own metadata line is not a fact: $facts")
        assertTrue(facts.none { it.value == description }, "the hero's own description is not a fact: $facts")
        assertEquals(emptyList(), bareDetail.detailFacts(), "a title with nothing beyond its hero offers no Details tab")
    }

    @Test fun countsAreGroupedInThrees() {
        assertEquals("7", groupedCount(7))
        assertEquals("999", groupedCount(999))
        assertEquals("8,431", groupedCount(8_431))
        assertEquals("1,234,567", groupedCount(1_234_567))
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(initial: DetailSnapshot) : AutoCloseable {
        var detail by mutableStateOf(initial)
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {})
            }
        } }
        private var frame = 0L
        suspend fun settle() { repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }; delay(200); repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun merged() = scene.semanticsOwners.flatMap { owner ->
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            flatten(owner.rootSemanticsNode) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun shown(text: String) = nodes().count { text in texts(it) }
        fun shownTexts() = nodes().flatMap(::texts)
        fun clickable(text: String) = merged().firstOrNull { node -> node.config.contains(SemanticsActions.OnClick) && text in texts(node) }
        fun fact(label: String, value: String): Boolean {
            val row = nodes().firstOrNull { label in texts(it) && !it.config.contains(SemanticsActions.OnClick) } ?: return false
            return nodes().any { value in texts(it) && it.boundsInRoot.top == row.boundsInRoot.top && it.boundsInRoot.left > row.boundsInRoot.left }
        }
        fun tab(text: String) = assertNotNull(clickable(text), "\"$text\" is a tab: " + nodes().flatMap(::texts))
        fun open(text: String) = assertTrue(tab(text).config[SemanticsActions.OnClick].action!!.invoke())
        fun scrollToEnd() {
            val scroll = merged().firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
            assertNotNull(scroll, "the page scrolls: " + nodes().flatMap(::texts))
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
        }
        override fun close() = scene.close()
    }

    @Test fun theDetailsTabAddsFactsAndRepeatsNothingFromUnderTheArtwork() = onEdt {
        Page(enrichedDetail).use { page ->
            page.settle()
            assertEquals(1, page.shown(metadataLine), "the hero shows the metadata line once")
            assertEquals(1, page.shown(description), "the hero shows the description once")
            page.open("Details")
            page.settle()
            assertEquals(1, page.shown(metadataLine), "the Details tab does not repeat the metadata line")
            assertEquals(1, page.shown(description), "the Details tab does not repeat the description")
            page.scrollToEnd()
            page.settle()
            for (fact in listOf("Seasons" to "2", "Episodes" to "18", "Votes" to "8,431",
                "Also known as" to "Oak Street · The End of Oak Street (US)")) {
                assertTrue(page.fact(fact.first, fact.second), "the facts list shows ${fact.first} \u2192 ${fact.second}: " +
                    page.shownTexts())
            }
        }
    }

    @Test fun aTitleWithNoOtherFactsDoesNotOfferTheTab() = onEdt {
        Page(bareDetail).use { page ->
            page.settle()
            assertNull(page.clickable("Details"), "no Details tab: " + page.shownTexts())
            assertNotNull(page.clickable("More like this"), "the recommendations tab stays")
        }
    }
}
