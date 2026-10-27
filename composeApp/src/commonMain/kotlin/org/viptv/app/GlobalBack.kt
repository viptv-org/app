package org.viptv.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.viptv.app.state.*

/**
 * Back for the keyboard-and-mouse layouts, the desktop window and the browser: Escape, the mouse
 * Back button, the title-bar chevron and browser history all take this one step
 * (docs/BACK_MATRIX.md). As with Android Back, the open route's own step comes first (a player
 * panel, a sign-in step, a Settings sub-page); otherwise the app's [navigateBack].
 */
@Stable
class GlobalBack(private val controller: ApplicationController) {
    private var routeBack by mutableStateOf<(() -> Unit)?>(null)

    /** Pass as [App]'s `entryBackChanged`: routes register their own step there while it applies. */
    val routeBackChanged: ((() -> Unit)?) -> Unit = { routeBack = it }

    /** Whether Back has a step to take; false on a root page (Home without history, Connect). */
    fun canGoBack(state: ApplicationState): Boolean = routeBack != null || state.canNavigateBack()

    fun canGoForward(state: ApplicationState): Boolean = state.navForward.isNotEmpty()

    fun back() { routeBack?.invoke() ?: controller.navigateBack() }

    fun forward() = controller.navigateForward()
}
