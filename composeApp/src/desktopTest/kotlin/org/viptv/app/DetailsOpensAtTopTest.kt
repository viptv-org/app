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
import org.viptv.engine.wire.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsOpensAtTopTest {
    @get:Rule val uiThread = UiThreadRule()

    private val show = "tt681"
    private val artwork = "https://images.example/edgerunners.jpg"
    private val series = Card(MediaIdentity(show, "series", emptyList()), provider_id = "addon:21",
        title = "Cyberpunk: Edgerunners", badges = emptyList(), landscape = artwork, poster = artwork,
        description = "A street kid tries to survive in a city obsessed with technology and body modification.",
        metadata = CardMetadata(year = 2022, runtime_minutes = 25, genres = listOf("Anime", "Action")))
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("$show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", description = null,
        metadata = CardMetadata(runtime_minutes = 25))
    private val detail = DetailSnapshot(series, episodes = (1L..10L).map(::episode),
        seasons = listOf(EpisodeSeason(1, 10, 0)))
    private val continued = Card(MediaIdentity("$show:1:5", "series", emptyList(), EpisodeCoordinates("aired", 1, 5)),
        provider_id = "library", title = "Episode 5", badges = emptyList(), progress = 0.4,
        parent_identity = MediaIdentity(show, "series", emptyList()))

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(val width: Int, val height: Int, val television: Boolean, val progressed: Boolean) : AutoCloseable {
        var detail by mutableStateOf<DetailSnapshot?>(null)
        var busy by mutableStateOf(true)
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy, error = null, back = {}, sources = {}, television = television,
                    continuing = if (progressed) listOf(continued) else emptyList())
            }
        } }
        fun arrive() { busy = false; detail = this@DetailsOpensAtTopTest.detail }
        suspend fun settle() {
            repeat(3) { repeat(10) { scene.render(frame++ * 16_666_667L).close(); yield() }; delay(150) }
        }
        fun exact(text: String) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }
        override fun close() = scene.close()
    }

    private suspend fun heroTop(height: Int, television: Boolean): Float {
        val tops = listOf(false, true).map { progressed ->
            Page(if (television) 1920 else 390, height, television, progressed).use { page ->
                page.settle()
                page.arrive()
                page.settle()
                val title = assertNotNull(page.exact(series.title), "the hero title renders")
                if (!television) assertNull(page.exact("5. Episode 5"), "the episode list stays below the fold")
                assertTrue(title.boundsInRoot.top >= 0f && title.boundsInRoot.bottom <= height,
                    "the hero title is on screen: ${title.boundsInRoot}")
                title.boundsInRoot.top
            }
        }
        assertEquals(tops[0], tops[1],
            "a series with progress opens where an unwatched series does: ${tops[0]} against ${tops[1]}")
        return tops[0]
    }

    @Test fun phoneOpensAnUnwatchedAndAProgressedSeriesAtTheTop() = onEdt {
        val top = heroTop(844, television = false)
        assertTrue(top < 300f, "the phone hero title sits under the artwork at the top: ${top}px")
    }

    @Test fun tvOpensAnUnwatchedAndAProgressedSeriesAtTheTop() = onEdt {
        val top = heroTop(1080, television = true)
        assertTrue(top < 400f, "the TV hero title sits in its board slot: ${top}px")
    }
}
