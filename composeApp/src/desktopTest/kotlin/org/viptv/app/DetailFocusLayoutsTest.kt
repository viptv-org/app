package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.junit.Rule
import org.viptv.app.connection.DetailFocus
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailFocusLayoutsTest {
    @get:Rule val uiThread = UiThreadRule()
    private val show = "tt0388629"
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))),
        provider_id = "addon:4", title = "One Piece", badges = emptyList())
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number")
    private fun label(season: Long, number: Long) = "$number. Episode $season-$number"
    private val detail = DetailSnapshot(series,
        episodes = (1L..8L).map { episode(1, it) } + (1L..10L).map { episode(4, it) },
        seasons = listOf(EpisodeSeason(1, 8, 0), EpisodeSeason(4, 22, 8)))
    private fun resume(season: Long, number: Long) = Card(
        MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        provider_id = "library", title = "One Piece", subtitle = "S$season E$number · 9m left", badges = emptyList(),
        progress = 0.4, parent_identity = MediaIdentity(show, "series", emptyList()))

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(val width: Int, val height: Int, val continuing: List<Card> = emptyList(),
        val focus: DetailFocus?, val tablet: Boolean = false, val browser: Boolean = false,
        val television: Boolean = false) : AutoCloseable {
        val pages = mutableListOf<Long>()
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                    tablet = tablet, browser = browser, loadPage = { pages += it }, continuing = continuing, focus = focus)
            }
        } }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun node(text: String) = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
            it.text == text || it.text == text.substringAfter(". ") && text.matches(Regex("[0-9]+\\. .*")) } }
        fun top(text: String) = assertNotNull(node(text), "$text is composed: " + nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }?.let { "$it@${node.boundsInRoot.top}" } }).boundsInRoot.top
        suspend fun settle(pages: Int = 0) {
            frames(); delay(200); frames()
            val deadline = System.nanoTime() + 10_000_000_000L
            while (this.pages.size < pages && System.nanoTime() < deadline) { frames(1); delay(20) }
        }
        override fun close() = scene.close()
    }

    private fun assertBelowBackBar(text: String, top: Float) =
        assertTrue(top in 48f..72f, "$text is first in the list below the back bar, not at ${top}px")

    @Test fun phoneOpensOnTheFocusSeasonAndScrollsToTheFocusEpisode() = onEdt {
        Page(390, 844, focus = DetailFocus(4, 3)).use { page ->
            page.settle(1)
            assertEquals(listOf(18L), page.pages, "the focus season pages from its canonical offset")
            assertBelowBackBar(label(4, 3), page.top(label(4, 3)))
            assertNull(page.node(label(1, 1)), "season 1 is not drawn")
        }
    }

    @Test fun aResumeInTheFocusSeasonWinsTheEpisode() = onEdt {
        Page(390, 844, continuing = listOf(resume(4, 2)), focus = DetailFocus(4, 3)).use { page ->
            page.settle()
            assertBelowBackBar(label(4, 2), page.top(label(4, 2)))
        }
    }

    @Test fun aResumeInAnotherSeasonDoesNotTakeTheFocusSeasonBack() = onEdt {
        Page(390, 844, continuing = listOf(resume(1, 5)), focus = DetailFocus(4, 3)).use { page ->
            page.settle()
            assertBelowBackBar(label(4, 3), page.top(label(4, 3)))
            assertNull(page.node(label(1, 1)), "the focus season beats the season the viewer is continuing")
        }
    }

    @Test fun desktopAndBrowserAndTabletOpenOnTheFocusSeason() = onEdt {
        for ((name, page) in listOf(
            "desktop" to { Page(1280, 800, focus = DetailFocus(4, 3)) },
            "tablet" to { Page(1280, 800, focus = DetailFocus(4, 3), tablet = true) },
            "browser" to { Page(1280, 900, focus = DetailFocus(4, 3), browser = true) })) {
            page().use { shown ->
                shown.settle(1)
                assertEquals(listOf(18L), shown.pages, "$name: the focus season pages from its canonical offset")
                assertTrue(shown.top(label(4, 3)) in 0f..80f, "$name: S4 E3 is the first item in its list")
            }
        }
    }

    @Test fun tvShelfOpensOnTheFocusSeasonAndFocusesTheEpisode() = onEdt {
        Page(1920, 1080, focus = DetailFocus(4, 3), television = true).use { page ->
            page.settle(1)
            assertNotNull(page.node("Season 4"), "the shelf selects the focus season")
            assertEquals(listOf(18L), page.pages, "the focus season pages from its canonical offset")
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp))
            page.settle()
            val focused = page.nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
                .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } }
            assertTrue(focused.any { it.contains(label(4, 3)) }, "focus is on S4 E3, not $focused")
        }
    }
}
