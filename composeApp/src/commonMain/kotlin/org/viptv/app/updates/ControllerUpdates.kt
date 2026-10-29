package org.viptv.app.updates

import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen

fun ApplicationController.updateServerOrigin(): String? {
    val current = state.value
    return if (current.starting || current.localMode) null else connection?.origin
}

fun ApplicationController.updatePromptAllowed(): Boolean {
    val current = state.value
    return !current.starting && current.screen != Screen.Player && current.screen != Screen.Connect &&
        current.screen != Screen.RecoveryCodes
}
