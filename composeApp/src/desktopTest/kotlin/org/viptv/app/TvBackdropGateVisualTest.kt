package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.TvHomeBackdrop
import org.viptv.app.features.home.TvHomeVisibility
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Deterministic capture of the TV backdrop planes and the retained-visibility
 * gate. The artwork URLs are null on purpose (no network): the planes under
 * test are the gradient/mask/background draws and the visibility container,
 * which are exactly what the draw gates must not alter while visible and must
 * remove while at alpha 0. ImageComposeScene runs on a virtual clock, so the
 * frame indices land on the same point of the 450 ms plane swap every run.
 *
 * Set VIPTV_BACKDROP_CAPTURE_DIR to write PNGs.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvBackdropGateVisualTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val hero = Card(MediaIdentity("tt-hero", "movie", emptyList()), provider_id = "simkl",
        title = "Backdrop Hero", subtitle = "2026", badges = emptyList())
    private val picked = Card(MediaIdentity("tt-pick", "movie", emptyList()), provider_id = "simkl",
        title = "Shelf Pick", subtitle = "2024", badges = emptyList())

    @Test fun captureBackdropSwapFrames() = onEdt {
        val dir = System.getenv("VIPTV_BACKDROP_CAPTURE_DIR")
        val captures = linkedMapOf<String, ByteArray>()
        val browsing = mutableStateOf(false)
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            TvHomeBackdrop(card = if (browsing.value) picked else hero, browsing = browsing.value,
                portrait = true, hero = hero)
        } }
        var frame = 0L
        fun render(steps: Int = 1) { repeat(steps) { scene.render(frame++ * 16_666_667L).close() } }
        fun capture(name: String) {
            if (dir == null) return
            scene.render(frame++ * 16_666_667L).use { image -> captures[name] = image.encodeToData()!!.bytes }
        }
        try {
            render(8); delay(2)
            capture("backdrop-0-hero-settled")
            browsing.value = true
            render(8) // ~128 ms into the 450 ms swap
            capture("backdrop-1-mid-swap")
            render(24) // past 450 ms
            capture("backdrop-2-browse-settled")
            browsing.value = false
            render(8)
            capture("backdrop-3-mid-return")
            render(24)
            capture("backdrop-4-hero-again")
        } finally { scene.close() }
        if (dir != null) { File(dir).mkdirs(); for ((name, bytes) in captures) File(dir, "$name.png").writeBytes(bytes) }
    }

    @Test fun captureRetainedVisibilityGate() = onEdt {
        val dir = System.getenv("VIPTV_BACKDROP_CAPTURE_DIR")
        val captures = linkedMapOf<String, ByteArray>()
        val visible = mutableStateOf(true)
        val scene = ImageComposeScene(640, 360, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Color(0xFF102030))) {
                TvHomeVisibility(visible.value, 1f, 24, 450, retain = true) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE05050)))
                }
            }
        } }
        var frame = 0L
        fun render(steps: Int = 1) { repeat(steps) { scene.render(frame++ * 16_666_667L).close() } }
        fun capture(name: String) {
            if (dir == null) return
            scene.render(frame++ * 16_666_667L).use { image -> captures[name] = image.encodeToData()!!.bytes }
        }
        try {
            render(8); delay(2)
            capture("gate-0-visible")
            visible.value = false
            render(6)
            capture("gate-1-mid-exit")
            render(24)
            capture("gate-2-hidden")
            visible.value = true
            render(6)
            capture("gate-3-mid-enter")
            render(24)
            capture("gate-4-visible-again")
        } finally { scene.close() }
        if (dir != null) { File(dir).mkdirs(); for ((name, bytes) in captures) File(dir, "$name.png").writeBytes(bytes) }
    }
}
