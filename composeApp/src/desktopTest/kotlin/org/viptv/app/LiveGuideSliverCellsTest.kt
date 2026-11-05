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
import androidx.compose.ui.unit.dp
import org.viptv.app.features.live.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LiveGuideSliverCellsTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun program(id: String, start: Int, end: Int) = LiveProgram(id, id, start, end)
    private fun slots(programs: List<LiveProgram>, minimumMinutes: Int) =
        liveGuideSlots(programs, start = 0, span = 120, minimumMinutes = minimumMinutes).map { Triple(it.program.id, it.begin, it.end) }

    @Test fun aSliverMergesIntoTheProgramBeforeIt() {
        val guide = listOf(program("news", 0, 60), program("filler", 60, 61), program("filler two", 61, 62), program("afternoon", 62, 120))
        assertEquals(listOf(Triple("news", 0, 62), Triple("afternoon", 62, 120)), slots(guide, 5))
    }

    @Test fun aSliverAtTheWindowStartMergesIntoTheProgramAfterIt() {
        assertEquals(listOf(Triple("news", 0, 120)), slots(listOf(program("filler", 0, 2), program("news", 2, 120)), 5))
    }

    @Test fun aShortProgramAfterAGapIsDroppedRatherThanCoveringTheGap() {
        val guide = listOf(program("news", 0, 60), program("filler", 70, 71), program("afternoon", 100, 120))
        assertEquals(listOf(Triple("news", 0, 60), Triple("afternoon", 100, 120)), slots(guide, 5))
    }

    @Test fun programsAreClippedToTheWindowAndOrderedByStart() {
        assertEquals(listOf(Triple("news", 0, 60), Triple("afternoon", 60, 120)),
            slots(listOf(program("afternoon", 60, 200), program("news", -30, 60)), 5))
    }

    @Test fun programsOutsideTheWindowAreNotRendered() {
        assertEquals(emptyList<Triple<String, Int, Int>>(), slots(listOf(program("before", -60, -10), program("after", 300, 400)), 5))
    }

    @Test fun aLoneShortProgramHasNoNeighbourToMergeInto() {
        assertEquals(emptyList<Triple<String, Int, Int>>(), slots(listOf(program("filler", 70, 71)), 5))
    }

    @Test fun theInitialProgramIsTheOneOnAir() {
        val guide = liveGuideSlots(listOf(program("news", 0, 60), program("afternoon", 60, 120)), start = 0, span = 120, minimumMinutes = 5)
        assertEquals("afternoon", guide.initial(90)?.program?.id)
        assertEquals("news", guide.initial(null)?.program?.id)
        assertEquals("news", guide.initial(300)?.program?.id)
    }

    @Test fun theMinimumProgramWidthGrowsWithTheTimeline() {
        assertEquals(7, liveGuideMinimumMinutes(television = true, scale = 1f, minuteWidth = 11.7.dp, gap = 8.dp))
        assertEquals(10, liveGuideMinimumMinutes(television = false, scale = 1f, minuteWidth = 6.dp, gap = 4.dp))
        assertEquals(3, liveGuideMinimumMinutes(television = false, scale = 1f, minuteWidth = 20.dp, gap = 4.dp))
    }

    private val sliverGuide = listOf(
        LiveProgram("p1", "Started earlier", -30, 60),
        LiveProgram("p2", "Filler", 60, 61),
        LiveProgram("p3", "Filler two", 61, 62),
        LiveProgram("p4", "Afternoon", 62, 120))

    private fun channel(programs: List<LiveProgram>) = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()),
        id = "one", connectionId = "m3u:1", programs = programs)

    @Test fun tvGuideFocusOnlyRestsOnVisiblePrograms() {
        Page { LiveScreen(listOf(channel(sliverGuide)), television = true, start = 0, now = 30, watch = {}) }.use { page ->
            page.focus("Started earlier")
            val cells = page.cells(setOf("Started earlier", "Afternoon"))
            assertEquals(2, cells.size, "only the programs wide enough to read are cells")
            assertTrue(cells.all { it.boundsInRoot.width >= 100f }, "cells stay wide: " + cells.map { it.boundsInRoot.width })
            page.press(Key.DirectionRight)
            assertEquals("Afternoon", page.text(assertNotNull(page.focused())))
            assertTrue(page.nodes().none { page.text(it).startsWith("Filler") }, "sliver programs are merged away, not rendered")
        }
    }

    @Test fun desktopGuideShowsNoSliverCells() {
        Page(1200, 800, television = false) {
            LiveScreen(listOf(channel(sliverGuide)), television = false, start = 0, now = 30, watch = {})
        }.use { page ->
            page.click("Full guide")
            page.until("the guide's programs") { page.nodes().any { page.text(it).startsWith("Afternoon") } }
            assertTrue(page.cells(setOf("Afternoon"), prefix = true).all { it.boundsInRoot.width >= 100f })
            assertTrue(page.nodes().none { page.text(it).startsWith("Filler") }, "sliver programs are merged away, not rendered")
        }
    }

    private class Page(width: Int = 1920, height: Int = 1080, television: Boolean = true, content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) {
                focusManager = LocalFocusManager.current
                Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
            }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun focused() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun cells(titles: Set<String>, prefix: Boolean = false) = nodes().filter {
            it.config.getOrNull(SemanticsActions.OnClick) != null && titles.any { title -> if (prefix) text(it).startsWith(title) else text(it) == title }
        }

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }

        fun focus(program: String) {
            until("the guide") { nodes().any { text(it) == program } }
            until("the guide's focus") { focused()?.let(::text) == program }
        }

        fun click(label: String) {
            until("$label to appear") { nodes().any { text(it) == label && it.config.getOrNull(SemanticsActions.OnClick) != null } }
            nodes().first { text(it) == label && it.config.getOrNull(SemanticsActions.OnClick) != null }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frames()
        }

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
