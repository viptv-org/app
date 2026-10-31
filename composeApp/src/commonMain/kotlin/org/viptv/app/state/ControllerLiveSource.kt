package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.accountAccess

fun ApplicationController.loadLiveAccountAccess() {
    val state = mutable.value
    if (state.localMode || state.accountAccess != null) return
    val remote = connection ?: return
    val owner = generation
    scope.launch {
        val access = try { remote.accountAccess() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { return@launch }
        if (connection === remote && generation == owner) updateState { next -> next.copy(accountAccess = access) }
    }
}

fun ApplicationController.openLiveSetup(section: String) {
    updateState { next -> next.copy(liveSetupReturnFocus = true, settingsEntrySection = section) }
    navigate(Screen.Settings)
}

fun ApplicationController.consumeLiveSetupFocus() {
    if (mutable.value.liveSetupReturnFocus) updateState { next -> next.copy(liveSetupReturnFocus = false) }
}