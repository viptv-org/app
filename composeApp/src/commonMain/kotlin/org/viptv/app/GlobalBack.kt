package org.viptv.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.viptv.app.state.*

@Stable
class GlobalBack(private val controller: ApplicationController) {
    private var routeBack by mutableStateOf<(() -> Unit)?>(null)

    val routeBackChanged: ((() -> Unit)?) -> Unit = { routeBack = it }

    fun canGoBack(state: ApplicationState): Boolean = routeBack != null || state.canNavigateBack()

    fun canGoForward(state: ApplicationState): Boolean = state.navForward.isNotEmpty()

    fun back() { routeBack?.invoke() ?: controller.navigateBack() }

    fun forward() = controller.navigateForward()
}
