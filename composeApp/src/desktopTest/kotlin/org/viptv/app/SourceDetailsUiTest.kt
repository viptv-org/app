@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.sources.SourceDetailsPanel
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourcesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.SourceCandidate
import org.junit.Rule
import kotlin.test.*

class SourceDetailsUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val description = "Oak.Street.2026.2160p.WEB-DL.DUAL.DDP5.1.Atmos.DV.HDR10.H.265.MSubs-GROUP.mkv · MKV · HEVC · EAC3 · 14.2 GB · a tail the two-line window cannot hold"
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    @Test fun holdingOkOnATvRowOpensSourceDetailsAndBackReturnsToIt() = onEdt {
        var chosen: SourceCandidate? = null
        val source = SourceCandidate("tv", "addon:fixture", "Torrentio 4K", detail = description, quality_height = 2160)
        Scene(1920, 1080, television = true) {
            TvSourcesScreen(null, listOf(source), false, null, { chosen = it }, {}, info = mapOf("tv" to TvSourceInfo("4K")))
        }.use { s ->
            s.frames(40)
            assertTrue(s.text(s.focused()!!).contains("Torrentio 4K"), "the first row takes focus")
            s.keyDown(Key.DirectionCenter)
            s.waitFor("Source details") { s.hasDetails() }
            s.keyUp(Key.DirectionCenter)
            s.frames()
            assertEquals(2, s.descriptionNodes(), "the row window and Source details both hold the full text")
            assertNull(chosen, "a hold opens details instead of playing the source")
            s.keyDown(Key.Back); s.keyUp(Key.Back)
            s.waitFor("Source details to close") { !s.hasDetails() }
            assertTrue(s.text(s.focused()!!).contains("Torrentio 4K"), "Back returns to the row it came from")
            assertNull(chosen)
        }
    }

    @Test fun holdingEnterOnADesktopRowOpensSourceDetailsWithItsFullText() = onEdt {
        val source = SourceCandidate("desktop", "addon:fixture", "Torrentio 4K", detail = description, quality_height = 2160)
        Scene(1440, 900) { SourcesScreen(null, listOf(source), false, null, {}, {}) }.use { s ->
            s.frames(40)
            assertTrue(s.row("Torrentio 4K").config[SemanticsActions.RequestFocus].action!!.invoke())
            s.frames()
            s.keyDown(Key.Enter)
            s.waitFor("Source details") { s.hasDetails() }
            s.keyUp(Key.Enter)
            s.frames()
            assertEquals(2, s.descriptionNodes(), "the row window and Source details both hold the full text")
            s.keyDown(Key.Escape); s.keyUp(Key.Escape)
            s.waitFor("Source details to close") { !s.hasDetails() }
            assertTrue(s.text(s.focused()!!).contains("Torrentio 4K"), "closing returns to the row")
        }
    }

    @Test fun theDetailsPanelShowsTheFullDescriptionAndClosesToItsRow() {
        var closed = false
        val source = SourceCandidate("panel", "addon:fixture", "Torrentio 4K", detail = description)
        Scene(1920, 1080, television = true) {
            SourceDetailsPanel(source, television = true, scale = 1f, dismiss = { closed = true })
        }.use { s ->
            s.frames(20)
            s.pane()
            assertEquals(1, s.descriptionNodes())
            assertTrue(s.unmerged().any { s.text(it) == "Torrentio 4K" })
            assertEquals("Close", s.text(s.focused()!!), "focus starts on Close")
            assertTrue(s.focused()!!.config[SemanticsActions.OnClick].action!!.invoke())
            s.frames()
            assertTrue(closed)
        }
    }

    private inner class Scene(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }.also { scene -> repeat(16) { scene.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 6) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun merged() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        fun unmerged() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun focused() = merged().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        fun row(label: String) = merged().single { it.config.contains(SemanticsActions.OnClick) && text(it).contains(label) }
        fun pane() = unmerged().single { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Source details" }
        fun hasDetails() = unmerged().any { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Source details" }
        fun descriptionNodes() = unmerged().count { text(it) == description }
        fun keyDown(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)) }
        fun keyUp(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)) }
        suspend fun waitFor(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what" }
                frames(); delay(15)
            }
        }
        override fun close() = scene.close()
    }
}
