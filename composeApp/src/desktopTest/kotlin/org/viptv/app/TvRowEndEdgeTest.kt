@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.features.details.ShelfFocus
import org.viptv.app.features.details.ShelfFocusHandle
import org.viptv.app.features.details.ShelfLanding
import org.viptv.app.features.details.TvEpisodeShelf
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import kotlin.math.abs
import kotlin.test.*

// AND-TV-ROW-EDGE-001: a TV media row runs to the viewport's right edge, keeping only its start inset.
class TvRowEndEdgeTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val viewport = 1920f
    private val startInset = 96f

    private class Ui(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { focusManager = LocalFocusManager.current; content() }
        }
        suspend fun settle(frames: Int = 12) = repeat(frames) { scene.render(frame++ * 16_666_667L).close(); delay(2) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun focusedOrNull(): SemanticsNode? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun focused(): SemanticsNode = focusedOrNull() ?: fail("nothing holds focus")
        fun label(node: SemanticsNode): String =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        suspend fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            if (!consumed) focusManager!!.moveFocus(if (key == Key.DirectionLeft) FocusDirection.Left else FocusDirection.Right)
            settle()
        }
        suspend fun focusFirst(request: FocusRequester) {
            repeat(20) {
                runCatching { request.requestFocus() }
                settle(2)
                if (focusedOrNull() != null) return
            }
            fail("the first card never took focus")
        }
        suspend fun focusEpisodes(shelf: ShelfFocusHandle) {
            repeat(20) {
                if (shelf.request?.invoke(ShelfLanding.Any) == ShelfFocus.Focused) { settle(); return }
                settle(2)
            }
            fail("the episode row never took focus")
        }
        suspend fun traverseToTheEnd() {
            var current = label(focused())
            repeat(40) {
                press(Key.DirectionRight)
                val next = label(focused())
                if (next == current) return
                current = next
            }
            fail("Right never settled on a last card")
        }
        override fun close() = scene.close()
    }

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= 1f, "$what: expected $expected, got $actual")

    private fun movies(count: Int) = (1..count).map { index ->
        Card(MediaIdentity("movie-$index", "movie", emptyList()), provider_id = "fixture", title = "Title $index", badges = emptyList())
    }

    private val show = Card(MediaIdentity("show", "series", emptyList()), provider_id = "fixture", title = "Show", badges = emptyList())

    private fun episodes(count: Int): List<Card> = (1..count).map { number ->
        show.copy(title = "Episode $number", identity = MediaIdentity("episode-$number", "series", emptyList(),
            EpisodeCoordinates("aired", 1, number.toLong())))
    }

    private suspend fun Ui.assertLastCardAtTheEdge(name: String, marker: String) {
        traverseToTheEnd()
        val last = focused()
        assertTrue(label(last).contains(marker), "$name: focus ended on '${label(last)}', not $marker")
        val bounds = last.boundsInRoot
        near(viewport, bounds.right, "$name last card right edge")
        assertTrue(bounds.left >= startInset, "$name last card starts before the row inset: $bounds")
        press(Key.DirectionRight)
        assertEquals(label(last), label(focused()), "$name: Right at the end stays on the last card")
        near(viewport, focused().boundsInRoot.right, "$name last card right edge after Right")
    }

    @Test fun homeShelvesScrollTheirLastCardToTheViewportEdge() = onEdt {
        for (id in listOf("popular", "continue")) {
            val name = "Home $id"
            val first = FocusRequester()
            val row = HomeRow(id = id, title = "Popular", provider_id = "fixture", state = RowState.READY, cards = movies(14))
            Ui { TvHomeShelf(row, scale = 1f, first = first, up = {}, focus = {}, open = {}, modifier = Modifier.padding(top = 40.dp)) }.use { ui ->
                ui.settle()
                ui.focusFirst(first)
                near(startInset, ui.focused().boundsInRoot.left, "$name first card")
                near(startInset, ui.nodes().single { ui.label(it) == "Popular" }.boundsInRoot.left, "$name heading")
                ui.assertLastCardAtTheEdge(name, "Title 14")
                ui.press(Key.DirectionLeft)
                assertTrue(ui.label(ui.focused()).startsWith("Title 13"), "$name: Left moves one card back")
            }
        }
    }

    @Test fun theDetailsEpisodeRowScrollsItsLastEpisodeToTheViewportEdge() = onEdt {
        val shelf = ShelfFocusHandle()
        Ui {
            TvEpisodeShelf(show, episodes(10), scale = 1f, play = {}, focused = {}, expanded = true, resume = null,
                returnToHero = {}, focus = shelf)
        }.use { ui ->
            ui.settle()
            ui.focusEpisodes(shelf)
            near(startInset, ui.focused().boundsInRoot.left, "Details first episode")
            near(startInset, ui.nodes().single { ui.label(it) == "Season 1" }.boundsInRoot.left, "Details season chip")
            ui.assertLastCardAtTheEdge("Details", "10. Episode 10")
            ui.press(Key.DirectionLeft)
            assertTrue(ui.label(ui.focused()).contains("9. Episode 9"), "Details: Left moves one episode back")
        }
    }
}
