@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourcesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.junit.Rule
import kotlin.test.*

/** SRC-BEST-MATCH-001 Progressive ranking: a re-rank keeps focus and the scroll anchor on the same source. */
class SourceRerankUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun row(id: String) = SourceCandidate(id, "addon:fixture", "Source $id", detail = "Release $id")
    private fun ranked(ids: List<String>, best: Int = 5) = ids.map(::row) to SourceGroups.of(ids.take(best), ids.first())

    private class Scene(width: Int, height: Int, television: Boolean, content: @Composable () -> Unit) : AutoCloseable {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        private var frame = 0L
        suspend fun settle(n: Int = 40) { repeat(n) { scene.render(frame++ * 16_666_667L).close(); delay(1) } }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun row(id: String) = nodes().single { it.config.contains(SemanticsActions.OnClick) && text(it).contains("Source $id") }
        fun focused() = nodes().single { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun header(label: String) = nodes().singleOrNull { it.config.contains(SemanticsProperties.Heading) && text(it) == label }
        fun focus(id: String) = assertTrue(row(id).config[SemanticsActions.RequestFocus].action!!.invoke())
        /** A D-pad press: on desktop it only reaches key handlers, it does not move focus. */
        fun press(key: androidx.compose.ui.input.key.Key) {
            scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(key, androidx.compose.ui.input.key.KeyEventType.KeyDown))
            scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(key, androidx.compose.ui.input.key.KeyEventType.KeyUp))
        }
        override fun close() = scene.close()
    }

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(kotlin.math.abs(expected - actual) <= 1.5f, "$what: expected $expected, got $actual")

    @Test fun tvFocusStaysOnTheSameSourceWhenBetterRowsPushItIntoAllSources() = ui {
        // Scenario 18: focus on the fifth Best matches row when two better rows arrive.
        var list by mutableStateOf(ranked((1..8).map { "r$it" }))
        val info = (listOf("x1", "x2") + (1..8).map { "r$it" }).associateWith { TvSourceInfo(quality = "1080p") }
        Scene(1920, 1080, television = true) {
            TvSourcesScreen(null, list.first, false, null, {}, {}, info = info, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source r1"), "Initial focus is on the first row")
            s.press(androidx.compose.ui.input.key.Key.DirectionDown); s.focus("r5"); s.settle()
            val before = s.row("r5").boundsInRoot.top
            list = ranked(listOf("x1", "x2") + (1..8).map { "r$it" })
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source r5"), "Focus follows the source, not the index")
            near(before, s.row("r5").boundsInRoot.top, "The focused row keeps its on-screen position")
            // r4, now the first All sources row, sits above the leading edge: the header
            // scrolled away with the rows above the focused one, and the rows below follow.
            assertTrue(s.row("r6").boundsInRoot.top > s.row("r5").boundsInRoot.top)
        }
    }

    @Test fun tvBetterRowTakesFirstPlaceMarkerAndInitialFocusBeforeNavigation() = ui {
        // Scenario 17.
        var list by mutableStateOf(ranked(listOf("a", "b", "c")))
        val info = listOf("x", "a", "b", "c").associateWith { TvSourceInfo(quality = "1080p") }
        Scene(1920, 1080, television = true) {
            TvSourcesScreen(null, list.first, false, null, {}, {}, info = info, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source a"))
            list = ranked(listOf("x", "a", "b", "c"))
            s.settle()
            val focused = s.focused()
            assertTrue(s.text(focused).contains("Source x") && s.text(focused).contains("Best match"), "The better row takes focus and the marker")
            assertFalse(s.text(s.row("a")).contains("Best match"))
            val header = assertNotNull(s.header("BEST MATCHES"))
            assertTrue(header.boundsInRoot.top >= focused.boundsInRoot.top - 1 && header.boundsInRoot.top >= 0f,
                "The Best matches header stays in view with the focused row")
            assertFalse(header.config.contains(SemanticsActions.RequestFocus), "The header is not focusable")
            assertFalse(header.config.contains(SemanticsActions.OnClick), "The header is not activatable")
        }
    }

    @Test fun tvFocusStaysOnTheFirstRowOnceTheViewerHasPressedTheDpad() = ui {
        // Step 4: after Down and Up back to the first row, a better arrival does not take focus.
        var list by mutableStateOf(ranked(listOf("a", "b", "c")))
        val info = listOf("x", "a", "b", "c").associateWith { TvSourceInfo(quality = "1080p") }
        Scene(1920, 1080, television = true) {
            TvSourcesScreen(null, list.first, false, null, {}, {}, info = info, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            s.press(androidx.compose.ui.input.key.Key.DirectionUp); s.settle(4)
            s.focus("a"); s.settle()
            list = ranked(listOf("x", "a", "b", "c"))
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source a"), "Focus stays on the source the viewer is on")
            assertTrue(s.text(s.row("x")).contains("Best match"), "The marker still moves to the better row")
        }
    }

    @Test fun tvSelectedRowKeepsInitialFocusWhileBetterRowsArrive() = ui {
        var list by mutableStateOf(ranked(listOf("a", "b", "c")))
        val info = listOf("x", "a", "b", "c").associateWith { TvSourceInfo(quality = "1080p") }
        Scene(1920, 1080, television = true) {
            TvSourcesScreen(null, list.first, false, null, {}, {}, info = info, loadingMore = true, selectedSource = "b", groups = list.second)
        }.use { s ->
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source b"))
            list = ranked(listOf("x", "a", "b", "c"))
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source b"))
        }
    }

    @Test fun keyboardFocusCrossesHeadersAndStaysOnItsSourceAcrossARerank() = ui {
        // Scenario 18 on a desktop keyboard list: Up/Down skip the header; a re-rank keeps focus and position.
        var list by mutableStateOf(ranked(listOf("a", "b", "c", "d", "e"), best = 3))
        var focusManager: androidx.compose.ui.focus.FocusManager? = null
        Scene(1440, 900, television = false) {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            SourcesScreen(null, list.first, false, null, {}, {}, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            s.focus("c"); s.settle()
            assertTrue(focusManager!!.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)); s.settle()
            assertTrue(s.text(s.focused()).contains("Source d"), "Down crosses the All sources header to its first row")
            assertTrue(focusManager!!.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)); s.settle()
            assertTrue(s.text(s.focused()).contains("Source c"), "Up crosses back to the last Best matches row")
            val before = s.row("c").boundsInRoot.bottom
            // Enough later rows that the list can scroll to hold c in place.
            list = ranked(listOf("x1", "x2", "a", "b", "c", "d", "e") + (1..12).map { "z" + ('a' + it) }, best = 4)
            s.settle()
            assertTrue(s.text(s.focused()).contains("Source c"), "Focus stays on c, which now opens All sources")
            val header = assertNotNull(s.header("ALL SOURCES")).boundsInRoot
            val c = s.row("c").boundsInRoot
            assertTrue(header.top >= c.top - 1 && header.bottom <= c.bottom + 1, "c now opens All sources")
            near(before, c.bottom, "The row itself, below its new header, keeps its on-screen position")
        }
    }

    private suspend fun Scene.scrollDown(until: () -> Boolean) {
        repeat(40) {
            if (until()) return
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(195f, 600f), scrollDelta = Offset(0f, 1f))
            settle(4)
        }
        assertTrue(until(), "The list did not scroll")
    }

    @Test fun phoneScrollAnchorKeepsItsPlaceWhenRowsArriveAbove() = ui {
        // Scenario 19, viewer has scrolled.
        val ids = (1..20).map { "r" + it.toString().padStart(2, '0') }
        var list by mutableStateOf(ranked(ids))
        val chosen = mutableListOf<String>()
        Scene(390, 844, television = false) {
            SourcesScreen(null, list.first, false, null, { chosen += it.id }, {}, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            val top = s.row("r01").boundsInRoot.top
            fun shown(id: String) = s.nodes().singleOrNull { it.config.contains(SemanticsActions.OnClick) && s.text(it).contains("Source $id") }
            s.scrollDown { shown("r01").let { it == null || it.boundsInRoot.top < top - 300 } }
            s.settle()
            // The first row item starts at the list's top content padding.
            val anchor = ids.first { id -> shown(id)?.let { it.boundsInRoot.top >= top - 1 } == true }
            val before = s.row(anchor).boundsInRoot.top
            list = ranked(listOf("x1", "x2", "x3") + ids)
            s.settle()
            near(before, s.row(anchor).boundsInRoot.top, "The first fully visible row keeps its offset")
            assertTrue(shown("x1").let { it == null || it.boundsInRoot.bottom <= top }, "New rows above are seen only by scrolling up")
            assertTrue(chosen.isEmpty())
        }
    }

    @Test fun phoneListAtTheTopShowsTheNewBestRow() = ui {
        // Scenario 19, viewer has not scrolled.
        val ids = (1..20).map { "r" + it.toString().padStart(2, '0') }
        var list by mutableStateOf(ranked(ids))
        Scene(390, 844, television = false) {
            SourcesScreen(null, list.first, false, null, {}, {}, loadingMore = true, groups = list.second)
        }.use { s ->
            s.settle()
            val top = s.row("r01").boundsInRoot.top
            list = ranked(listOf("x1") + ids)
            s.settle()
            near(top, s.row("x1").boundsInRoot.top, "The new best row is at the top")
            assertTrue(s.text(s.row("x1")).contains("Best match"))
            assertNotNull(s.header("BEST MATCHES"))
        }
    }

    @Test fun phoneHeadersAreHeadingsThatNeverChooseASource() = ui {
        val chosen = mutableListOf<String>()
        val (rows, groups) = ranked(listOf("a", "b", "c"), best = 2)
        Scene(390, 844, television = false) {
            SourcesScreen(null, rows, false, null, { chosen += it.id }, {}, groups = groups)
        }.use { s ->
            s.settle()
            val header = assertNotNull(s.header("BEST MATCHES"))
            assertNotNull(s.header("ALL SOURCES"))
            assertFalse(header.config.contains(SemanticsActions.OnClick))
            val at = header.boundsInRoot.center
            s.scene.sendPointerEvent(PointerEventType.Press, at)
            s.scene.sendPointerEvent(PointerEventType.Release, at)
            s.settle(10)
            assertTrue(chosen.isEmpty(), "Pressing a header chooses nothing")
            val row = s.row("a").boundsInRoot
            val inRow = Offset(row.center.x, row.bottom - 10f)
            s.scene.sendPointerEvent(PointerEventType.Press, inRow)
            s.scene.sendPointerEvent(PointerEventType.Release, inRow)
            s.settle(10)
            assertEquals(listOf("a"), chosen)
        }
    }

    @Test fun phoneHeadersHideWhenBestMatchesIsEmpty() = ui {
        Scene(390, 844, television = false) {
            SourcesScreen(null, listOf(row("a"), row("b")), false, null, {}, {}, groups = SourceGroups())
        }.use { s ->
            s.settle()
            assertNull(s.header("BEST MATCHES")); assertNull(s.header("ALL SOURCES"))
            assertTrue(s.nodes().none { s.text(it).contains("Best match") })
        }
    }
}
