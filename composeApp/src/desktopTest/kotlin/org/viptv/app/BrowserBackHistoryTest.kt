package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.*
import kotlin.test.*

class BrowserBackHistoryTest {
    private class Tab : SessionHistory {
        val entries = mutableListOf<Int?>(null, null)
        var position = 1
        var left = false
        private val moves = ArrayDeque<Int>()
        var popstate: (Int?) -> Unit = {}
        override fun push(index: Int) { while (entries.size > position + 1) entries.removeAt(entries.lastIndex); entries += index; position++ }
        override fun replace(index: Int) { entries[position] = index }
        override fun go(delta: Int) { moves += delta }
        fun settle() {
            while (moves.isNotEmpty() && !left) {
                val target = position + moves.removeFirst()
                if (target !in entries.indices) continue
                position = target
                if (target == 0) left = true else popstate(entries[target])
            }
        }
        val backEnabled get() = position > 0
        val forwardEnabled get() = position < entries.lastIndex
        fun pressBack() { check(backEnabled); go(-1); settle() }
        fun pressForward() { check(forwardEnabled); go(1); settle() }
    }

    private class Web : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }).also { it.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1", localMode = true) }
        val back = GlobalBack(controller)
        val tab = Tab()
        val history = BrowserBackHistory(tab, { back.canGoBack(controller.state.value) }, { back.canGoForward(controller.state.value) },
            back::back, back::forward)
        init { tab.popstate = history::moved; history.start(); tab.settle() }
        val screen get() = controller.state.value.screen
        fun app(action: ApplicationController.() -> Unit) { controller.action(); history.changed(); tab.settle() }
        fun pressBack() { tab.pressBack(); history.changed(); tab.settle() }
        fun pressForward() { tab.pressForward(); history.changed(); tab.settle() }
        override fun close() { scope.cancel() }
    }

    @Test fun browserBackOnHomeLeavesTheSite() = Web().use { web ->
        assertEquals(Screen.Home, web.screen)
        web.pressBack()
        assertTrue(web.tab.left)
    }

    @Test fun browserBackAndForwardWalkTheAppsScreenHistory() = Web().use { web ->
        web.app { navigate(Screen.Movies) }
        web.app { navigate(Screen.Calendar) }
        assertTrue(web.tab.backEnabled); assertFalse(web.tab.forwardEnabled)
        web.pressBack(); assertEquals(Screen.Movies, web.screen)
        assertTrue(web.tab.forwardEnabled, "Forward must be offered after Back")
        web.pressBack(); assertEquals(Screen.Home, web.screen)
        web.pressForward(); assertEquals(Screen.Movies, web.screen)
        web.pressForward(); assertEquals(Screen.Calendar, web.screen)
        assertFalse(web.tab.forwardEnabled)
        web.pressBack(); web.pressBack(); assertEquals(Screen.Home, web.screen)
        assertFalse(web.tab.left)
        web.pressBack(); assertTrue(web.tab.left, "Back on Home with no history left must leave the site")
    }

    @Test fun aNewPageAfterBackDropsTheForwardStep() = Web().use { web ->
        web.app { navigate(Screen.Movies) }
        web.pressBack(); assertEquals(Screen.Home, web.screen)
        web.app { navigate(Screen.Library) }
        assertFalse(web.tab.forwardEnabled)
        web.pressBack(); assertEquals(Screen.Home, web.screen)
        web.pressForward(); assertEquals(Screen.Library, web.screen)
    }

    @Test fun theInPageArrowAndBrowserBackStayInStep() = Web().use { web ->
        web.app { navigate(Screen.Movies) }
        web.app { navigateBack() }
        assertEquals(Screen.Home, web.screen)
        web.pressForward(); assertEquals(Screen.Movies, web.screen)
        web.app { navigateBack() }
        web.pressBack(); assertTrue(web.tab.left, "Back on Home after the arrow must leave, not stall on a stale entry")
    }

    @Test fun nestedLevelsAndRouteStepsComeFirst() = Web().use { web ->
        web.app { navigate(Screen.Settings) }
        web.app { mutable.value = state.value.copy(settingsPage = SettingsPage("Add-ons", subpage = true)) }
        web.pressBack()
        assertEquals(Screen.Settings, web.screen); assertEquals(SettingsPage(), web.controller.state.value.settingsPage)
        var panelOpen = true
        web.back.routeBackChanged { panelOpen = false }
        web.pressBack()
        assertFalse(panelOpen); assertEquals(Screen.Settings, web.screen)
        web.back.routeBackChanged(null)
        web.pressBack(); assertEquals(Screen.Home, web.screen)
        assertFalse(web.tab.left)
    }

    @Test fun anEntryFromBeforeAReloadKeepsLeaving() {
        val tab = Tab()
        tab.entries.clear(); tab.entries += listOf(null, null, null, null); tab.position = 3
        var backs = 0
        val history = BrowserBackHistory(tab, { false }, { false }, { backs++ }, {})
        tab.popstate = history::moved
        history.start(); tab.settle()
        tab.pressBack()
        assertTrue(tab.left); assertEquals(0, backs)
    }
}
