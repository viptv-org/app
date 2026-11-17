@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.components.PAGE_STATE_TAG
import org.viptv.app.features.calendar.CALENDAR_EMPTY_WEEK
import org.viptv.app.features.calendar.CALENDAR_MANAGE
import org.viptv.app.features.calendar.CALENDAR_UNAVAILABLE
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// The Calendar's empty week and no-source states are one page state block on every layout, centred in the page area.
class CalendarPageStatesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val days = listOf(CalendarDay("d1", "Mon", "11", "Today", "Mon 11 Nov"),
        CalendarDay("d2", "Tue", "12", "Tomorrow", "Tue 12 Nov"))

    private data class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false)

    private val layouts = listOf(Layout("phone", 390, 844), Layout("tablet", 834, 1112), Layout("desktop", 1280, 800),
        Layout("web", 1440, 900), Layout("tv", 1920, 1080, television = true))

    private class Frame(val layout: Layout, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(layout.width, layout.height, Density(1f)) { content() }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun shows(text: String) = nodes().any { this.text(it).contains(text) }
        fun block() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == PAGE_STATE_TAG }
        override fun close() = scene.close()
    }

    private fun page(layout: Layout, available: Boolean): @Composable () -> Unit = {
        ViptvTheme(layout.television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                CalendarScreen(days, emptyList(), layout.television, open = {}, manage = {}, available = available,
                    controls = layout.television)
            }
        }
    }

    private fun everyLayout(available: Boolean, headline: String) = runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) {
            val label = "${layout.name}${if (available) "" else "/no source"}"
            val frame = Frame(layout, page(layout, available))
            try {
                frame.settle()
                if (!frame.shows(headline)) failures += "$label: '$headline' is not on the page"
                if (!frame.shows(CALENDAR_MANAGE)) failures += "$label: no $CALENDAR_MANAGE action"
                val block = frame.block()
                if (block == null) { failures += "$label: no page state block"; continue }
                val bounds = block.boundsInRoot
                val centre = bounds.left + bounds.width / 2
                if (abs(centre - layout.width / 2f) > 40f) failures += "$label: the block is off-centre at $centre of ${layout.width}"
                val middle = bounds.top + bounds.height / 2
                if (layout.television) {
                    if (abs(middle - layout.height / 2f) > 1f) failures += "$label: the block is not centred at $middle of ${layout.height}"
                } else {
                    val strip = setOf("11", "12", "Mon 11", "Tue 12", "Mon 11 Nov", "Tue 12 Nov", "Today", "Tomorrow")
                    val rail = frame.nodes().filter { frame.text(it).trim() in strip }.maxOfOrNull { it.boundsInRoot.bottom } ?: 0f
                    val expected = (rail + layout.height) / 2
                    if (abs(middle - expected) > 40f) failures += "$label: the block is centred at $middle, not in the page area below the day strip ($expected)"
                }
            } finally { frame.close() }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun everyLayoutDrawsTheEmptyWeekAsOneCentredBlock() = everyLayout(available = true, headline = CALENDAR_EMPTY_WEEK)

    @Test fun everyLayoutDrawsTheNoSourceStateAsOneCentredBlock() = everyLayout(available = false, headline = CALENDAR_UNAVAILABLE)
}
