@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

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
import org.viptv.app.features.live.*
import org.viptv.app.preview.PreviewLive
import org.viptv.app.preview.PreviewTvLive
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvGuideRowBoundsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun rightOnARowsLastProgrammeStaysInTheRow() {
        Page { LiveScreen(PreviewTvLive.channels, television = true, start = PreviewLive.start, now = PreviewLive.now, watch = {}) }.use { page ->
            val rows = page.guideRows()
            assertTrue(rows.size >= 4, "the fixture draws several guide rows: ${rows.keys}")
            var walked = 0
            for ((top, row) in rows) {
                if (row.size < 2) continue
                val last = row.maxBy { it.boundsInRoot.left }
                page.requestFocus(last)
                page.press(Key.DirectionRight)
                val after = assertNotNull(page.focused(), "Right from ${page.text(last)} left nothing focused")
                assertEquals(last.boundsInRoot.top, after.boundsInRoot.top,
                    "Right from ${page.text(last)} (row at y=$top) jumped to ${page.text(after)} in another row")
                assertEquals(page.text(last), page.text(after),
                    "Right from the last programme of a row does nothing")
                walked++
            }
            assertTrue(walked >= 4, "walked $walked row ends")
        }
    }

    @Test fun rightOnAnEarlierProgrammeMovesOneCellAlongTheRow() {
        Page { LiveScreen(PreviewTvLive.channels, television = true, start = PreviewLive.start, now = PreviewLive.now, watch = {}) }.use { page ->
            for (row in page.guideRows().values) {
                val cells = row.sortedBy { it.boundsInRoot.left }
                for (index in 0 until cells.lastIndex) {
                    page.requestFocus(cells[index])
                    page.press(Key.DirectionRight)
                    val after = assertNotNull(page.focused())
                    assertEquals(cells[index + 1].boundsInRoot.left, after.boundsInRoot.left,
                        "Right from ${page.text(cells[index])} moves one cell along its row")
                }
            }
        }
    }

    @Test fun downFromARowsLastProgrammeEntersTheRowBelow() {
        Page { LiveScreen(PreviewTvLive.channels, television = true, start = PreviewLive.start, now = PreviewLive.now, watch = {}) }.use { page ->
            val rows = page.guideRows().toSortedMap()
            val tops = rows.keys.toList()
            for (index in 0 until tops.lastIndex) {
                val last = rows.getValue(tops[index]).maxBy { it.boundsInRoot.left }
                page.requestFocus(last)
                page.press(Key.DirectionDown)
                val after = assertNotNull(page.focused(), "Down from ${page.text(last)} left nothing focused")
                assertTrue(after.boundsInRoot.top > tops[index],
                    "Down from ${page.text(last)} in the row at y=${tops[index]} enters a row below, not ${page.text(after)}")
            }
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

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun focused() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }

        fun requestFocus(node: SemanticsNode) {
            until("${text(node)} to appear") { nodes().any { it.id == node.id } }
            node.config[SemanticsActions.RequestFocus].action!!.invoke()
            frames()
            until("focus on ${text(node)}") { focused()?.id == node.id }
        }

        // Guide cells are the focusable programmes right of the channel labels and below the preview, grouped into rows by their top edge.
        fun guideRows(): Map<Float, List<SemanticsNode>> = nodes()
            .filter { it.config.getOrNull(SemanticsActions.OnClick) != null && it.config.getOrNull(SemanticsProperties.Focused) != null }
            .filter { it.boundsInRoot.left > 400f && it.boundsInRoot.top > 460f }
            .groupBy { it.boundsInRoot.top }

        // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
            if (!consumed && key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                focusManager!!.moveFocus(when (key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    else -> FocusDirection.Down
                })
                frames()
            }
        }

        override fun close() = scene.close()
    }
}