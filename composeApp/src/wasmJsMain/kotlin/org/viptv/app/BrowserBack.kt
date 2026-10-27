package org.viptv.app

import androidx.compose.runtime.*
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.browser.document
import kotlinx.browser.window
import org.viptv.app.state.ApplicationController
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.random.Random

/** The web app with browser Back/Forward and Escape as the app's own Back (app#20, app#45). */
@Composable
internal fun BrowserApp() {
    val scope = rememberCoroutineScope()
    val controller = remember { ApplicationController(scope) }
    val back = remember(controller) { GlobalBack(controller) }
    val state by controller.state.collectAsState()
    // Escape that the focused content and any open dialog or menu let pass.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = back.canGoBack(state),
        onBackCompleted = back::back)
    BrowserHistory(controller, back)
    BodyEscape(back)
    App(browser = true, providedController = controller, entryBackChanged = back.routeBackChanged)
}

@Composable
private fun BrowserHistory(controller: ApplicationController, back: GlobalBack) {
    val history = remember(controller, back) {
        BrowserBackHistory(WindowSessionHistory(), { back.canGoBack(controller.state.value) }, { back.canGoForward(controller.state.value) },
            back::back, back::forward)
    }
    DisposableEffect(history) {
        val listener: (Event) -> Unit = { event -> history.moved(WindowSessionHistory.index((event as PopStateEvent).state)) }
        window.addEventListener("popstate", listener)
        history.start()
        onDispose { window.removeEventListener("popstate", listener) }
    }
    val state by controller.state.collectAsState()
    LaunchedEffect(history) {
        snapshotFlow { back.canGoBack(state) to back.canGoForward(state) }.collect { history.changed() }
    }
}

/**
 * Before a click or Tab moves DOM focus into Compose, keys reach the page, not Compose. Escape there
 * takes the same Back. Keyup, after the Details dialog's own first-key handler, which marks it handled.
 */
@Composable
private fun BodyEscape(back: GlobalBack) {
    DisposableEffect(back) {
        val listener: (Event) -> Unit = { event ->
            if (event is KeyboardEvent && event.key == "Escape" && !event.defaultPrevented && document.activeElement == document.body) {
                event.preventDefault(); back.back()
            }
        }
        window.addEventListener("keyup", listener)
        onDispose { window.removeEventListener("keyup", listener) }
    }
}

/** `window.history`, with entries marked by this page load so entries from an earlier load are not ours. */
private class WindowSessionHistory : SessionHistory {
    override fun push(index: Int) = window.history.pushState(entry(index), "", null)
    override fun replace(index: Int) = window.history.replaceState(entry(index), "", null)
    override fun go(delta: Int) = window.history.go(delta)

    companion object {
        private val load = Random.nextLong().toString(36)
        private fun entry(index: Int): JsAny = "viptv:$load:$index".toJsString()
        fun index(state: JsAny?): Int? = stateText(state)?.takeIf { it.startsWith("viptv:$load:") }?.substringAfterLast(':')?.toIntOrNull()
    }
}

private fun stateText(state: JsAny?): String? = js("(typeof state === 'string') ? state : null")
