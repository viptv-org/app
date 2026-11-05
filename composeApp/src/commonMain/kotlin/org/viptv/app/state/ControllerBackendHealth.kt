package org.viptv.app.state

internal fun ApplicationController.backendOutcome(reachable: Boolean) {
    if (reachable) {
        backend.answered()
        if (mutable.value.backendUnreachableSince != null) {
            updateState { it.copy(backendUnreachableSince = null, backendRetrySeconds = 0, backendBannerDismissed = false) }
        }
        return
    }
    val started = backend.unreachable()
    updateState {
        it.copy(backendUnreachableSince = if (started) clockMillis() else it.backendUnreachableSince,
            backendRetrySeconds = backend.retrySeconds,
            backendBannerDismissed = it.backendBannerDismissed && !started)
    }
}

fun ApplicationController.dismissBackendBanner() {
    updateState { it.copy(backendBannerDismissed = true) }
}

internal val ApplicationState.backendBannerShown: Boolean
    get() = backendUnreachableSince != null && !backendBannerDismissed && !localMode