package org.viptv.app.updates

import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen

/** The origin of the server this app is signed in to; null in local mode, signed out or while starting. */
fun ApplicationController.updateServerOrigin(): String? {
    val current = state.value
    return if (current.starting || current.localMode) null else connection?.origin
}

/** The update prompt stays off the player and the sign-in screens. */
fun ApplicationController.updatePromptAllowed(): Boolean {
    val current = state.value
    return !current.starting && current.screen != Screen.Player && current.screen != Screen.Connect &&
        current.screen != Screen.RecoveryCodes
}
