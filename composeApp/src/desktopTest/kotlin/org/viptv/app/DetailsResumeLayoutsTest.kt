package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlinx.coroutines.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsResumeLayoutsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val show = "tt5607616"
    private val origin = MetadataOrigin("stremio", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92")
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Re: Zero", badges = emptyList(), metadata_origin = origin)
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number")
    private fun label(season: Long, number: Long) = "$number. Episode $season-$number"
    private val resume = Card(MediaIdentity("$show:3:5", "series", emptyList(), EpisodeCoordinates("aired", 3, 5)),
        provider_id = "library", title = "Re: Zero", subtitle = "S3 E5 · 9m left", badges = emptyList(), progress = 0.4,
        parent_identity = MediaIdentity(show, "series", emptyList()))
    private val firstPage = DetailSnapshot(series, episodes = (1L..25L).map { episode(1, it) }, next_cursor = "25",
        seasons = listOf(EpisodeSeason(1, 25, 0), EpisodeSeason(2, 24, 25), EpisodeSeason(3, 16, 49)))
    private fun withSeason(detail: DetailSnapshot, season: Long, count: Long) =
        detail.copy(episodes = detail.episodes + (1L..count).map { episode(season, it) })

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Details(val width: Int, val height: Int, initial: DetailSnapshot, val continuing: List<Card> = listOf(resume),
        val tablet: Boolean = false, val browser: Boolean = false, val television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(initial)
        val pages = mutableListOf<Long>()
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television, tablet = tablet,
                    browser = browser, loadPage = { pages += it }, continuing = continuing)
            }
        } }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun node(text: String) = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
            it.text == text || it.text == text.substringAfter(". ") && text.matches(Regex("[0-9]+\\. .*")) } }
        fun exact(text: String) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }
        fun click(text: String) = assertTrue(nodes().first { node -> node.config.contains(SemanticsActions.OnClick) &&
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.config[SemanticsActions.OnClick].action!!.invoke())
        fun top(text: String) = assertNotNull(node(text), "$text is composed (${width}x$height): " + nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }?.let { "$it@${node.boundsInRoot.top}" } }).boundsInRoot.top
        suspend fun settle(pages: Int = 0) {
            frames(); delay(200); frames()
            val deadline = System.nanoTime() + 10_000_000_000L
            while (this.pages.size < pages && System.nanoTime() < deadline) { frames(1); delay(20) }
        }
        override fun close() = scene.close()
    }

    @Test fun phoneOpensOnTheContinuedSeasonAndStaysAtTheTop() = onEdt {
        Details(390, 844, firstPage).use { page ->
            page.settle(1)
            assertEquals(listOf(49L), page.pages, "the continued season loads through Details paging")
            val heroTop = page.top("Re: Zero")
            page.detail = withSeason(firstPage, 3, 16)
            page.settle()
            assertNotNull(page.exact("Resume S3 E5"), "the hero resumes the continued episode")
            assertNull(page.exact(label(3, 5)), "the episode list is not scrolled into view")
            assertTrue(kotlin.math.abs(page.top("Re: Zero") - heroTop) <= 1f, "the hero stays at the top when the season arrives")
        }
    }

    @Test fun aSeasonMissingFromTheIndexIsSelectedWhenALaterPageBringsIt() = onEdt {
        val legacy = firstPage.copy(seasons = emptyList())
        Details(390, 844, legacy).use { page ->
            page.settle(1)
            assertEquals(listOf(25L), page.pages, "the legacy cursor keeps paging")
            page.detail = withSeason(withSeason(legacy, 2, 24), 3, 16).copy(next_cursor = null)
            page.settle()
            assertNotNull(page.exact("Resume S3 E5"), "the page names the continued episode once its season arrives")
            assertNull(page.exact(label(3, 5)), "the list does not jump to S3 E5")
        }
    }

    @Test fun theViewersOwnSeasonChoiceIsNotOverridden() = onEdt {
        Details(390, 844, firstPage).use { page ->
            page.settle()
            assertNotNull(page.node("Season 3"))
            page.click("Season 3"); page.settle()
            page.click("Season 1"); page.settle()
            assertEquals(listOf(49L), page.pages, "season 1 is already loaded")
            page.detail = withSeason(firstPage, 3, 16)
            page.settle()
            assertNotNull(page.node(label(1, 1)), "season 1 stays: ${page.nodes().size}")
            assertNull(page.node(label(3, 5)), "the list does not jump to season 3")
        }
    }

    @Test fun aViewerWhoScrollsBeforeTheSeasonArrivesKeepsTheirPlace() = onEdt {
        val legacy = firstPage.copy(seasons = emptyList())
        Details(390, 844, legacy).use { page ->
            page.settle()
            page.scene.sendPointerEvent(PointerEventType.Scroll, Offset(195f, 600f), scrollDelta = Offset(0f, 4f))
            page.settle()
            page.detail = withSeason(withSeason(legacy, 2, 24), 3, 16).copy(next_cursor = null)
            page.settle()
            assertNotNull(page.node("Season 1"), "the season the viewer is reading stays")
            assertNull(page.node(label(3, 5)), "the list does not jump to S3 E5")
        }
    }

    @Test fun aSeriesWithNothingToContinueKeepsTheFirstNumberedSeasonAtTheTop() = onEdt {
        Details(390, 844, withSeason(firstPage, 3, 16), continuing = emptyList()).use { page ->
            page.settle()
            assertNotNull(page.node(label(1, 1)))
            assertNull(page.node(label(3, 5)))
            assertNotNull(page.node("Re: Zero"), "the hero is still on screen: nothing scrolled")
        }
    }

    @Test fun tabletDesktopAndBrowserLayoutsOpenAtTheTop() = onEdt {
        for ((name, details) in listOf(
            "tablet" to { Details(1280, 800, firstPage, tablet = true) },
            "desktop" to { Details(1280, 800, firstPage) },
            "browser" to { Details(1280, 900, firstPage, browser = true) })) {
            details().use { page ->
                page.settle(1)
                assertEquals(listOf(49L), page.pages, "$name: the continued season loads through Details paging")
                page.detail = withSeason(firstPage, 3, 16)
                page.settle()
                assertNotNull(page.exact("Resume S3 E5"), "$name: the hero names the continued episode")
                val top = if (name == "tablet") page.exact(label(3, 1)) else page.exact("Re: Zero")
                assertNotNull(top, "$name: the layout opens at its top rather than at the continued episode")
            }
        }
    }

    @Test fun tvOpensOnTheHeroOfTheContinuedSeasonAndDownReachesTheEpisode() = onEdt {
        Details(1920, 1080, firstPage, television = true).use { page ->
            page.settle(1)
            assertNotNull(page.node("Season 3"), "the shelf selects season 3 before its episodes arrive")
            assertEquals(listOf(49L), page.pages, "season 3 loads through Details paging")
            page.detail = withSeason(firstPage, 3, 16)
            page.settle()
            assertNotNull(page.exact("Resume S3 E5"), "the hero resumes the continued episode")
            val title = assertNotNull(page.exact("Re: Zero"), "the hero title is on screen")
            assertTrue(title.boundsInRoot.top < 400f, "the page opens on the hero: ${title.boundsInRoot}")
            val season = assertNotNull(page.exact("Season 3"), "the shelf keeps the continued season selected")
            assertTrue(season.boundsInRoot.top > 800f, "the episode shelf starts below the hero: ${season.boundsInRoot}")
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp))
            page.settle()
            val focused = page.nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
                .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } }
            assertTrue(focused.any { it.contains(label(3, 5)) }, "Down from the hero reaches S3 E5, not $focused")
        }
    }
}
