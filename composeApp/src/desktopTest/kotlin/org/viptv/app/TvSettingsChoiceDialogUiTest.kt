package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.settings.TvDialogPanelTag
import org.viptv.app.features.settings.TvSettingsChoice
import org.viptv.app.features.settings.TvSettingsChoiceDialog
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvSettingsChoiceDialogUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private companion object {
        val captures = File("/tmp/viptv-1068-choice").apply { deleteRecursively(); mkdirs() }
    }

    private val languages = listOf("Original language", "English", "Spanish", "Portuguese", "French", "German",
        "Italian", "Japanese", "Korean", "Mandarin", "Polish", "Russian")

    private var picked: String? = null
    private var dismissals = 0

    private inner class Panel(val name: String, content: @Composable () -> Unit) : AutoCloseable {
        private var clock = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        }

        fun tick() { scene.render(clock++ * 16_666_667L).close() }

        suspend fun settle(count: Int = 24) {
            repeat(count) { tick(); yield() }
            delay(120)
            repeat(8) { tick(); yield() }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun label(node: SemanticsNode) = (texts(node) +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ").trim()
        fun labels() = nodes().map(::label).filter { it.isNotEmpty() }
        fun panel() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == TvDialogPanelTag }
        fun row(text: String) = nodes().firstOrNull {
            it.config.contains(SemanticsActions.OnClick) && label(it) == text
        }
        fun focused(): String? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::label)
        fun checked(): List<String> = nodes().filter { it.config.getOrNull(SemanticsProperties.Selected) == true }.map(::label)

        fun capture() = scene.render(clock++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(captures, "$name.png").writeBytes(it.bytes) }
        }

        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun dialog(current: String?) = Panel("languages") {
        TvSettingsChoiceDialog(TvSettingsChoice("Subtitle language", languages, current) { picked = it },
            scale = 1f, dismiss = { dismissals++ })
    }

    @Test fun theCurrentValueIsCheckedAndTakesFocusFirst() = onEdt {
        dialog("French").use { panel ->
            panel.settle()
            assertEquals(listOf("French"), panel.checked(), "only the value in use is checked: ${panel.labels()}")
            assertEquals("French", panel.focused(), "the list opens on the value in use")
            panel.capture()
        }
    }

    @Test fun everyValueIsAFullWidthRowInOneColumn() = onEdt {
        dialog("English").use { panel ->
            panel.settle()
            val frame = assertNotNull(panel.panel(), "the list draws the TV panel: ${panel.labels()}").boundsInRoot
            // A row clipped by the panel's scroll keeps its slot but loses its width, so only fully drawn rows count.
            val composed = languages.mapNotNull { language -> panel.row(language)?.boundsInRoot?.takeIf { it.width > 0f } }
            assertTrue(composed.size >= 6, "the panel shows a column of values: ${panel.labels()}")
            for (bounds in composed) {
                assertEquals(frame.width - 96f, bounds.width, 1f, "every value spans the panel's width, inside its padding")
                assertEquals(composed.first().left, bounds.left, 1f, "every value starts on the list's left edge")
            }
            assertTrue(composed.zipWithNext().all { (above, below) -> below.top >= above.bottom },
                "the values are one column, top to bottom")
            val cancel = assertNotNull(panel.row("Cancel"), "Cancel is a row").boundsInRoot
            assertTrue(cancel.top >= composed.last().bottom, "Cancel sits after the values")
            assertTrue(cancel.left >= frame.left && cancel.right <= frame.right, "Cancel stays inside the panel")
        }
    }

    @Test fun aLongListScrollsThePanelToKeepTheFocusedValueInView() = onEdt {
        dialog("Original language").use { panel ->
            panel.settle()
            val frame = assertNotNull(panel.panel(), "the list draws the TV panel").boundsInRoot
            val down = androidx.compose.ui.input.key.KeyEvent(androidx.compose.ui.input.key.Key.DirectionDown,
                androidx.compose.ui.input.key.KeyEventType.KeyDown)
            var seen = 0
            for (step in languages.indices) {
                panel.scene.sendKeyEvent(down)
                panel.settle(6)
                val name = panel.focused() ?: continue
                val bounds = panel.row(name)?.boundsInRoot ?: continue
                assertTrue(bounds.top >= frame.top && bounds.bottom <= frame.bottom,
                    "step $step: “$name” stays inside the panel at ${bounds.top}..${bounds.bottom} of ${frame.top}..${frame.bottom}")
                seen++
            }
            assertEquals(languages.size, seen, "the remote walks every value")
            panel.capture()
        }
    }

    @Test fun aValueThatIsNotInTheListLeavesNothingCheckedAndFocusesTheFirstRow() = onEdt {
        dialog("Auto (this device: 1080p)").use { panel ->
            panel.settle()
            assertTrue(panel.checked().isEmpty(), "nothing is checked when the row's label is not one of the choices")
            assertEquals(languages.first(), panel.focused(), "focus lands on the first value")
        }
    }

    @Test fun pickingAValueAppliesItAndClosesThePanel() = onEdt {
        picked = null
        dismissals = 0
        dialog("English").use { panel ->
            panel.settle()
            assertTrue(panel.row("Korean")!!.config[SemanticsActions.OnClick].action!!.invoke(), "Korean takes the press")
            panel.settle(8)
            assertEquals("Korean", picked, "the pick applies")
            assertEquals(1, dismissals, "the pick closes the panel")
        }
    }

    @Test fun cancelClosesWithoutPickingAndTheRemoteWalksTheRows() = onEdt {
        picked = null
        dismissals = 0
        dialog("Spanish").use { panel ->
            panel.settle()
            assertEquals("Spanish", panel.focused())
            val down = androidx.compose.ui.input.key.KeyEvent(androidx.compose.ui.input.key.Key.DirectionDown,
                androidx.compose.ui.input.key.KeyEventType.KeyDown)
            panel.scene.sendKeyEvent(down)
            panel.settle(8)
            assertEquals("Portuguese", panel.focused(), "Down walks to the next value")
            panel.scene.sendKeyEvent(down)
            panel.settle(8)
            assertEquals("French", panel.focused())
            assertTrue(panel.row("Cancel")!!.config[SemanticsActions.OnClick].action!!.invoke(), "Cancel takes the press")
            assertEquals(null, picked, "Cancel picks nothing")
            assertEquals(1, dismissals, "Cancel closes the panel")
        }
    }
}