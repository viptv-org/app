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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
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
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PhoneEpisodeCaptionTest {
    @get:Rule val uiThread = UiThreadRule()

    private val series = Card(MediaIdentity("tt570", "series", emptyList()), provider_id = "addon:21", title = "Fixture show", badges = emptyList())
    private fun episode(number: Long, progress: Double? = null, watched: Boolean = false, minutes: Long? = 44) = series.copy(
        identity = MediaIdentity("tt570:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", subtitle = "S1 E$number", progress = progress,
        badges = if (watched) listOf("Watched") else emptyList(), metadata = minutes?.let { CardMetadata(runtime_minutes = it) })

    @Test fun phoneEpisodesCaptionTheDurationAndTheRemainingTime() = onEdt {
        val detail = DetailSnapshot(series, episodes = listOf(episode(1, watched = true), episode(2, progress = 0.4), episode(3)))
        Page(detail).use { page ->
            page.settle()
            page.revealAllEpisodes()
            val texts = page.texts()
            assertTrue(texts.contains("44m · 26m left"), "a part-watched episode shows what is left: $texts")
            assertTrue(texts.count { it == "44m" } >= 2, "finished and unstarted episodes show their duration: $texts")
            assertFalse(texts.contains("S1 E2") || texts.contains("S1 E3"), "the caption carries state instead of the coordinates: $texts")
        }
    }

    @Test fun aWatchedEpisodeKeepsItsPillAndDropsTheDuplicateState() = onEdt {
        val detail = DetailSnapshot(series, episodes = listOf(episode(1, watched = true)))
        Page(detail).use { page ->
            page.settle()
            val texts = page.texts()
            assertTrue(texts.contains("Watched"), "the watched pill stays: $texts")
            assertFalse(texts.contains("44m · Watched"), "the state is not printed twice: $texts")
        }
    }

    @Test fun anEpisodeWithoutADurationKeepsItsSubtitle() = onEdt {
        val detail = DetailSnapshot(series, episodes = listOf(episode(1, minutes = null)))
        Page(detail).use { page ->
            page.settle()
            assertTrue(page.texts().contains("S1 E1"), "nothing better than the coordinates to print: ${page.texts()}")
        }
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(initial: DetailSnapshot) : AutoCloseable {
        var detail by mutableStateOf(initial)
        private val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false, pointer = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {})
            }
        } }
        private var frame = 0L
        private suspend fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        suspend fun settle() { frames(); delay(200); frames() }
        suspend fun revealAllEpisodes() {
            val at = Offset(195f, 500f)
            scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (texts().none { it == "3. Episode 3" }) {
                check(System.nanoTime() < deadline) { "the episode list never showed the third row: ${texts()}" }
                scene.sendPointerEvent(PointerEventType.Scroll, at, type = PointerType.Mouse, scrollDelta = Offset(0f, 1f))
                frames(2)
            }
        }
        fun texts() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        override fun close() = scene.close()
    }
}