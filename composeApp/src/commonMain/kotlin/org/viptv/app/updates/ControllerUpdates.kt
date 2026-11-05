package org.viptv.app.updates

import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen
import org.viptv.app.state.updateState

fun ApplicationController.updateServerOrigin(): String? {
    val current = state.value
    return if (current.starting || current.localMode) null else connection?.origin
}

fun ApplicationController.updatePromptAllowed(): Boolean {
    val current = state.value
    return !current.starting && current.screen != Screen.Player && current.screen != Screen.Connect &&
        current.screen != Screen.RecoveryCodes
}

// A stale client is told on every screen it can still reach, sign-in included: nothing else there can work.
fun ApplicationController.updateRequiredNoticeAllowed(): Boolean = state.value.screen != Screen.Player

fun ApplicationController.dismissUpdateRequired() = updateState { it.copy(updateRequired = false) }
