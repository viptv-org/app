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
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * Every Details layout opens a series on the season of the episode the viewer is
 * continuing and brings that episode into view (app#40): phone, tablet, desktop,
 * browser and the TV shelf. A season that only arrives on a later page is selected
 * when it lands; a viewer who picks a season or scrolls first keeps their choice; a
 * series with nothing to continue keeps the first numbered season.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsResumeLayoutsTest {
    private val show = "tt5607616"
    private val origin = MetadataOrigin("stremio", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92")
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Re: Zero", badges = emptyList(), metadata_origin = origin)
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number")
    private fun label(season: Long, number: Long) = "$number. Episode $season-$number"
    /** Home's Continue Watching card for S3 E5: an imported `library` card with no metadata scope. */
    private val resume = Card(MediaIdentity("$show:3:5", "series", emptyList(), EpisodeCoordinates("aired", 3, 5)),
        provider_id = "library", title = "Re: Zero", subtitle = "S3 E5 · 9m left", badges = emptyList(), progress = 0.4,
        parent_identity = MediaIdentity(show, "series", emptyList()))
    /** The first Details page: season 1 only, with the host's season index. */
    private val firstPage = DetailSnapshot(series, episodes = (1L..25L).map { episode(1, it) }, next_cursor = "25",
        seasons = listOf(EpisodeSeason(1, 25, 0), EpisodeSeason(2, 24, 25), EpisodeSeason(3, 16, 49)))
    private fun withSeason(detail: DetailSnapshot, season: Long, count: Long) =
        detail.copy(episodes = detail.episodes + (1L..count).map { episode(season, it) })

    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

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
        /** The node showing [text]; the desktop row shows an episode's number and title as separate texts. */
        fun node(text: String) = nodes().firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any {
            it.text == text || it.text == text.substringAfter(". ") && text.matches(Regex("[0-9]+\\. .*")) } }
        fun click(text: String) = assertTrue(nodes().first { node -> node.config.contains(SemanticsActions.OnClick) &&
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }.config[SemanticsActions.OnClick].action!!.invoke())
        fun top(text: String) = assertNotNull(node(text), "$text is composed (${width}x$height): " + nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { it.text }?.let { "$it@${node.boundsInRoot.top}" } }).boundsInRoot.top
        suspend fun settle() { frames(); delay(200); frames() }
        override fun close() = scene.close()
    }

    /** The episode is the list's first visible item, just under the phone's floating back bar (48 dp). */
    private fun assertBelowBackBar(text: String, top: Float) = assertTrue(top in 48f..72f, "$text is first in the list below the back bar, not at ${top}px")

    @Test fun phoneOpensOnTheContinuedSeasonOnceItsPageArrivesAndScrollsToTheEpisode() = onEdt {
        Details(390, 844, firstPage).use { page ->
            page.settle()
            assertNotNull(page.node("Season 3"), "season 3 is selected while its episodes load")
            assertEquals(listOf(49L), page.pages, "the selected season loads through Details paging")
            page.detail = withSeason(firstPage, 3, 16)
            page.settle()
            assertBelowBackBar(label(3, 5), page.top(label(3, 5)))
            assertNotNull(page.nodes().firstOrNull { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) &&
                it.config[SemanticsProperties.ProgressBarRangeInfo].current == 0.4f }, "the continued episode is marked with its progress")
        }
    }

    @Test fun aSeasonMissingFromTheIndexIsSelectedWhenALaterPageBringsIt() = onEdt {
        // An older host without a season index: Details only learn of season 3 from a later page.
        val legacy = firstPage.copy(seasons = emptyList())
        Details(390, 844, legacy).use { page ->
            page.settle()
            assertNotNull(page.node("Season 1"))
            assertEquals(listOf(25L), page.pages, "the legacy cursor keeps paging")
            page.detail = withSeason(withSeason(legacy, 2, 24), 3, 16).copy(next_cursor = null)
            page.settle()
            // The picker has scrolled away above the episode; only season 3 holds "Episode 3-5".
            assertBelowBackBar(label(3, 5), page.top(label(3, 5)))
        }
    }

    @Test fun theViewersOwnSeasonChoiceIsNotOverridden() = onEdt {
        Details(390, 844, firstPage).use { page ->
            page.settle()
            assertNotNull(page.node("Season 3"))
            // The viewer opens the season sheet and picks season 1 before season 3 arrives.
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
            assertNotNull(page.node("Season 1"))
            assertNull(page.node(label(3, 5)))
            assertNotNull(page.node("Re: Zero"), "the hero is still on screen: nothing scrolled")
        }
    }

    @Test fun tabletDesktopAndBrowserLayoutsLandOnTheContinuedEpisode() = onEdt {
        for ((name, details) in listOf(
            "tablet" to { Details(1280, 800, firstPage, tablet = true) },
            "desktop" to { Details(1280, 800, firstPage) },
            "browser" to { Details(1280, 900, firstPage, browser = true) })) {
            details().use { page ->
                page.settle()
                assertEquals(listOf(49L), page.pages, "$name: season 3 loads through Details paging")
                page.detail = withSeason(firstPage, 3, 16)
                page.settle()
                val top = page.top(label(3, 5))
                // Tablet and desktop lists start under 40 px and 16 px of padding; the browser dialog under its 32 px inset.
                assertTrue(top in 0f..80f, "$name: S3 E5 is the first item in its list, not ${top}px down")
            }
        }
    }

    @Test fun tvShelfOpensOnTheContinuedSeasonAndFocusLandsOnTheEpisode() = onEdt {
        Details(1920, 1080, withSeason(firstPage, 3, 16), television = true).use { page ->
            page.settle()
            assertNotNull(page.node("Season 3"))
            assertNotNull(page.node(label(3, 5)), "S3 E5 is placed in the shelf")
            // Down from the hero enters the shelf on the continued episode.
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
            page.scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp))
            page.settle()
            val focused = page.nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
                .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } }
            assertTrue(focused.any { it.contains(label(3, 5)) }, "focus is on S3 E5, not $focused")
        }
    }
}
