@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.viptv.app.components.FocusLazyColumn
import org.viptv.app.components.FocusLazyRow
import org.viptv.app.components.pointerClickable
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/**
 * app#59: on desktop and web a horizontal [FocusLazyRow] scrolls with the plain mouse
 * wheel and with click-drag; touch layouts and TV keep Foundation's scrolling, and a
 * rail inside a vertical page leaves the wheel to the page.
 */
class PointerRowScrollTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    private class Scene(pointer: Boolean, television: Boolean = false, content: @Composable () -> Unit) : AutoCloseable {
        val scene = ImageComposeScene(400, 600, Density(1f)) {
            ViptvTheme(television, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        private var time = 0L
        private fun millis() = time / 1_000_000
        suspend fun frames(n: Int = 30) = repeat(n) { scene.render(time).close(); time += 16_666_667L; delay(2) }
        suspend fun wheel(at: Offset, notches: Int = 3) {
            scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse, timeMillis = millis())
            repeat(notches) {
                scene.sendPointerEvent(PointerEventType.Scroll, at, type = PointerType.Mouse, scrollDelta = Offset(0f, 1f), timeMillis = millis())
                frames(2)
            }
            frames(60)
        }
        suspend fun drag(from: Offset, dx: Float, type: PointerType = PointerType.Mouse) {
            val buttons = if (type == PointerType.Mouse) PointerButtons(isPrimaryPressed = true) else null
            scene.sendPointerEvent(PointerEventType.Press, from, type = type, buttons = buttons,
                button = if (type == PointerType.Mouse) PointerButton.Primary else null, timeMillis = millis())
            frames(2)
            repeat(20) {
                scene.sendPointerEvent(PointerEventType.Move, from + Offset(dx * (it + 1) / 20, 0f), type = type, buttons = buttons, timeMillis = millis())
                frames(1)
            }
            scene.sendPointerEvent(PointerEventType.Release, from + Offset(dx, 0f), type = type,
                button = if (type == PointerType.Mouse) PointerButton.Primary else null, timeMillis = millis())
            frames(30)
        }
        suspend fun click(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Mouse, buttons = PointerButtons(isPrimaryPressed = true),
                button = PointerButton.Primary, timeMillis = millis())
            frames(2)
            scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Mouse, button = PointerButton.Primary, timeMillis = millis())
            frames(10)
        }
        override fun close() = scene.close()
    }

    /** Thirty 80 dp chips in a 400 dp row at the top of the window. */
    @Composable private fun Chips(state: LazyListState, clicked: ((Int) -> Unit)?) =
        FocusLazyRow(Modifier.fillMaxWidth().height(40.dp), state = state, contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((1..30).toList()) { n ->
                Box(Modifier.size(80.dp, 40.dp).then(if (clicked == null) Modifier else Modifier.pointerClickable { clicked(n) })) { Text("Chip $n") }
            }
        }

    private fun LazyListState.scrolled() = firstVisibleItemIndex * 86 + firstVisibleItemScrollOffset

    @Test fun thePlainWheelScrollsAChipRowSideways() = ui {
        val state = LazyListState()
        Scene(pointer = true) { Chips(state) {} }.use { s ->
            s.frames()
            s.wheel(Offset(200f, 20f))
            assertTrue(state.scrolled() > 40, "three wheel notches move the row: ${state.scrolled()} px")
        }
    }

    @Test fun clickDragScrollsTheRowAndDoesNotClickTheChip() = ui {
        val state = LazyListState()
        val clicks = mutableListOf<Int>()
        Scene(pointer = true) { Chips(state) { clicks += it } }.use { s ->
            s.frames()
            s.drag(Offset(300f, 20f), -200f)
            assertTrue(state.scrolled() in 170..230, "the row follows the mouse: ${state.scrolled()} px")
            assertTrue(clicks.isEmpty(), "a drag is not a click: $clicks")
            val before = state.scrolled()
            s.click(Offset(300f, 20f))
            assertEquals(1, clicks.size, "a plain click still clicks")
            assertEquals(before, state.scrolled())
        }
    }

    @Test fun touchLayoutsAndTvKeepFoundationScrolling() = ui {
        val tv = LazyListState()
        // The TV theme turns the pointer layout off even when one is asked for. The chips take
        // no clicks: on TV a press focuses a chip and focus anchoring scrolls it to the edge.
        Scene(pointer = true, television = true) { Chips(tv, null) }.use { s ->
            s.frames()
            s.wheel(Offset(200f, 20f))
            s.drag(Offset(300f, 20f), -200f)
            assertEquals(0, tv.scrolled(), "TV ignores the vertical wheel and mouse drags")
        }
        val touch = LazyListState()
        Scene(pointer = false) { Chips(touch) {} }.use { s ->
            s.frames()
            s.wheel(Offset(200f, 20f))
            s.drag(Offset(300f, 20f), -200f)
            assertEquals(0, touch.scrolled(), "a touch layout ignores the vertical wheel and mouse drags")
            s.drag(Offset(300f, 20f), -200f, PointerType.Touch)
            assertTrue(touch.scrolled() > 100, "a finger still scrolls the row: ${touch.scrolled()} px")
        }
    }

    @Test fun aRailInAVerticalPageLeavesTheWheelToThePage() = ui {
        val page = LazyListState()
        val rail = LazyListState()
        Scene(pointer = true) {
            FocusLazyColumn(Modifier.fillMaxSize(), state = page) {
                item { Chips(rail) {} }
                items((1..30).toList()) { Text("Row $it", Modifier.height(60.dp)) }
            }
        }.use { s ->
            s.frames()
            s.wheel(Offset(200f, 20f))
            assertEquals(0, rail.scrolled(), "the rail stays put")
            assertTrue(page.firstVisibleItemIndex > 0 || page.firstVisibleItemScrollOffset > 0, "the page scrolls")
        }
    }
}
