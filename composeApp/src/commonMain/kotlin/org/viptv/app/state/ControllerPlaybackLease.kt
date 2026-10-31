package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

internal fun interface PlaybackLeaseOpener {
    suspend fun open(remote: RemoteConnection, source: SourceCandidate, profile: String, forceGateway: Boolean,
        positionMillis: Long?, stage: (String) -> Unit): RemotePlaybackLease

    companion object {
        val Server = PlaybackLeaseOpener { remote, source, profile, forceGateway, positionMillis, stage ->
            remote.openPlayback(source, profile, forceGateway = forceGateway, positionMillis = positionMillis, stage = stage)
        }
    }
}

private const val RENEW_RETRY_MILLIS = 40_000L
private const val REACQUIRE_RETRY_MILLIS = 60_000L
private const val FIRST_RETRY_MILLIS = 1_000L
private const val MAX_RETRY_MILLIS = 15_000L

internal fun ApplicationController.playbackInFront(front: Boolean) {
    inFront.value = front
    if (front) leaseWake.value++
}

// A sleeping device stops monotonic clocks, so elapsed time cannot tell whether the lease lapsed: keep the player paused until it is renewed or re-acquired.
internal fun ApplicationController.resumeAfterLease(input: PlaybackInput) {
    resumeAfterRenewal = input
    leaseWake.value++
}

internal fun ApplicationController.keepPlaybackLease(lease: RemotePlaybackLease, source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    playbackRenewal = scope.launch {
        val interval = lease.renewSeconds.coerceIn(1, 30) * 1000
        while (true) {
            val wake = leaseWake.value
            withTimeoutOrNull(interval) { leaseWake.first { it != wake } }
            awaitLeaseNeeded()
            if (remotePlayback !== lease) return@launch
            if (retryTransient(RENEW_RETRY_MILLIS) { lease.renew() } != null) break
            if (resumeAfterRenewal === input) {
                resumeAfterRenewal = null
                if (mutable.value.playback === input) updateState { next -> next.copy(paused = false) }
            }
        }
        reacquirePlayback(lease, source, forceGateway, input)
    }
}

private suspend fun ApplicationController.awaitLeaseNeeded() {
    combine(inFront, mutable) { front, state -> front || !state.paused }.first { it }
}

private suspend fun retryTransient(budgetMillis: Long, block: suspend () -> Unit): Exception? {
    var wait = FIRST_RETRY_MILLIS
    var spent = 0L
    while (true) {
        val failure = try { block(); return null }
            catch (error: CancellationException) { if (error is TimeoutCancellationException) error else throw error }
            catch (error: Exception) { error }
        if (!failure.transientFailure() || spent + wait > budgetMillis) return failure
        delay(wait); spent += wait; wait = (wait * 2).coerceAtMost(MAX_RETRY_MILLIS)
    }
}

private suspend fun ApplicationController.reacquirePlayback(lease: RemotePlaybackLease, source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    awaitLeaseNeeded()
    job?.join()
    if (remotePlayback !== lease || mutable.value.playback !== input) return
    val position = if (input.live) null else lastProgress?.positionMillis ?: input.positionMillis
    val paused = mutable.value.paused && resumeAfterRenewal !== input
    resumeAfterRenewal = null
    if (!completionSeen) saveProgress(false)
    // Open on the controller's job lane: opening releases the old lease, which cancels this renewal.
    run {
        val failure = retryTransient(REACQUIRE_RETRY_MILLIS) { openSourceNow(source, forceGateway, position, pausedAfterOpen = paused) }
            ?: return@run
        if (failure is RemoteFailure && failure.status == 401) throw failure
        releaseRemotePlayback()
        val gone = failure is RemoteFailure && failure.status in listOf(404, 410)
        updateState { next -> next.copy(playback = null, screen = Screen.Sources, error =
            if (gone) "The server playback session ended and this source is no longer available. Choose the source again."
            else "The server playback session ended and could not be reopened. Check the connection and choose the source again.") }
    }
}
