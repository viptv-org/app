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
import org.viptv.app.components.caughtUpCaption
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.detailSeasons
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class AnimeMergeDetailsTest {
    @get:Rule val uiThread = UiThreadRule()

    private val show = "tt2560140"
    private val origin = MetadataOrigin("stremio", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92")
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))),
        provider_id = "addon:21", title = "Attack on Titan", badges = emptyList(), metadata_origin = origin)
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number")
    private fun label(season: Long, number: Long) = "$number. Episode $season-$number"

    private val seasonIndex = listOf(
        EpisodeSeason(0, 2, 0), EpisodeSeason(1, 3, 2), EpisodeSeason(2, 3, 5),
        EpisodeSeason(3, 22, 8), EpisodeSeason(4, 3, 30))
    private val firstPage = (1L..2L).map { episode(0, it) } + (1L..3L).map { episode(1, it) } +
        (1L..3L).map { episode(2, it) }
    private val resume = Card(MediaIdentity("$show:3:5", "series", emptyList(), EpisodeCoordinates("aired", 3, 5)),
        provider_id = "library", title = "Attack on Titan", subtitle = "S3 E5 · 9m left", badges = emptyList(),
        progress = 0.4, parent_identity = MediaIdentity(show, "series", emptyList()))

    @Test fun seasonZeroIsLabelledSpecialsAndNumberedSeasonsFollow() {
        val seasons = detailSeasons(firstPage, seasonIndex)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), seasons.map { it.number })
        assertEquals("Specials", seasons.first().label)
        assertEquals("2 episodes", seasons.first().countLabel)
        assertEquals("Season 1", seasons[1].label)
        assertEquals("Season 3", seasons[3].label)
        assertEquals("22 episodes", seasons[3].countLabel)
        assertEquals("Season 4", seasons[4].label)
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Details(initial: DetailSnapshot, television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(initial)
        val pages = mutableListOf<Long>()
        val scene = ImageComposeScene(if (television) 1920 else 390, if (television) 1080 else 844, Density(1f)) {
            ViptvTheme(television) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                        loadPage = { pages += it }, continuing = listOf(resume))
                }
            }
        }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }.joinToString(" ")
        fun node(text: String) = nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }
        suspend fun settle() { frames(); delay(200); frames() }
        override fun close() = scene.close()
    }

    @Test fun openingAnAttackOnTitanDetailsPagePicksSeasonThreeEpisodeFive() = onEdt {
        Details(DetailSnapshot(series, episodes = firstPage, next_cursor = "8", seasons = seasonIndex)).use { page ->
            page.settle()
            assertNotNull(page.node("Season 3"), "the resumed season is selected: ${page.nodes().map(page::text)}")
            assertEquals(listOf(8L), page.pages, "season 3 pages from its canonical offset")
            page.detail = page.detail.copy(episodes = firstPage + (1L..22L).map { episode(3, it) }, next_cursor = null)
            page.settle()
            assertNotNull(page.node(label(3, 5)), "S3 E5 is the continued episode: ${page.nodes().map(page::text)}")
        }
    }

    @Test fun aContinueWatchingCardKeepsItsTvdbCoordinates() {
        assertEquals("S3 E5 · 9m left", resume.subtitle)
        val finished = resume.copy(badges = listOf("Watched"), subtitle = null, progress = null)
        assertEquals("S3 E5 · Watched", finished.caughtUpCaption())
        val unnumbered = finished.copy(identity = finished.identity.copy(episode = EpisodeCoordinates("aired", null, 7)))
        assertEquals("E7 · Watched", unnumbered.caughtUpCaption())
    }

    @Test fun theTvShelfKeepsSeasonZeroReachableAndLandsOnSeasonThree() = onEdt {
        Details(DetailSnapshot(series, episodes = firstPage, next_cursor = "8", seasons = seasonIndex),
            television = true).use { page ->
            page.settle()
            assertNotNull(page.node("Season 0"), "specials stay reachable as season 0: ${page.nodes().map(page::text)}")
            assertNotNull(page.node("Season 3"), "the resumed season is selected: ${page.nodes().map(page::text)}")
            assertEquals(listOf(8L), page.pages, "season 3 pages from its canonical offset")
            page.detail = page.detail.copy(episodes = firstPage + (1L..22L).map { episode(3, it) }, next_cursor = null)
            page.settle()
            assertNotNull(page.node(label(3, 5)), "S3 E5 is placed in the shelf: ${page.nodes().map(page::text)}")
        }
    }
}