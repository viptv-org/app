package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.HistoryCorrection
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HistoryCorrectionUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun card(id: String, kind: String = "movie") =
        Card(identity = MediaIdentity(id, kind, emptyList()), provider_id = "simkl", title = "The title", badges = emptyList())

    private fun inProgress(id: String = "movie-1") = HistoryDisplay(HistoryEntry(card(id), 1L), "Today",
        position = 30.0, duration = 100.0, watched = false, resumeActive = true)

    private fun page(width: Int = 1280, height: Int = 900, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) {
            val routes = rememberSaveableStateHolder()
            routes.SaveableStateProvider("page") { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        } }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 10) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private fun texts(scene: ImageComposeScene): List<String> = nodes(scene).map(::label)

    private fun click(scene: ImageComposeScene, text: String) {
        val node = nodes(scene).first { label(it).contains(text) && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "$text is clickable")
        frames(scene)
    }

    private fun setText(scene: ImageComposeScene, text: String) {
        val node = nodes(scene).first { it.config.contains(SemanticsActions.SetText) }
        assertTrue(node.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(text)) == true)
        frames(scene)
    }

    private fun visible(scene: ImageComposeScene, text: String): Boolean = texts(scene).any { it.contains(text) }

    @Test fun markWatchedAndUnwatchedFlipTheRowThroughTheMenu() {
        val entries = mutableStateOf(listOf(inProgress()))
        val corrections = mutableListOf<HistoryCorrection>()
        fun apply(display: HistoryDisplay, correction: HistoryCorrection): HistoryDisplay = when (correction) {
            HistoryCorrection.Watched -> display.copy(watched = true, resumeActive = false, position = display.duration)
            HistoryCorrection.Unwatched -> display.copy(watched = false, resumeActive = false, position = 0.0)
            is HistoryCorrection.Position -> display.copy(position = correction.seconds.toDouble(), watched = false,
                resumeActive = correction.seconds > 0)
        }
        val scene = page {
            HistoryScreen(entries.value, busy = false, error = null, open = {}, clear = {},
                correct = { display, correction ->
                    corrections += correction
                    entries.value = entries.value.map { if (it.entry.card.identity.id == display.entry.card.identity.id) apply(display, correction) else it }
                })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "In progress"), "an in-progress row shows its state")
            click(scene, "History actions")
            click(scene, "Mark watched")
            assertEquals(listOf<HistoryCorrection>(HistoryCorrection.Watched), corrections)
            assertTrue(visible(scene, "Watched"), "the row flips to watched")
            click(scene, "History actions")
            assertTrue(visible(scene, "Mark unwatched"), "a watched row offers mark unwatched")
            click(scene, "Mark unwatched")
            assertEquals(listOf<HistoryCorrection>(HistoryCorrection.Watched, HistoryCorrection.Unwatched), corrections)
        } finally { scene.close() }
    }

    @Test fun resumeAtSendsTheSavedPosition() {
        val entries = mutableStateOf(listOf(inProgress()))
        val corrections = mutableListOf<HistoryCorrection>()
        val scene = page {
            HistoryScreen(entries.value, busy = false, error = null, open = {}, clear = {},
                correct = { _, correction -> corrections += correction })
        }
        try {
            frames(scene)
            click(scene, "History actions")
            click(scene, "Resume at…")
            setText(scene, "24")
            click(scene, "Save position")
            assertEquals(listOf<HistoryCorrection>(HistoryCorrection.Position(24)), corrections)
        } finally { scene.close() }
    }

    @Test fun importedUnknownDateRowsKeepTheirNote() {
        val imported = inProgress().copy(watched = true, resumeActive = false, completionOnly = true, watchDateKnown = false)
        val entries = mutableStateOf(listOf(imported))
        val scene = page {
            HistoryScreen(entries.value, busy = false, error = null, open = {}, clear = {}, correct = { _, _ -> })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Imported — date unknown"), "an imported completion is labelled")
            assertTrue(visible(scene, "Watched"), "its watched state stays visible")
        } finally { scene.close() }
    }

    @Test fun localModeStatesCorrectionsAreUnavailableAndKeepsClearHistory() {
        val entries = mutableStateOf(listOf(inProgress()))
        val scene = page {
            HistoryScreen(entries.value, busy = false, error = null, open = {}, clear = {}, localMode = true)
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Progress corrections need a server."), "local mode states the actions are unavailable")
            click(scene, "History actions")
            assertFalse(visible(scene, "Mark watched"), "no correction action appears in local mode")
            assertFalse(visible(scene, "Mark unwatched"), "no correction action appears in local mode")
            click(scene, "View details")
            click(scene, "Clear history")
            assertTrue(visible(scene, "Clear history?"), "Clear history is still offered")
        } finally { scene.close() }
    }
}