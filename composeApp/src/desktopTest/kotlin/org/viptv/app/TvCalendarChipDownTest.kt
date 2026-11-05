package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.calendar.CalendarFilter
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.preview.PreviewTvCalendar
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvCalendarChipDownTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private var focusManager: FocusManager? = null
    private var frame = 0L
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun focusedText(scene: ImageComposeScene) = focused(scene)?.let(::text).orEmpty()
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(FocusDirection.Down)
        settle(scene)
    }
    private suspend fun focusChip(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).firstOrNull { text(it) == label && it.config.contains(SemanticsActions.RequestFocus) }
            ?: error("No chip named '$label'; texts=" + nodes(scene).map(::text).filter(String::isNotBlank))
        assertTrue(node.config[SemanticsActions.RequestFocus].action?.invoke() == true, "$label refused focus")
        settle(scene)
        assertEquals(label, focusedText(scene), "$label did not take focus")
    }
    private fun scene() = ImageComposeScene(1920, 1080, Density(1f)) {
        ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvCalendarScreen(PreviewTvCalendar.days, PreviewTvCalendar.entries, {}, {}, controls = true,
                    filter = CalendarFilter.YourShows, actions = PreviewTvCalendar.actions)
            }
        }
    }

    @Test fun downFromEveryCalendarChipLandsInTheDayGrid() = onEdt {
        val scene = scene()
        try {
            settle(scene)
            for (chip in listOf("Type: All", "Shows: Your shows", "Previous week", "This week", "Next week")) {
                focusChip(scene, chip)
                press(scene, Key.DirectionDown)
                assertEquals("Today", focusedText(scene), "Down from '$chip' must land on the active day, focused '${focusedText(scene)}'")
            }
        } finally { scene.close() }
    }
}