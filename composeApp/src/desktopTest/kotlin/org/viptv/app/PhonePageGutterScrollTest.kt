package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import org.junit.Rule
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

// Every phone page keeps its 16 dp gutter while its own scroller moves. Lazy lists leave a zero-size
// semantics node for the item scrolled just past the top; those are skipped, not the content we assert.
@OptIn(ExperimentalComposeUiApi::class)
class PhonePageGutterScrollTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun card(i: Int, prefix: String) =
        Card(MediaIdentity("$prefix-$i", "movie", emptyList()), provider_id = "simkl", title = "$prefix $i", badges = emptyList())

    private fun home() = HomeSnapshot(Scope("local", "device", "one", 1), 1, rows = (1..4).map { row ->
        HomeRow(id = "row$row", title = "Trending $row", provider_id = "simkl", state = RowState.READY,
            cards = (1..30).map { card(it, "Row$row") })
    }, hero = card(0, "Hero"), complete = true)

    private fun searchRows() = (0..8).map { i ->
        SearchResultRow("addon:$i", "Provider $i", "stremio", addon_id = i.toLong(),
            cards = (1..12).map { card(it, "Provider$i") })
    }

    @Test fun phoneSearchResultsKeepTheirGutterWhileScrolling() {
        val scene = scene { SearchScreen("naruto", emptyList(), emptyList(), false, null, false, {}, {}, editQuery = {}, searchRows = searchRows()) }
        try {
            frames(scene, 30)
            scrollDown(scene, 900f)
            frames(scene, 40)
            val labels = laidOut(nodes(scene).filter { text(it).startsWith("Provider ") })
            assertTrue(labels.size >= 2, "Search: expected several provider headings, got ${labels.map(::text)}")
            labels.forEach { assertNear(16f, it.boundsInRoot.left, "Search: ${text(it)} loses its gutter") }
        } finally { scene.close() }
    }

    @Test fun phoneGridsKeepTheirGutterWhileScrolling() {
        for (case in gridCases) {
            val scene = scene { Column(Modifier.fillMaxSize()) { case.content() } }
            try {
                frames(scene, 30)
                scrollDown(scene, 900f)
                frames(scene, 40)
                val cards = nodes(scene).filter { node ->
                    node.config.contains(SemanticsActions.OnClick) &&
                        node.boundsInRoot.top in 0f..height &&
                        (text(node).contains("${case.prefix} ") ||
                            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains("${case.prefix} ") })
                }
                assertTrue(cards.isNotEmpty(), "${case.name}: no cards visible after scrolling")
                assertNear(16f, cards.minOf { it.boundsInRoot.left }, "${case.name}: the leftmost card loses its gutter")
                if (case.edgeToEdge) assertNear(width - 16f, cards.maxOf { it.boundsInRoot.right }, "${case.name}: the rightmost card loses its gutter")
            } finally { scene.close() }
        }
    }

    @Test fun homeRowsKeepTheirGutterWhileScrolling() {
        val scene = scene { Column(Modifier.fillMaxSize()) { HomeScreen(home(), false, {}, {}, busy = false) } }
        try {
            frames(scene, 30)
            scrollDown(scene, 900f)
            frames(scene, 40)
            val titles = laidOut(nodes(scene).filter {
                text(it).startsWith("Trending ") && it.boundsInRoot.top in 0f..height
            })
            assertTrue(titles.isNotEmpty(), "Home: no row titles visible after scrolling")
            titles.forEach { assertNear(16f, it.boundsInRoot.left, "Home: ${text(it)} loses its gutter") }
        } finally { scene.close() }
    }

    private data class GridCase(val name: String, val prefix: String, val edgeToEdge: Boolean, val content: @Composable () -> Unit)

    private val gridCases = listOf(
        GridCase("Browse grid", "Browse", edgeToEdge = true) {
            BrowseScreen(Screen.Movies, null, false, {}, loadedCards = (1..40).map { card(it, "Browse") }, controls = null, busy = false)
        },
        GridCase("Search grid", "Grid", edgeToEdge = false) {
            SearchScreen("naruto", (1..30).map { card(it, "Grid") }, emptyList(), false, null, false, {}, {}, editQuery = {})
        },
    )

    private fun laidOut(nodes: List<SemanticsNode>) = nodes.filter { it.boundsInRoot.width > 0f }

    private fun scrollDown(scene: ImageComposeScene, amount: Float) {
        val scroller = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller")
        scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f, amount)
    }

    private val width = 390f
    private val height = 844f
    private fun scene(content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width.toInt(), height.toInt(), Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
    }
    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")
}