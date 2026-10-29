package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.*
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class DesktopBackInputsTest {
    @get:Rule val uiThread = UiThreadRule()
    private class Window(start: ApplicationState) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }).also { it.mutable.value = start }
        val back = GlobalBack(controller)
        val input = DirectNavigationEventInput()
        var fieldShown by mutableStateOf(true)
        var dialogOpen by mutableStateOf(false)
        val field = FocusRequester()
        val scene = ImageComposeScene(480, 320, Density(1f)) {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) { dispatcher.addInput(input); onDispose { dispatcher.removeInput(input) } }
            val state by controller.state.collectAsState()
            DesktopBackInputs(back, state) {
                Box(Modifier.fillMaxSize()) {
                    if (fieldShown) Box(Modifier.size(40.dp).focusRequester(field).focusable().onKeyEvent { it.key == Key.Escape })
                    if (dialogOpen) Dialog(onDismissRequest = { dialogOpen = false }) { Text("Leave VIPTV?") }
                }
            }
        }
        private var frame = 0L
        fun render() = repeat(6) { scene.render(frame++ * 16_666_667L).close() }
        fun escape() {
            val consumed = scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
            if (!consumed) input.backCompleted()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp)); render()
        }
        fun mouse(button: PointerButton) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(300f, 200f), button = button)
            scene.sendPointerEvent(PointerEventType.Release, Offset(300f, 200f), button = button); render()
        }
        val screen get() = controller.state.value.screen
        override fun close() { scene.close(); scope.cancel() }
    }

    private fun page(screen: Screen, navBack: List<Screen>) = ApplicationState(screen = screen, navBack = navBack, selectedProfile = "1", localMode = true)

    @Test fun escapeThatNothingTookGoesBack() = Window(page(Screen.Library, listOf(Screen.Home, Screen.Calendar))).use { w ->
        w.render()
        w.escape()
        assertEquals(Screen.Calendar, w.screen)
    }

    @Test fun aFocusedControlOrAnOpenDialogKeepsItsEscape() = Window(page(Screen.Library, listOf(Screen.Home))).use { w ->
        w.render()
        w.field.requestFocus(); w.render()
        w.escape()
        assertEquals(Screen.Library, w.screen, "a focused control's Escape must not also go Back")
        w.fieldShown = false; w.render()
        w.dialogOpen = true; w.render()
        w.escape()
        assertFalse(w.dialogOpen, "Escape closes the dialog")
        assertEquals(Screen.Library, w.screen, "and only the dialog")
        w.escape()
        assertEquals(Screen.Home, w.screen)
    }

    @Test fun theMouseButtonsGoBackAndForward() = Window(page(Screen.Library, listOf(Screen.Home, Screen.Calendar))).use { w ->
        w.render()
        w.mouse(PointerButton.Back)
        assertEquals(Screen.Calendar, w.screen)
        w.mouse(PointerButton.Forward)
        assertEquals(Screen.Library, w.screen)
    }

    @Test fun aRoutesOwnStepComesFirstAndARootPageIgnoresBack() = Window(page(Screen.Home, emptyList())).use { w ->
        w.render()
        w.escape(); w.mouse(PointerButton.Back)
        assertEquals(Screen.Home, w.screen)
        var panels = 1
        w.back.routeBackChanged { panels-- }; w.render()
        w.escape()
        assertEquals(0, panels, "Escape must close the open panel")
        assertEquals(Screen.Home, w.screen)
    }
}
