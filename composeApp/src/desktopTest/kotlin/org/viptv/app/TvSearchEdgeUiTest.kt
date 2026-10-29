@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import java.io.File
import kotlin.math.abs
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class TvSearchEdgeUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val screenWidth = 1920

    private val columnStart = 96f + 520f + 84f
    private val keyboardRight = 96f + 520f

    private val cardWidth = 212f

    private fun cards(count: Int = 14) = (1..count).map { index ->
        Card(MediaIdentity("search-$index", "movie", emptyList()), provider_id = "addon:1",
            title = "Result $index", badges = emptyList())
    }

    private val rows = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = cards()))

    private class Fixture(width: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        val scene = ImageComposeScene(width, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                focusManager = LocalFocusManager.current
                content()
            } }
        }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); delay(2) } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun focused(): SemanticsNode = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?: fail("no focused control")
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            if (!consumed) focusManager!!.moveFocus(when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                else -> FocusDirection.Down
            })
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
        fun capture(name: String) {
            val dir = System.getenv("VIPTV_SEARCH_CAPTURE_DIR") ?: return
            File(dir).mkdirs()
            scene.render().use { image -> File(dir, "$name.png").writeBytes(requireNotNull(image.encodeToData()).bytes) }
        }
        override fun close() = scene.close()
    }

    private fun searchScreen() = Fixture(screenWidth) {
        SearchScreen("whisper", emptyList(), emptyList(), false, null, true, {}, {},
            television = true, editQuery = {}, searchRows = rows)
    }

    @Test fun resultRowsRunToTheRightScreenEdgeFromTheResultsColumn() = ui {
        searchScreen().use { screen ->
            screen.settle()
            val nodes = screen.nodes()
            val first = card(nodes, "Result 1") ?: fail("no first result card")
            val strip = nodes.filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .single { strip -> flatten(strip).any { it.id == first.id } }
            assertNear(columnStart, first.boundsInRoot.left, "the first card rests on the results column")
            assertNear(columnStart, strip.boundsInRoot.left, "the row starts at the results column, not under the keyboard gap")
            assertNear(screenWidth.toFloat(), strip.boundsInRoot.right, "the result row runs to the right screen edge")
            val edge = (2..14).mapNotNull { card(nodes, "Result $it") }
                .firstOrNull { it.boundsInRoot.left > columnStart && it.boundsInRoot.right >= screenWidth - 1f }
                ?: fail("no result card runs under the right screen edge")
            assertTrue(edge.boundsInRoot.left < screenWidth, "the edge card starts on screen")
        }
    }

    @Test fun dPadKeepsEveryFocusedCardOnScreenAndLeftReturnsToTheKeyboard() = ui {
        searchScreen().use { screen ->
            screen.settle()
            screen.capture("search-tv-rest")
            focusCard(screen, "Result 1"); screen.settle()
            assertFocusedCardFullyOnScreen(screen, "Result 1")
            for (index in 2..10) {
                screen.press(Key.DirectionRight); screen.settle()
                assertTrue("Result $index" in titles(screen.focused()),
                    "right through the row reaches Result $index, was ${titles(screen.focused())}")
                assertFocusedCardFullyOnScreen(screen, "Result $index")
            }
            screen.capture("search-tv-row-scrolled")
            repeat(9) { screen.press(Key.DirectionLeft); screen.settle() }
            assertTrue("Result 1" in titles(screen.focused()), "left returns to the first card")
            screen.press(Key.DirectionLeft); screen.settle()
            val keyboard = screen.focused()
            assertTrue(keyboard.boundsInRoot.right <= keyboardRight + 1f,
                "left from the first card focuses the keyboard column, was ${keyboard.boundsInRoot}")
            assertTrue(isKeyboardKey(keyboard), "left from the first card lands on a key, was ${titles(keyboard)}")
            screen.capture("search-tv-keyboard-left")
        }
    }

    @Test fun previewResultsRunToTheRightScreenEdge() = ui {
        Fixture(screenWidth) {
            org.viptv.app.preview.UiPreview(television = true, screen = org.viptv.app.state.Screen.Search)
        }.use { screen ->
            screen.settle(150)
            screen.capture("search-preview-rest")
            val row = screen.nodes().filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .filter { it.boundsInRoot.left >= columnStart - 1f }.maxByOrNull { it.boundsInRoot.right }
                ?: fail("the preview has no result row")
            assertNear(columnStart, row.boundsInRoot.left, "the preview row starts at the results column")
            assertNear(screenWidth.toFloat(), row.boundsInRoot.right, "the preview row runs to the right screen edge")
        }
    }

    private fun assertFocusedCardFullyOnScreen(screen: Fixture, name: String) {
        val focused = screen.focused()
        assertTrue(name in titles(focused), "focus is on $name, was ${titles(focused)}")
        val bounds = focused.boundsInRoot
        assertNear(cardWidth, bounds.width, "$name keeps its full width")
        assertTrue(bounds.left >= columnStart - 1f, "$name stays right of the keyboard column, was $bounds")
        assertTrue(bounds.right <= screenWidth + 1f, "$name never runs under the screen edge, was $bounds")
    }

    private fun isKeyboardKey(node: SemanticsNode) = texts(node).any { it.length == 1 } ||
        description(node).contains("Space") || description(node).contains("Delete")

    private fun card(nodes: List<SemanticsNode>, title: String) = nodes.firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && title in titles(it)
    }

    private fun focusCard(screen: Fixture, title: String) {
        val node = card(screen.nodes(), title) ?: fail("no $title card")
        assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke(), "$title accepts focus")
    }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun titles(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
}

private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
