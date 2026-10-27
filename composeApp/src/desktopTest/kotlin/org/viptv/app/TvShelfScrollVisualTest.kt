package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Deterministic, frame-by-frame capture of a shelf scroll.
 *
 * [ImageComposeScene] runs on a virtual clock, so the same frame index always
 * shows the same point of the 300 ms scroll animation. Comparing these captures
 * between two builds proves that a change to the scroll mechanism is
 * pixel-identical mid-animation, not just once it settles. The live TV catalog is
 * randomised per launch, so this is the only repeatable way to check it.
 *
 * Set VIPTV_SHELF_CAPTURE_DIR to write PNGs.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvShelfScrollVisualTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun cards(count: Int) = (1..count).map { index ->
        Card(MediaIdentity("tt$index", "movie", emptyList()), provider_id = "simkl",
            title = "Title $index", subtitle = "Subtitle $index", badges = emptyList())
    }

    @Test fun captureShelfScrollFrames() = onEdt {
        val dir = System.getenv("VIPTV_SHELF_CAPTURE_DIR")
        val captures = linkedMapOf<String, ByteArray>()
        val requester = FocusRequester()
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeShelf(
                    row = HomeRow(id = "popular", title = "Popular", provider_id = "simkl", state = RowState.READY, cards = cards(14)),
                    scale = 1f, first = requester,
                    up = {}, focus = {}, open = {},
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } }
        var frame = 0
        fun render() { scene.render(frame * 16_666_667L).close(); frame++ }
        fun capture(name: String) {
            if (dir == null) return
            File(dir).mkdirs()
            scene.render(frame * 16_666_667L).use { image -> captures[name] = image.encodeToData()!!.bytes }
        }
        fun press(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
        try {
            repeat(6) { render(); delay(2) }
            requester.requestFocus()
            repeat(6) { render(); delay(2) }
            capture("shelf-0-rest")
            repeat(6) { press(Key.DirectionRight); repeat(4) { render() }; delay(2) }
            capture("shelf-1-scrolled-out")
            press(Key.DirectionRight)
            for (step in 0..24) {
                render()
                if (step in setOf(1, 4, 8, 13, 23)) capture("shelf-2-scroll-$step")
                delay(2)
            }
            capture("shelf-3-settled")
            repeat(3) { press(Key.DirectionLeft); repeat(5) { render() }; delay(2) }
            capture("shelf-4-back-start")
            for (step in 0..24) {
                render()
                if (step in setOf(1, 4, 8, 13, 23)) capture("shelf-5-back-$step")
                delay(2)
            }
            capture("shelf-6-back-settled")
        } finally { scene.close() }
        if (dir != null) for ((name, bytes) in captures) File(dir, "$name.png").writeBytes(bytes)
    }
}