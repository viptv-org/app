package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.live.LiveFilters
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

/**
 * Horizontal rows run edge to edge (app#63): the scrolling strip spans the whole
 * screen, its first card rests on the page gutter, a card runs under the right
 * screen edge instead of being clipped at the gutter, and the row's title and
 * arrows stay inset by the gutter.
 */
@OptIn(ExperimentalComposeUiApi::class)
class EdgeToEdgeRowsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private class Case(val name: String, val width: Int, val gutter: Int, val title: String, val prefix: String,
        val arrows: Boolean = false, val content: @Composable () -> Unit)

    private fun cards(prefix: String, count: Int = 14) = (1..count).map {
        Card(MediaIdentity("$prefix$it", "movie", emptyList()), provider_id = "addon:1", title = "$prefix title $it", badges = emptyList())
    }
    private fun home(prefix: String, count: Int = 14) = HomeSnapshot(Scope("server", "owner", "12", 1), 1,
        rows = listOf(HomeRow(id = "top", title = "Popular", provider_id = "addon:1", state = RowState.READY, cards = cards(prefix, count))),
        complete = true)
    private fun detail(prefix: String) = DetailSnapshot(
        Card(MediaIdentity("film", "movie", emptyList()), provider_id = "addon:1", title = "Fixture film", badges = emptyList()),
        recommendations = cards(prefix), episodes = emptyList())
    private fun search(prefix: String) = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = cards(prefix)),
        SearchResultRow("addon:2", "Second provider", "stremio", addon_id = 2, cards = cards("Other")))

    private val cases = listOf(
        Case("Home desktop", 1440, 40, "Popular", "Home", arrows = true) { HomeScreen(home("Home"), false, {}, {}) },
        Case("Home web", 1440, 57, "Popular", "Web", arrows = true) { HomeScreen(home("Web"), false, {}, {}, browser = true) },
        Case("Home phone", 390, 16, "Popular", "Phone") { HomeScreen(home("Phone"), false, {}, {}) },
        Case("Details desktop", 1440, 40, "More like this", "Related") { DetailsScreen(detail("Related"), false, null, {}, {}) },
        Case("Details phone", 390, 16, "More like this", "Similar") { DetailsScreen(detail("Similar"), false, null, {}, {}) },
        Case("Search desktop", 1440, 40, "First provider", "Found") {
            SearchScreen("fixture", emptyList(), emptyList(), false, null, true, {}, {}, editQuery = {}, searchRows = search("Found"))
        },
        Case("Search phone", 390, 16, "First provider", "Match") {
            SearchScreen("fixture", emptyList(), emptyList(), false, null, false, {}, {}, editQuery = {}, searchRows = search("Match"))
        },
    )

    @Test fun rowsScrollToTheScreenEdgeWithTheFirstCardAndTheChromeOnTheGutter() {
        for (case in cases) {
            val scene = scene(case.width, 2400, case.content)
            try {
                frames(scene)
                val nodes = nodes(scene)
                val first = card(nodes, "${case.prefix} title 1") ?: fail("${case.name}: no first card")
                val strip = nodes.filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                    .single { strip -> flatten(strip).any { it.id == first.id } }
                assertNear(0f, strip.boundsInRoot.left, "${case.name}: the strip starts at the screen edge")
                assertNear(case.width.toFloat(), strip.boundsInRoot.right, "${case.name}: the strip ends at the screen edge")
                assertNear(case.gutter.toFloat(), first.positionInRoot.x, "${case.name}: the first card rests on the gutter")
                val edge = (2..14).mapNotNull { card(nodes, "${case.prefix} title $it") }
                    .firstOrNull { it.positionInRoot.x < case.width && it.positionInRoot.x + it.size.width > case.width }
                    ?: fail("${case.name}: no card runs under the right screen edge")
                assertNear(case.width.toFloat(), edge.boundsInRoot.right, "${case.name}: the edge card is cut by the screen, not the gutter")
                val title = nodes.first { text(it) == case.title }
                assertNear(case.gutter.toFloat(), title.boundsInRoot.left, "${case.name}: the title stays inset")
                if (case.arrows) {
                    val next = arrow(nodes, "Next items")
                    assertNear(case.width - case.gutter.toFloat(), next.boundsInRoot.right, "${case.name}: the arrows stay inset")
                }
            } finally { scene.close() }
        }
    }

    @Test fun arrowsPageAWholeScreenOfCardsAndStopAtTheTrueEnds() {
        val width = 1440; val gutter = 40
        val scene = scene(width, 1000) { HomeScreen(home("Page", 30), false, {}, {}) }
        try {
            frames(scene)
            // 176 dp cards with 12 dp gaps from a 40 dp gutter: cards 1-7 sit wholly on screen, card 8 runs under the edge.
            press(scene, "Next items")
            assertNear(gutter.toFloat(), card(nodes(scene), "Page title 8")!!.positionInRoot.x, "a page forward lands the first unseen card on the gutter")
            repeat(6) { if (!disabled(arrow(nodes(scene), "Next items"))) press(scene, "Next items") }
            assertTrue(disabled(arrow(nodes(scene), "Next items")), "forward stops at the end")
            val last = card(nodes(scene), "Page title 30")!!
            assertNear(width - gutter.toFloat(), last.positionInRoot.x + last.size.width, "the last card ends on the gutter")
            repeat(6) { if (!disabled(arrow(nodes(scene), "Previous items"))) press(scene, "Previous items") }
            assertTrue(disabled(arrow(nodes(scene), "Previous items")), "back stops at the start")
            assertNear(gutter.toFloat(), card(nodes(scene), "Page title 1")!!.positionInRoot.x, "the first card returns to the gutter")
        } finally { scene.close() }
    }

    @Test fun chipStripsScrollOutOfThePagePaddingToTheScreenEdge() {
        val width = 390; val gutter = 16
        val categories = (1..12).map { "Category $it" }
        val scene = scene(width, 400) {
            // LiveScreen's phone page: a Column padded by the gutter holding the chips.
            Column(Modifier.fillMaxSize().padding(horizontal = gutter.dp)) {
                LiveFilters(categories, "All", {}, gutter = gutter.dp)
            }
        }
        try {
            frames(scene)
            val nodes = nodes(scene)
            val first = nodes.first { it.config.contains(SemanticsActions.OnClick) && "All" in texts(it) }
            val strip = nodes.filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .single { strip -> flatten(strip).any { it.id == first.id } }
            assertNear(0f, strip.boundsInRoot.left, "the chip strip starts at the screen edge")
            assertNear(width.toFloat(), strip.boundsInRoot.right, "the chip strip ends at the screen edge")
            assertNear(gutter.toFloat(), first.positionInRoot.x, "the first chip rests on the gutter")
            val edge = nodes.filter { it.config.contains(SemanticsActions.OnClick) && texts(it).any { text -> text in categories } }
                .firstOrNull { it.positionInRoot.x < width && it.positionInRoot.x + it.size.width > width } ?: fail("no chip runs under the edge")
            assertNear(width.toFloat(), edge.boundsInRoot.right, "the edge chip is cut by the screen, not the gutter")
        } finally { scene.close() }
    }

    private fun card(nodes: List<SemanticsNode>, title: String) = nodes.firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && (title in texts(it) || title in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
    }
    private fun arrow(nodes: List<SemanticsNode>, label: String) = nodes.first { it.config.contains(SemanticsActions.OnClick) && description(it).contains(label) }
    private fun disabled(node: SemanticsNode) = node.config.contains(SemanticsProperties.Disabled)
    private fun press(scene: ImageComposeScene, label: String) {
        assertTrue(arrow(nodes(scene), label).config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene, 90)
    }
    private fun assertNear(expected: Float, actual: Float, message: String) = assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private var frame = 0L
    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
    }
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
}
