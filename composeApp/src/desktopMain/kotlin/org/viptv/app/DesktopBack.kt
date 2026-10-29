package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.viptv.app.state.ApplicationState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopBackInputs(back: GlobalBack, state: ApplicationState, content: @Composable () -> Unit) {
    // Compose sends Escape here only after the focused content and any open dialog or popup let it pass.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = back.canGoBack(state),
        onBackCompleted = back::back)
    Box(Modifier.fillMaxSize().pointerInput(back) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                when (event.button) {
                    PointerButton.Back -> { event.changes.forEach { it.consume() }; back.back() }
                    PointerButton.Forward -> { event.changes.forEach { it.consume() }; back.forward() }
                    else -> Unit
                }
            }
        }
    }) { content() }
}
