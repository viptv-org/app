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
import androidx.compose.ui.geometry.Rect
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
import org.viptv.engine.wire.CardMetadata
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
class TvHeroResumeBarTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val series = Card(MediaIdentity("one-piece", "series", emptyList()), provider_id = "addon:4",
        title = "One Piece", badges = listOf("4K"),
        landscape = "https://images.example/one-piece.jpg", poster = "https://images.example/one-piece.jpg",
        description = "Monkey D. Luffy sets off in search of the One Piece, the greatest treasure in the world.",
        metadata = CardMetadata(year = 2023, runtime_minutes = 24, genres = listOf("Anime", "Adventure"), rating = 8.7,
            cast = listOf("Mayumi Tanaka", "Akemi Okamura")))
    private fun episode(number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("one-piece:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", subtitle = "S1 E$number · 24m",
        progress = progress, metadata = CardMetadata(runtime_minutes = 24))
    private val partway = DetailSnapshot(series, episodes = (1L..6L).map { episode(it, if (it == 3L) 0.4 else null) },
        seasons = listOf(EpisodeSeason(1, 6, 0)))
    private val unwatched = partway.copy(episodes = (1L..6L).map { episode(it) })

    private val PLAY_EPISODE = Regex("Play S[0-9]+ E[0-9]+")

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(opening: DetailSnapshot) : AutoCloseable {
        var detail by mutableStateOf(opening)
        private var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = true,
                    continuing = listOfNotNull(detail.episodes.firstOrNull { it.progress != null }))
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun nodes(): List<SemanticsNode> {
            val acc = mutableListOf<SemanticsNode>()
            fun visit(node: SemanticsNode) { acc += node; node.children.forEach(::visit) }
            scene.semanticsOwners.forEach { visit(it.rootSemanticsNode) }
            return acc
        }

        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun play(): SemanticsNode = assertNotNull(nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) &&
                texts(node).any { it == "Play" || it.startsWith("Resume") || PLAY_EPISODE.matches(it) }
        }, "the hero's Play is on screen: " + nodes().flatMap(::texts))
        fun heroBar(button: Rect): SemanticsNode? = nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null &&
                node.boundsInRoot.top >= button.top - 1f && node.boundsInRoot.bottom <= button.bottom + 1f
        }
        override fun close() = scene.close()
    }

    @Test fun theResumeBarSitsUnderThePlayLabelAlongTheButtonsBottomEdge() = onEdt {
        Page(partway).use { page ->
            page.settle()
            val button = page.play().boundsInRoot
            val bar = assertNotNull(page.heroBar(button), "the hero draws the resume bar")
            val bounds = bar.boundsInRoot
            assertEquals(4f, bounds.height, 0.5f, "the bar is a hairline: $bounds")
            val labelBand = button.center.y
            assertTrue(bounds.top >= labelBand, "the bar starts below the label's line, not through it: bar $bounds in button $button")
            val fromBottom = button.bottom - bounds.bottom
            assertTrue(fromBottom in 0f..20f, "the bar hugs the button's bottom edge: ${fromBottom}px above it")
            assertEquals(0.4f, bar.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.01f, "the bar carries the progress")
        }
    }

    @Test fun aTitleWithNothingToResumeDrawsNoHeroBar() = onEdt {
        Page(unwatched).use { page ->
            page.settle()
            val button = page.play().boundsInRoot
            assertNull(page.heroBar(button), "nothing to resume, no bar")
        }
    }
}