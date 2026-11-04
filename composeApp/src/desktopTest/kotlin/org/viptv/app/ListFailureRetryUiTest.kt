package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Density
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.history.TvHistoryScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ListFailureRetryUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun page(width: Int = 1280, height: Int = 900, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
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
        val node = nodes(scene).first {
            it.config.contains(SemanticsActions.OnClick) && (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
        }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "$text is clickable")
        frames(scene)
    }

    private fun visible(scene: ImageComposeScene, text: String): Boolean = texts(scene).any { it.contains(text) }

    @Test fun historyFailureOffersTryAgain() {
        var retried = 0
        val scene = page {
            HistoryScreen(history = emptyList(), busy = false, error = "The server did not answer.",
                open = {}, clear = {}, retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load history"), "a failed history shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }

    @Test fun calendarFailureOffersTryAgain() {
        var retried = 0
        val scene = page {
            CalendarScreen(days = emptyList(), entries = emptyList(), television = false, open = {}, manage = {},
                busy = false, error = "The server did not answer.", retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load the calendar"), "a failed calendar shows its failure title")
            assertFalse(visible(scene, "Nothing is scheduled"), "no empty answer appears beside the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }

    @Test fun myListFailureOffersTryAgain() {
        var retried = 0
        val scene = page {
            LibraryScreen(cards = emptyList(), available = true, television = false, open = {}, history = {}, home = {},
                busy = false, error = "The server did not answer.", retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load My List"), "a failed My List shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            assertFalse(visible(scene, "Your list is empty."), "no empty answer appears beside the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }

    @Test fun tvMyListFailureOffersTryAgain() {
        var retried = 0
        val scene = page(width = 1920, height = 1080, television = true) {
            TvLibraryScreen(cards = emptyList(), available = true, open = {}, home = {},
                busy = false, error = "The server did not answer.", retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load My List"), "a failed TV My List shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            assertFalse(visible(scene, "Your list is empty."), "no empty answer appears beside the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }

    @Test fun tvHistoryFailureOffersTryAgain() {
        var retried = 0
        val scene = page(width = 1920, height = 1080, television = true) {
            TvHistoryScreen(history = emptyList(), busy = false, error = "The server did not answer.",
                open = {}, clear = {}, back = {}, retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load history"), "a failed TV history shows its failure title")
            assertTrue(visible(scene, "The server did not answer."), "the reason is shown")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }

    @Test fun tvCalendarFailureOffersTryAgain() {
        var retried = 0
        val scene = page(width = 1920, height = 1080, television = true) {
            TvCalendarScreen(days = emptyList(), entries = emptyList(), open = {}, manage = {},
                busy = false, error = "The server did not answer.", retry = { retried++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "Couldn’t load the calendar"), "a failed TV calendar shows its failure title")
            assertFalse(visible(scene, "No releases for this day."), "no empty answer appears beside the failure")
            click(scene, "Try again")
            assertEquals(1, retried, "Try again re-runs the load")
        } finally { scene.close() }
    }
}