@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.components.PAGE_STATE_TAG
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.AppliedChip
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.features.browse.CATALOG_CLEAR_ALL
import org.viptv.app.features.browse.CATALOG_FAILED_FALLBACK
import org.viptv.app.features.browse.CATALOG_NO_TITLES_TITLE
import org.viptv.app.features.browse.CATALOG_RETRY
import org.viptv.app.features.browse.CATALOG_FILTERED_TITLE
import org.viptv.app.features.browse.CatalogActionKind
import org.viptv.app.features.browse.catalogEmptyState
import org.viptv.app.features.browse.catalogFailureState
import org.viptv.app.features.browse.catalogNoun
import org.viptv.app.features.browse.catalogStateSource
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.engine.wire.QueryValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The catalog pages' empty and failed states come from one copy.md-backed state, so web, desktop and TV can no longer
// blame filters that are not set, count "all 1 filters", or offer a Clear all that clears nothing.
class CatalogEmptyStateTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val label = "Popular"
    private val source = "Popular from VIPTV Sources"
    private val nouns = listOf("movies", "series", "anime", "titles")

    private fun chip(value: String, multi: Boolean = true) = AppliedChip("genre", value, value.replaceFirstChar(Char::uppercase), multi)

    private fun controls(peers: Int = 2) = DiscoveryControls(provider_id = "stremio", media_types = listOf("movie"), controls = emptyList(),
        catalogs = listOf(DiscoveryCatalog("popular", "Popular", "movie", connection_id = "c1", addon_id = 1,
            controls = listOf(QueryControl("genre", "Genre", ControlKind.MULTICHOICE, false,
                listOf(Choice("horror", "Horror"), Choice("crime", "Crime"), Choice("drama", "Drama"))),
                QueryControl("year", "Year", ControlKind.MULTICHOICE, false,
                    listOf(Choice("2020s", "2020s"), Choice("2010s", "2010s"))))),
            DiscoveryCatalog("new", "New Releases", "movie", connection_id = "c1", addon_id = 1)).take(peers),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Available"))

    private fun query(vararg genres: String) = BrowseQuery("movie", catalog = "popular", connection_id = "c1",
        values = genres.toList().takeIf { it.isNotEmpty() }?.let { listOf(QueryValue("genre", it)) }.orEmpty())

    @Test fun nothingFilteredNeverBlamesFiltersAndOffersNothingToClear() {
        val state = catalogEmptyState(label, source, "movies", emptyList(), otherCatalog = false)
        assertEquals(CATALOG_NO_TITLES_TITLE, state.title)
        assertEquals("", state.message)
        assertEquals(emptyList(), state.actions, "nothing is filtered, so there is no clear action")
        assertFalse("filters" in state.title.lowercase())
    }

    @Test fun oneFilterReadsASingularSentenceAndItsNewestFilterCanBeRemoved() {
        val state = catalogEmptyState(label, source, "movies", listOf(chip("horror")), otherCatalog = false)
        assertEquals(CATALOG_FILTERED_TITLE, state.title)
        assertEquals("$source has no movies matching “Horror”.", state.message)
        assertFalse("all 1 filters" in state.message)
        assertEquals(listOf("Remove “Horror”"), state.actions.map { it.label }, "one filter needs no redundant clear")
        assertEquals(CatalogActionKind.Remove, state.actions.first().kind)
        assertEquals("horror", state.actions.first().chip?.value)
        assertTrue(state.actions.first().primary)
    }

    @Test fun severalFiltersReadTheCountAndRemoveTheNewest() {
        val chips = listOf(chip("horror"), chip("crime"), chip("drama"))
        val state = catalogEmptyState(label, source, "movies", chips, otherCatalog = true)
        assertEquals("$source has no movies with all 3 filters. Removing the newest one usually helps.", state.message)
        assertEquals(listOf("Remove “Drama”", CATALOG_CLEAR_ALL, "Choose another catalog"), state.actions.map { it.label })
        assertEquals("drama", state.actions.first().chip?.value)
    }

    @Test fun aLongFilterLabelKeepsAOneLineRemove() {
        val long = chip("a-very-long-filter-label-that-will-not-fit-on-the-button")
        val state = catalogEmptyState(label, source, "movies", listOf(long), otherCatalog = false)
        assertEquals("Remove filter", state.actions.first().label)
    }

    @Test fun theNounAndTheCatalogNameFallBack() {
        assertEquals("movies", catalogNoun("movie"))
        assertEquals("series", catalogNoun("series"))
        assertEquals("titles", catalogNoun("anything"))
        assertEquals("This catalog", catalogStateSource(null, null))
        assertEquals("Popular", catalogStateSource("Popular", ""))
        for (noun in nouns) {
            assertTrue(catalogEmptyState(null, null, noun, listOf(chip("horror")), false).message.startsWith("This catalog has no $noun"))
        }
    }

    @Test fun theFailureReadsTheRequestMessageOtherwiseTheDesignSystemsLine() {
        val failed = catalogFailureState(label, "The catalog timed out.", otherCatalog = false)
        assertTrue(failed.failure)
        assertEquals("Couldn’t load Popular", failed.title)
        assertEquals("The catalog timed out.", failed.message)
        assertEquals(listOf(CATALOG_RETRY), failed.actions.map { it.label })
        assertTrue(failed.actions.first().primary)
        val withoutReason = catalogFailureState(null, null, otherCatalog = true)
        assertEquals("Couldn’t load this catalog", withoutReason.title)
        assertEquals(CATALOG_FAILED_FALLBACK, withoutReason.message)
        assertEquals(listOf(CATALOG_RETRY, "Choose another catalog"), withoutReason.actions.map { it.label })
    }

    private class Frame(width: Int, height: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(): List<String> = nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        }
        fun block(): SemanticsNode? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == PAGE_STATE_TAG }
        override fun close() = scene.close()
    }

    private fun page(query: BrowseQuery, error: String? = null, peers: Int = 2): @Composable () -> Unit = {
        ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                BrowserBrowseScreen(Screen.Movies, emptyList(), controls(peers), query, busy = false, error = error,
                    more = null, open = {}, changed = { _, _ -> }, multipleChanged = { _, _ -> },
                    applyQuery = {}, retry = {})
            }
        }
    }

    @Test fun theWebPageReadsTheSharedStateAtEveryCase() = kotlinx.coroutines.runBlocking {
        val cases = listOf(
            "no-filters" to (query() to listOf(CATALOG_NO_TITLES_TITLE)),
            "one-filter" to (query("horror") to listOf(CATALOG_FILTERED_TITLE, "has no movies matching “Horror”.")),
            "three-filters" to (query("horror", "crime", "drama") to listOf("with all 3 filters")))
        val failures = mutableListOf<String>()
        for ((name, case) in cases) for (size in listOf(1440 to 900, 390 to 844)) {
            val frame = Frame(size.first, size.second, page(case.first))
            try {
                frame.settle()
                val shown = frame.texts()
                for (expected in case.second) if (shown.none { it.contains(expected) }) failures += "$name@${size.first}: missing '$expected'"
                if (shown.any { it.contains("all 1 filters") }) failures += "$name@${size.first}: reads 'all 1 filters'"
                if (name == "no-filters") {
                    if (shown.any { it == CATALOG_CLEAR_ALL }) failures += "$name@${size.first}: offers Clear all with nothing filtered"
                    if (shown.any { it.startsWith("Remove ") }) failures += "$name@${size.first}: offers Remove with nothing filtered"
                } else if (name == "three-filters" && shown.none { it == CATALOG_CLEAR_ALL }) {
                    failures += "$name@${size.first}: no Clear all"
                }
                val block = frame.block()
                if (block == null) { failures += "$name@${size.first}: not the shared page state block"; continue }
                val centre = block.boundsInRoot.left + block.boundsInRoot.width / 2
                if (kotlin.math.abs(centre - size.first / 2f) > 1f) failures += "$name@${size.first}: the block is off-centre"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "the catalog empty state on web and phone widths")
    }

    @Test fun aCatalogWithNothingToChooseOffersNoCatalogAction() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (peers in listOf(1, 2)) {
            val frame = Frame(1440, 900, page(query(), peers = peers))
            try {
                frame.settle()
                val offered = frame.texts().any { it.contains("Choose another catalog") }
                if (peers == 1 && offered) failures += "one catalog: offers Choose another catalog"
                if (peers == 2 && !offered) failures += "two catalogs: no Choose another catalog"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures)
    }

    @Test fun theWebFailureIsTheSameBlockWithTheRequestMessage() = kotlinx.coroutines.runBlocking {
        val frame = Frame(1440, 900, page(query(), error = "The catalog timed out."))
        try {
            frame.settle()
            val shown = frame.texts()
            assertTrue(shown.any { it.contains("Couldn’t load Popular") })
            assertEquals(1, shown.count { it.contains("The catalog timed out.") })
            assertTrue(shown.any { it.contains(CATALOG_RETRY) })
            assertFalse(shown.any { it.contains(CATALOG_NO_TITLES_TITLE) })
            assertTrue(frame.block() != null, "the failure is the shared block")
        } finally { frame.close() }
    }

    @Test fun render() = kotlinx.coroutines.runBlocking {
        val dir = System.getenv("VIPTV_1035_RENDER_DIR")?.let(::File)?.also { it.deleteRecursively(); it.mkdirs() }
            ?: return@runBlocking
        val cases = listOf("no-filters" to query(), "one-filter" to query("horror"),
            "three-filters" to query("horror", "crime", "drama"), "failed" to query())
        for (size in listOf(1440 to 900, 1280 to 800, 390 to 844)) for ((name, q) in cases) {
            val frame = Frame(size.first, size.second, page(q, error = if (name == "failed") "The catalog timed out." else null))
            try {
                frame.settle()
                frame.scene.render(1_000_000_000L).use { image ->
                    File(dir, "web-${size.first}-$name.png").writeBytes(image.encodeToData()!!.bytes)
                }
            } finally { frame.close() }
        }
    }
}