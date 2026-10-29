package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.BottomNavigation
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ListBottomEdgeUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private class Case(val name: String, val width: Int, val height: Int, val bar: Boolean, val content: @Composable () -> Unit)

    private val cards = (1..42).map {
        Card(MediaIdentity("list-$it", "movie", emptyList()), provider_id = "addon:1", title = "Fixture title $it", badges = emptyList())
    }
    private val seriesCards = cards.map { it.copy(identity = it.identity.copy(kind = "series")) }
    private val animeCards = cards.map { it.copy(category = "anime") }

    private fun cases() = buildList<Case> {
        val pages = listOf(Screen.Movies to cards, Screen.Series to seriesCards, Screen.Anime to animeCards, Screen.Discover to cards)
        for ((screen, pool) in pages) {
            val name = screen.name.lowercase()
            add(Case("phone $name", 393, 852, bar = true) { BrowseScreen(screen, null, false, {}, pool) })
            add(Case("tablet $name", 1024, 768, bar = false) { BrowseScreen(screen, null, false, {}, pool) })
            add(Case("web $name", 1440, 900, bar = false) {
                BrowserBrowseScreen(screen, pool, null, BrowseQuery(if (screen == Screen.Series) "series" else "movie"),
                    false, null, null, {}, { _, _ -> }, { _, _ -> }, {})
            })
        }
        add(Case("phone my list", 393, 852, bar = true) { LibraryScreen(cards, true, false, {}, {}, {}) })
        add(Case("tablet my list", 1024, 768, bar = false) { LibraryScreen(cards, true, false, {}, {}, {}) })
        add(Case("web my list", 1440, 900, bar = false) { LibraryScreen(cards, true, false, {}, {}, {}) })
    }

    @Test fun everyGridPageRunsToTheBottomEdgeAndItsLastRowClearsTheBottomBar() {
        for (case in cases()) {
            var barTop = 0f
            frame = 0
            val scene = ImageComposeScene(case.width, case.height, Density(1f)) {
                ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    Box(Modifier.fillMaxSize()) {
                        case.content()
                        if (case.bar) Box(Modifier.align(Alignment.BottomCenter).onGloballyPositioned { barTop = it.boundsInRoot().top }) {
                            BottomNavigation(Screen.Home, {})
                        }
                    }
                } }
            }
            try {
                frames(scene)
                assertNear(case.height.toFloat(), grid(nodes(scene)).boundsInRoot.bottom,
                    "${case.name}: the grid stops above the window's bottom edge")
                scrollToEnd(scene)
                val last = flatten(grid(nodes(scene))).filter { it.config.contains(SemanticsActions.OnClick) }
                    .maxByOrNull { bottom(it) } ?: fail("${case.name}: no card in the grid")
                assertTrue(bottom(last) <= case.height + .5f,
                    "${case.name}: the last row ends at ${bottom(last)}, under the window's bottom edge")
                if (case.bar) {
                    assertTrue(barTop > 0f, "${case.name}: the bottom bar was not measured")
                    assertTrue(bottom(last) <= barTop,
                        "${case.name}: the last row ends at ${bottom(last)}, under the bottom bar at $barTop")
                }
            } finally { scene.close() }
        }
    }

    private fun bottom(node: SemanticsNode) = node.positionInRoot.y + node.size.height
    private fun grid(nodes: List<SemanticsNode>) = nodes.filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical grid")
    private fun scrolled(nodes: List<SemanticsNode>) = grid(nodes).config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun scrollToEnd(scene: ImageComposeScene) {
        var before = -1f
        repeat(40) {
            val value = scrolled(nodes(scene))
            if (value <= before + .5f) return
            before = value
            grid(nodes(scene)).config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
            frames(scene, 4)
        }
    }
    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}