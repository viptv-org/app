package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailHeroRestartLayoutsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val label = "Play from the beginning"
    private val PLAY_WITH_EPISODE = Regex("Play S[0-9]+ E[0-9]+")
    private val show = "tt0388629"
    private val series = Card(MediaIdentity(show, "series", emptyList()), provider_id = "addon:4",
        title = "One Piece", badges = emptyList(),
        landscape = "https://images.example/one-piece.jpg", poster = "https://images.example/one-piece.jpg")
    private fun episode(number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("$show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", progress = progress)
    private val partway = DetailSnapshot(series, episodes = (1L..6L).map { episode(it, if (it == 3L) 0.4 else null) },
        seasons = listOf(EpisodeSeason(1, 6, 0)))
    private val unwatched = partway.copy(episodes = (1L..6L).map { episode(it) })
    private val movie = Card(MediaIdentity("mononoke", "movie", emptyList()), provider_id = "addon:4",
        title = "Princess Mononoke", badges = emptyList(), progress = 0.4)
    private val partWayMovie = DetailSnapshot(movie, episodes = emptyList())

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Layout(val name: String, val width: Int, val height: Int, val tablet: Boolean = false,
        val browser: Boolean = false, val television: Boolean = false) {
        val sitsUnderPlay get() = name in setOf("phone", "tablet", "browser")
        fun page(opening: DetailSnapshot, playFromStart: ((Card) -> Unit)?) = Page(name, width, height, opening, tablet, browser, television, playFromStart)
    }

    private inner class Page(val name: String, width: Int, height: Int, opening: DetailSnapshot, tablet: Boolean,
        browser: Boolean, television: Boolean, playFromStart: ((Card) -> Unit)?) : AutoCloseable {
        var detail by mutableStateOf(opening)
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                    tablet = tablet, browser = browser, continuing = emptyList(), playFromStart = playFromStart)
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        fun leaves(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun labelLeft(): Float? = leaves().firstOrNull { label in texts(it) }?.boundsInRoot?.left
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun described(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        fun shown() = nodes().flatMap { node -> texts(node) + described(node) }
        fun restart(): SemanticsNode? = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && (label in texts(node) || label in described(node))
        }
        fun restartOrFail() = assertNotNull(restart(), "$name: \"$label\" is on screen: ${shown()}")
        fun playButton(): SemanticsNode? = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) &&
                texts(node).any { it.startsWith("Resume") || it == "Play" || PLAY_WITH_EPISODE.matches(it) }
        }
        override fun close() = scene.close()
    }

    private val layouts = listOf(
        Layout("phone", 390, 844),
        Layout("tablet", 1280, 800, tablet = true),
        Layout("desktop", 1280, 800),
        Layout("browser", 1280, 900, browser = true),
        Layout("tv", 1920, 1080, television = true),
    )

    @Test fun everyHeroRestartsTheEpisodeItResumes() = onEdt {
        val started = mutableListOf<Card>()
        for (layout in layouts) {
            started.clear()
            layout.page(partway) { started += it }.use { page ->
                page.settle()
                assertNotNull(page.nodes().firstOrNull { node -> page.texts(node).any { it.startsWith("Resume") } },
                    "${layout.name}: the hero resumes S1 E3")
                val restart = page.restartOrFail()
                assertTrue(restart.config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the restart takes the tap")
                assertEquals(listOf(episode(3, 0.4)), started, "${layout.name}: the restart plays the resumed episode")
            }
        }
    }

    @Test fun everyHeroRestartsAPartWayMovieFromItsOwnStart() = onEdt {
        val started = mutableListOf<Card>()
        for (layout in layouts) {
            started.clear()
            layout.page(partWayMovie) { started += it }.use { page ->
                page.settle()
                val restart = page.restartOrFail()
                assertTrue(restart.config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the restart takes the tap")
                assertEquals(listOf(movie), started, "${layout.name}: the restart plays the title from its own start")
            }
        }
    }

    @Test fun theRestartLabelStartsOnTheContentEdgeUnderPlay() = onEdt {
        for (layout in layouts) {
            layout.page(partway) {}.use { page ->
                page.settle()
                val play = assertNotNull(page.playButton(), "${layout.name}: the hero's Play is on screen: ${page.shown()}")
                val restart = page.restartOrFail()
                if (layout.sitsUnderPlay) {
                    val text = assertNotNull(page.labelLeft(), "${layout.name}: the label is drawn: ${page.shown()}")
                    assertEquals(play.boundsInRoot.left, text, 1f,
                        "${layout.name}: \"$label\" starts on the Play button's edge, not inside it")
                } else assertTrue(restart.boundsInRoot.left > play.boundsInRoot.left,
                    "${layout.name}: the restart sits beside the Play row")
            }
        }
    }

    @Test fun aTitleWithNothingToResumeOffersNoRestart() = onEdt {
        val started = mutableListOf<Card>()
        for (layout in layouts) {
            started.clear()
            layout.page(unwatched) { started += it }.use { page ->
                page.settle()
                assertNull(page.restart(), "${layout.name}: nothing to restart: ${page.shown()}")
                assertEquals(emptyList(), started, "${layout.name}: nothing started")
            }
        }
    }

    @Test fun withoutTheControllerActionNoHeroOffersRestart() = onEdt {
        for (layout in layouts) layout.page(partway, null).use { page ->
            page.settle()
            assertNull(page.restart(), "${layout.name}: no action, no restart: ${page.shown()}")
        }
    }
}