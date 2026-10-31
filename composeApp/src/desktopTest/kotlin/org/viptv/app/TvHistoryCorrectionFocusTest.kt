package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import org.viptv.app.connection.HistoryCorrection
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.features.history.TvHistoryScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvHistoryCorrectionFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun card(id: String, title: String) =
        Card(identity = MediaIdentity(id, "movie", emptyList()), provider_id = "simkl", title = title, badges = emptyList())

    private fun row(id: String, title: String, position: Double = 30.0) =
        HistoryDisplay(HistoryEntry(card(id, title), 1L), "Today", position = position, duration = 100.0, resumeActive = true)

    @Test fun theHistoryRowActionsAreReachableWithDPadKeys() {
        val entries = mutableStateOf(listOf(row("m1", "Show 01"), row("m2", "Show 02")))
        val corrections = mutableListOf<Pair<String, HistoryCorrection>>()
        fun apply(display: HistoryDisplay, correction: HistoryCorrection): HistoryDisplay = when (correction) {
            HistoryCorrection.Watched -> display.copy(watched = true, resumeActive = false, position = display.duration)
            HistoryCorrection.Unwatched -> display.copy(watched = false, resumeActive = false, position = 0.0)
            is HistoryCorrection.Position -> display.copy(position = correction.seconds.toDouble(), watched = false,
                resumeActive = correction.seconds > 0)
        }
        Page {
            TvHistoryScreen(entries.value, busy = false, error = null, open = {}, clear = {},
                correct = { display, correction ->
                    corrections += display.entry.card.identity.id to correction
                    entries.value = entries.value.map {
                        if (it.entry.card.identity.id == display.entry.card.identity.id) apply(display, correction) else it
                    }
                })
        }.use { page ->
            page.until("the first row") { page.focusedText()?.startsWith("Show 01") == true }
            page.press(Key.DirectionDown)
            page.until("the second row") { page.focusedText()?.startsWith("Show 02") == true }
            page.press(Key.DirectionDown)
            assertEquals("Mark watched", page.focusedText(), "Down from the list reaches the action bar")
            page.press(Key.DirectionCenter)
            page.until("the row flips") { entries.value.any { it.entry.card.identity.id == "m2" && it.watched } }
            assertEquals(listOf<Pair<String, HistoryCorrection>>("m2" to HistoryCorrection.Watched), corrections)
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

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        fun texts() = nodes().map(::text)
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?.let { text(it).ifBlank { description(it) } }

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focusedText()})" }; frames(2) }
        }

        fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
            val direction = when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                Key.DirectionDown -> FocusDirection.Down
                else -> null
            }
            if (!consumed && direction != null) { focusManager!!.moveFocus(direction); frames() }
        }

        override fun close() = scene.close()
    }
}