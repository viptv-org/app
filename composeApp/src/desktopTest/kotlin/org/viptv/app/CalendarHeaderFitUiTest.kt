package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class CalendarHeaderFitUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val full = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val short = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun days(current: Boolean) = full.indices.map { index ->
        CalendarDay("d$index", short[index], "${index + 1}", if (current && index == 1) "Today" else full[index], "October ${index + 1}")
    }

    @Test fun everyWeekdayHeaderFitsTheViewportAtEveryWidth() {
        for (width in listOf(600, 768, 900, 1024)) {
            for (current in listOf(true, false)) {
                val scene = ImageComposeScene(width, 900, Density(1f)) {
                    ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                        CalendarScreen(days(current), emptyList(), television = false, open = {}, manage = {})
                    } }
                }
                try {
                    frames(scene)
                    val where = "$width px ${if (current) "current" else "next"} week"
                    full.indices.forEach { index ->
                        val name = headerName(scene, index, current)
                        assertTrue(name.boundsInRoot.left >= -1f, "$where: '${text(name)}' starts off the left edge at ${name.boundsInRoot.left}")
                        assertTrue(name.boundsInRoot.right <= width + 1f, "$where: '${text(name)}' ends off the right edge at ${name.boundsInRoot.right}")
                        val layout = layout(name)
                        assertTrue(layout.size.width + 0.5f >= layout.getLineRight(layout.lineCount - 1),
                            "$where: '${text(name)}' is truncated mid-word")
                    }
                } finally { scene.close() }
            }
        }
    }

    @Test fun aWideHeaderKeepsTheFullWeekdayNames() {
        val scene = ImageComposeScene(1024, 900, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                CalendarScreen(days(current = false), emptyList(), television = false, open = {}, manage = {})
            } }
        }
        try {
            frames(scene)
            val seen = unmerged(scene).map(::text).toSet()
            full.forEach { expected -> assertTrue(expected in seen, "at 1024 px the header drops '$expected'") }
        } finally { scene.close() }
    }

    private fun headerName(scene: ImageComposeScene, index: Int, current: Boolean): SemanticsNode {
        val candidates = buildSet { add(short[index]); add(full[index]); if (current && index == 1) add("Today") }
        return unmerged(scene).single { text(it) in candidates && it.config.contains(SemanticsActions.GetTextLayoutResult) }
    }

    private fun layout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render((it + 1) * 16_666_667L).close() }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}