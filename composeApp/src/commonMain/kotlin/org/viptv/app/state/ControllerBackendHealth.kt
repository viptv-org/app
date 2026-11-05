package org.viptv.app.state

internal fun ApplicationController.backendOutcome(reachable: Boolean) {
    if (reachable) backend.answered() else backend.unreachable()
}
