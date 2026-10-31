@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*

class SheetDragDismissTest {
    @get:Rule val uiThread = UiThreadRule()

    private val sources = (1..40).map { n ->
        SourceCandidate("s$n", "addon:torrentio", "Stream $n", detail = "Release $n",
            quality_height = 2160, presentation = SourcePresentation(provider_name = "Torrentio", source_id = "torrentio"))
    }

    private class Scene(width: Int, height: Int, pointer: Boolean, content: @Composable () -> Unit) : AutoCloseable {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television = false, pointer = pointer) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
        private var time = 0L
        private fun millis() = time / 1_000_000
        suspend fun frames(n: Int = 1) = repeat(n) { scene.render(time).close(); time += 16_666_667L; delay(1) }
        fun all() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun merged() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        fun pane(name: String) = all().firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == name }
        fun text(value: String) = all().firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun heading(value: String) = all().firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Heading) != null && n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun list() = all().filter { n ->
            n.config.contains(SemanticsActions.ScrollBy) && n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
        }.maxBy { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }

        suspend fun dragDown(from: Offset, by: Float, steps: Int = 24) {
            scene.sendPointerEvent(PointerEventType.Press, from, type = PointerType.Touch,
                buttons = PointerButtons(isPrimaryPressed = true), timeMillis = millis())
            frames(2)
            repeat(steps) { i ->
                val target = from + Offset(0f, by * (i + 1) / steps)
                scene.sendPointerEvent(PointerEventType.Move, target, type = PointerType.Touch,
                    buttons = PointerButtons(isPrimaryPressed = true), timeMillis = millis())
                frames(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, from + Offset(0f, by), type = PointerType.Touch, timeMillis = millis())
            frames(40)
        }

        override fun close() = scene.close()
    }

    private fun sourceScene(pointer: Boolean, check: suspend Scene.(dismissed: () -> Boolean) -> Unit) = runBlocking {
        val dismissed = mutableStateOf(false)
        val scene = Scene(500, 900, pointer) {
            SourcesScreen(null, sources, false, null, choose = {}, back = { dismissed.value = true },
                drawerVisible = !dismissed.value)
        }
        try {
            scene.frames(40)
            scene.check { dismissed.value }
        } finally { scene.close() }
    }

    @Test fun draggingAPhoneSheetPastTheThresholdDismissesIt() = sourceScene(pointer = false) { dismissed ->
        val pane = assertNotNull(pane("Choose a source"), "the phone drawer is open")
        dragDown(Offset(pane.boundsInRoot.center.x, pane.boundsInRoot.top + 40f), by = 360f)
        assertTrue(dismissed(), "a drag past a third of the sheet's height dismisses it")
    }

    @Test fun aShortDragSpringsTheSheetBackOpen() = sourceScene(pointer = false) { dismissed ->
        val pane = assertNotNull(pane("Choose a source"), "the phone drawer is open")
        val rest = pane.boundsInRoot.top
        dragDown(Offset(pane.boundsInRoot.center.x, rest + 40f), by = 60f)
        assertFalse(dismissed(), "a short drag does not dismiss")
        val back = assertNotNull(pane("Choose a source"), "the sheet stays composed").boundsInRoot.top
        assertTrue(abs(back - rest) < 2f, "the sheet springs back to its resting height: $rest -> $back")
    }

    @Test fun theListScrollsBeforeTheSheetFollowsTheDrag() = sourceScene(pointer = false) { dismissed ->
        val pane = assertNotNull(pane("Choose a source"), "the phone drawer is open")
        val rest = pane.boundsInRoot.top
        val listNode = list()
        assertTrue(listNode.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f, "the list overflows")
        listNode.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 600f)
        frames(6)
        val scrolled = list().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scrolled > 0f, "the list scrolled away from its top")
        val fromList = Offset(pane.boundsInRoot.center.x, pane.boundsInRoot.bottom - 120f)
        dragDown(fromList, by = 200f)
        assertFalse(dismissed(), "the drag only scrolled the list back toward its top")
        assertTrue(abs(pane("Choose a source")!!.boundsInRoot.top - rest) < 2f, "the sheet did not move while the list still had scroll room")
        assertTrue(list().config[SemanticsProperties.VerticalScrollAxisRange].value() < scrolled, "the drag went to the list first")

        list().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -2000f)
        frames(6)
        val fromTop = Offset(pane.boundsInRoot.center.x, pane.boundsInRoot.top + 300f)
        dragDown(fromTop, by = 600f)
        assertTrue(dismissed(), "a drag dismisses once the list has reached its top")
    }

    @Test fun aNonTouchSheetDrawsNoHandleAndDoesNotDrag() = sourceScene(pointer = true) { dismissed ->
        val pane = assertNotNull(pane("Choose a source"), "the phone drawer is open")
        val title = assertNotNull(text("Choose a source"), "the header text is present")
        val rest = pane.boundsInRoot.top
        assertTrue(title.boundsInRoot.top - rest < 20f,
            "no handle sits above the header on a pointer layout: ${title.boundsInRoot.top - rest} dp")
        dragDown(Offset(pane.boundsInRoot.center.x, rest + 40f), by = 360f)
        assertFalse(dismissed(), "a swipe cannot move a sheet nobody can touch")
        assertTrue(abs(pane("Choose a source")!!.boundsInRoot.top - rest) < 2f, "the sheet never moved")
    }

    @Test fun theAddOnSheetDismissesOnDrag() = runBlocking {
        val scene = Scene(390, 844, pointer = false) {
            AddonSettings(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {})
        }
        try {
            scene.frames(30)
            val add = scene.merged().first { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Add an add-on" } == true &&
                    n.config.contains(SemanticsActions.OnClick)
            }
            assertTrue(add.config[SemanticsActions.OnClick].action!!.invoke())
            scene.frames(30)
            val title = assertNotNull(scene.heading("Add an add-on"), "the add-on sheet opened")
            scene.dragDown(Offset(title.boundsInRoot.center.x, title.boundsInRoot.center.y), by = 200f)
            assertTrue(scene.all().none { it.config.contains(SemanticsActions.SetText) }, "the drag dismissed the add-on sheet")
        } finally { scene.close() }
    }
}