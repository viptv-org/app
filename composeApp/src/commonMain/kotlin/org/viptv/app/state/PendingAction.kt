package org.viptv.app.state

import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

// One in-flight action per app. Repeating a pending card cancels it; repeating a pending source is a no-op.
internal fun ApplicationState.hasPending(key: String): Boolean = pendingAction != null && pendingAction == key

internal fun ApplicationState.playPending(card: Card): Boolean = hasPending(cardLookupKey(card))

internal fun ApplicationState.playPending(source: SourceCandidate): Boolean = hasPending(source.id)

internal fun ApplicationController.beginPendingAction(key: String): Boolean {
    if (mutable.value.pendingAction == key) {
        cancelPendingAction()
        return false
    }
    updateState { it.copy(pendingAction = key) }
    return true
}

internal fun ApplicationController.beginPendingPlayback(card: Card): Boolean = beginPendingAction(cardLookupKey(card))

internal fun ApplicationController.beginPendingSource(source: SourceCandidate): Boolean {
    if (mutable.value.pendingAction == source.id) return false
    updateState { it.copy(pendingAction = source.id) }
    return true
}

internal fun ApplicationController.cancelPendingAction() {
    sourceDetailJob?.cancel(); sourceDetailJob = null
    generation++
    job?.cancel()
    updateState { it.copy(pendingAction = null, findingSources = false, openingSource = null, busy = false) }
}

internal fun ApplicationController.clearPendingAction() {
    if (mutable.value.pendingAction != null) updateState { it.copy(pendingAction = null) }
}