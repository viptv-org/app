package org.viptv.app.state

internal fun ApplicationController.backendOutcome(reachable: Boolean) {
    if (reachable) {
        backend.answered()
        if (mutable.value.backendUnreachableSince != null) updateState { it.copy(backendUnreachableSince = null, backendBannerDismissed = false) }
    } else if (backend.unreachable()) {
        updateState { it.copy(backendUnreachableSince = clockMillis(), backendBannerDismissed = false) }
    }
}

fun ApplicationController.dismissBackendBanner() {
    updateState { it.copy(backendBannerDismissed = true) }
}

internal val ApplicationState.backendBannerShown: Boolean
    get() = backendUnreachableSince != null && !backendBannerDismissed && !localMode