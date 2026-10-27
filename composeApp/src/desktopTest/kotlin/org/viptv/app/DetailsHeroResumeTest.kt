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
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.detailExtras
import org.viptv.app.features.details.detailResume
import org.viptv.app.features.details.heroEpisode
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlinx.coroutines.*
import org.junit.Rule
import java.io.File
import kotlin.test.*

/**
 * Every Details hero plays the episode its episode list lands on and marks (app#49):
 * the title's Continue Watching episode, else a loaded episode with partial progress.
 * An imported Continue Watching episode past the loaded pages plays in the Details'
 * scope, exactly as a Continue Watching tap would, never as S1 E1.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsHeroResumeTest {
    @get:Rule val uiThread = UiThreadRule()
    private val show = "tt5607616"
    private val origin = MetadataOrigin("stremio", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92")
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Re: Zero", badges = emptyList(), metadata_origin = origin)
    private fun episode(season: Long, number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $season-$number", progress = progress)
    /** Home's Continue Watching card for S3 E5: an imported `library` card with no metadata scope. */
    private val resume = Card(MediaIdentity("$show:3:5", "series", emptyList(), EpisodeCoordinates("aired", 3, 5)),
        provider_id = "library", title = "Re: Zero", subtitle = "S3 E5 · 9m left", badges = emptyList(), progress = 0.4,
        parent_identity = MediaIdentity(show, "series", emptyList()))
    /** The first Details page: season 1 only, with an older partly watched S1 E3, and the host's season index. */
    private val firstPage = DetailSnapshot(series, episodes = (1L..25L).map { episode(1, it, progress = if (it == 3L) 0.3 else null) },
        next_cursor = "25", seasons = listOf(EpisodeSeason(1, 25, 0), EpisodeSeason(2, 24, 25), EpisodeSeason(3, 16, 49)))

    /** [card] is S3 E5 in the Details' own scope, as the source selector and player need it. */
    private fun assertScopedResume(card: Card?, layout: String) {
        assertNotNull(card, "$layout: the hero played something")
        assertEquals(resume.identity, card.identity, "$layout: the hero plays S3 E5, not ${card.identity.id}")
        assertEquals(series.provider_id, card.provider_id, "$layout: in the Details' provider")
        assertEquals(origin, card.metadata_origin, "$layout: in the Details' metadata scope")
        assertEquals(series.identity, card.parent_identity, "$layout: under this title")
    }

    @Test fun anOffPageContinueWatchingEpisodeIsTheHerosEpisodeInTheDetailsScope() {
        val played = detailResume(firstPage, listOf(resume)).heroEpisode(firstPage)
        assertScopedResume(played, "off page")
        assertEquals(0.4, played!!.progress, "it keeps its Continue Watching progress")
    }

    @Test fun aLoadedContinueWatchingEpisodeIsPlayedAsItsOwnPageCardMarkedWithItsProgress() {
        val loaded = firstPage.copy(episodes = firstPage.episodes + (1L..16L).map { episode(3, it) })
        assertEquals(episode(3, 5, progress = 0.4), detailResume(loaded, listOf(resume)).heroEpisode(loaded))
    }

    @Test fun withoutContinueWatchingTheLoadedPartlyWatchedEpisodeStaysTheHerosEpisode() {
        assertEquals(episode(1, 3, progress = 0.3), detailResume(firstPage, emptyList()).heroEpisode(firstPage))
        val unwatched = firstPage.copy(episodes = (1L..25L).map { episode(1, it) })
        assertNull(detailResume(unwatched, emptyList()).heroEpisode(unwatched), "nothing to continue: the hero plays the title")
    }

    @Test fun tvHeroExtrasResumeTheContinueWatchingEpisode() {
        val extras = detailExtras(firstPage, listOf(resume))
        assertScopedResume(extras.resume, "tv")
        assertEquals("Resume S3 E5", extras.resumeLabel)
        assertEquals("Resume S1 E3", detailExtras(firstPage, emptyList()).resumeLabel)
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Details(val width: Int, val height: Int, initial: DetailSnapshot, val continuing: List<Card> = listOf(resume),
        val tablet: Boolean = false, val browser: Boolean = false, val television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(initial)
        val played = mutableListOf<Card>()
        val fromStart = mutableListOf<Card>()
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = { played += it }, television = television, tablet = tablet,
                    browser = browser, continuing = continuing, playFromStart = { fromStart += it })
            }
        } }
        private var frame = 0L
        suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun clickable(text: String) = nodes().firstOrNull { node -> node.config.contains(SemanticsActions.OnClick) && text in texts(node) }
        fun click(text: String) {
            val node = assertNotNull(clickable(text), "\"$text\" is a button (${width}x$height): " + nodes().flatMap(::texts))
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
        suspend fun settle() { frames(); delay(200); frames() }
        /** `VIPTV_DETAILS_HERO_CAPTURE_DIR` saves what the hero shows. */
        fun save(name: String) {
            val dir = System.getenv("VIPTV_DETAILS_HERO_CAPTURE_DIR")?.let(::File) ?: return
            dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }

    private inner class Layout(val name: String, val width: Int, val height: Int, val tablet: Boolean = false,
        val browser: Boolean = false, val television: Boolean = false) {
        fun details(detail: DetailSnapshot = firstPage, continuing: List<Card> = listOf(resume)) =
            Details(width, height, detail, continuing, tablet, browser, television)
    }
    private val layouts = listOf(Layout("phone", 390, 844), Layout("tablet", 1280, 800, tablet = true), Layout("desktop", 1280, 800),
        Layout("browser", 1280, 900, browser = true), Layout("tv", 1920, 1080, television = true))

    @Test fun everyLayoutsHeroResumesTheContinuedEpisodeBeforeItsPageLoads() = onEdt {
        for (layout in layouts) layout.details().use { page ->
            page.settle()
            // Season 3 is not loaded: the older S1 E3 on the first page must not take over.
            assertNull(page.clickable("Resume S1 E3"), "${layout.name}: the hero does not resume S1 E3")
            page.save("hero-resume-${layout.name}")
            page.click("Resume S3 E5")
            page.settle()
            assertEquals(1, page.played.size, "${layout.name}: one play")
            assertScopedResume(page.played.single(), layout.name)
        }
    }

    @Test fun desktopSourceAndRestartActionsUseTheContinuedEpisode() = onEdt {
        Details(1280, 800, firstPage).use { page ->
            page.settle()
            page.click("Best match")
            assertScopedResume(page.played.single(), "desktop source pill")
            page.click("Play from the beginning")
            assertScopedResume(page.fromStart.single(), "desktop restart")
        }
    }

    @Test fun aContinuedEpisodeWithoutProgressIsPlayedByName() = onEdt {
        // A Calendar row's episode (app#46) names the episode Details land on but has no progress.
        val named = resume.copy(progress = null, subtitle = null)
        for (layout in layouts) layout.details(continuing = listOf(named)).use { page ->
            page.settle()
            assertNull(page.clickable("Resume S3 E5"), "${layout.name}: nothing to resume")
            assertNull(page.clickable("Play from the beginning"), "${layout.name}: no restart")
            assertNull(page.clickable("Restart"), "${layout.name}: no restart")
            page.save("hero-named-${layout.name}")
            page.click("Play S3 E5")
            page.settle()
            assertEquals(resume.identity, page.played.single().identity, "${layout.name}: the hero plays S3 E5")
        }
    }

    @Test fun aSeriesWithNothingToContinuePlaysTheTitle() = onEdt {
        val unwatched = firstPage.copy(episodes = (1L..25L).map { episode(1, it) })
        for (layout in layouts) layout.details(unwatched, continuing = emptyList()).use { page ->
            page.settle()
            page.click("Play")
            page.settle()
            assertEquals(listOf(series), page.played, "${layout.name}: the title card, which sources resolve to its first episode")
        }
    }
}
