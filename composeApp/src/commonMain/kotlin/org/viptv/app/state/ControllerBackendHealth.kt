package org.viptv.app.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ApplicationController.backendOutcome(reachable: Boolean) {
    if (reachable) {
        backend.answered()
        if (mutable.value.backendUnreachableSince != null) {
            updateState { it.copy(backendUnreachableSince = null, backendRetrySeconds = 0, backendBannerDismissed = false) }
            if (downloadEngine != null) scope.launch { syncOfflineProgress() }
        }
        return
    }
    val started = backend.unreachable()
    updateState {
        it.copy(backendUnreachableSince = if (started) clockMillis() else it.backendUnreachableSince,
            backendRetrySeconds = backend.retrySeconds,
            backendBannerDismissed = it.backendBannerDismissed && !started)
    }
    if (started) probeBackend()
}

private fun ApplicationController.probeBackend() {
    val remote = connection ?: return
    if (healthProbe?.isActive == true) return
    healthProbe = scope.launch {
        while (connection === remote && mutable.value.backendUnreachableSince != null) {
            remote.probeHealth(healthProbeTimeoutMillis)
            if (mutable.value.backendUnreachableSince == null) return@launch
            delay(backend.retrySeconds * 1_000L)
        }
    }
}

fun ApplicationController.dismissBackendBanner() {
    updateState { it.copy(backendBannerDismissed = true) }
}

internal val ApplicationState.backendBannerShown: Boolean
    get() = backendUnreachableSince != null && !backendBannerDismissed && !localMode