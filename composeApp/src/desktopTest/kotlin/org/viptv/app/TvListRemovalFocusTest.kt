package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.ListRemovalHost.Companion.continueCard
import org.viptv.app.ListRemovalHost.Companion.libraryCard
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.TvContinueWatchingScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.theme.Palette
import org.junit.Rule
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvListRemovalFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun tvContinueWatchingReachesTheRemoveActionAndConfirmsIt() {
        val removed = mutableListOf<Card>()
        Page {
            TvContinueWatchingScreen(ContinueWatchingPage(listOf(continueCard(1), continueCard(2)), loaded = true),
                play = {}, open = {}, more = null, retry = {}, home = {}, remove = { removed += it })
        }.use { page ->
            page.until("the first card") { page.focusedText()?.startsWith("Show 01") == true }
            page.press(Key.DirectionDown)
            assertEquals("Remove from Continue Watching", page.focusedText(), "Down leaves the grid for the remove action")
            page.press(Key.DirectionCenter)
            page.until("the confirmation") { page.texts().any { it == "Remove from Continue Watching?" } }
            assertTrue(page.texts().any { it.contains("Watched history is preserved.") })
            page.click("Remove")
            assertEquals(listOf(continueCard(1)), removed)
        }
    }

    @Test fun tvMyListReachesTheRemoveActionAndConfirmsIt() {
        val removed = mutableListOf<Card>()
        Page {
            TvLibraryScreen(listOf(libraryCard(1), libraryCard(2)), available = true, open = {}, home = {},
                remove = { removed += it })
        }.use { page ->
            page.focusCard("Show 01")
            page.press(Key.DirectionDown)
            assertEquals("Remove from My List", page.focusedText(), "Down leaves the grid for the remove action")
            page.press(Key.DirectionCenter)
            page.until("the confirmation") { page.texts().any { it == "Remove from My List?" } }
            page.click("Remove")
            assertEquals(listOf(libraryCard(1)), removed)
        }
    }

    @Test fun theRemoveActionActsOnTheCardTheViewerStoodOn() {
        val removed = mutableListOf<Card>()
        Page {
            TvContinueWatchingScreen(ContinueWatchingPage((1..4).map { continueCard(it) }, loaded = true),
                play = {}, open = {}, more = null, retry = {}, home = {}, remove = { removed += it })
        }.use { page ->
            page.until("the first card") { page.focusedText()?.startsWith("Show 01") == true }
            page.press(Key.DirectionRight, 2)
            page.press(Key.DirectionDown)
            page.press(Key.DirectionCenter)
            page.until("the confirmation") { page.texts().any { it == "Remove from Continue Watching?" } }
            page.click("Remove")
            assertEquals(listOf(continueCard(3)), removed, "the bar acts on the card the viewer left")
        }
    }

    private class Page(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        private val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                focusManager = LocalFocusManager.current
                Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
            }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        fun texts() = nodes().map(::text)
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?.let { text(it).ifBlank { description(it) } }

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focusedText()})" }; frames(2) }
        }

        fun focusCard(title: String) {
            until("$title on screen") { nodes().any { description(it).startsWith(title) || text(it).startsWith(title) } }
            repeat(6) {
                if (focusedText()?.startsWith(title) == true) return
                focusManager!!.moveFocus(FocusDirection.Down); frames()
            }
            assertEquals(title, focusedText()?.take(title.length), "Down walks from the filters onto $title")
        }

        // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
        fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
            val direction = when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> null
            }
            if (!consumed && direction != null) { focusManager!!.moveFocus(direction); frames() }
        }

        fun click(label: String) {
            val node = nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) && text(it) == label }
            assertNotNull(node, "$label is on screen")
            node.config[SemanticsActions.OnClick].action?.invoke()
            frames()
        }

        override fun close() = scene.close()
    }
}
