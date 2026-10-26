package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

/** Opens a server playback lease for one source. */
internal fun interface PlaybackLeaseOpener {
    suspend fun open(remote: RemoteConnection, source: SourceCandidate, profile: String, forceGateway: Boolean,
        positionMillis: Long?, stage: (String) -> Unit): RemotePlaybackLease

    companion object {
        val Server = PlaybackLeaseOpener { remote, source, profile, forceGateway, positionMillis, stage ->
            remote.openPlayback(source, profile, forceGateway = forceGateway, positionMillis = positionMillis, stage = stage)
        }
    }
}

/*
 * The server forgets a lease 60 s after its last heartbeat and never revives
 * it: a late heartbeat gets 410, later 404. So the lease is kept like this:
 * - a heartbeat every renew_after_seconds while the app is in front or media
 *   still plays behind it (minimised window, hidden tab);
 * - none while the app is behind with playback paused (Android stops the
 *   app's network there), and one at once when it returns;
 * - a lost lease is re-acquired for the same source at the last position;
 *   only when that fails does the viewer go back to Sources.
 */

/** Transient renewal failures retry while the lease can still be alive: 60 s server life less one interval. */
private const val RENEW_RETRY_MILLIS = 40_000L
/** A network outage gets this long to recover before the viewer is sent back to Sources. */
private const val REACQUIRE_RETRY_MILLIS = 60_000L
private const val FIRST_RETRY_MILLIS = 1_000L
private const val MAX_RETRY_MILLIS = 15_000L

/** Called from [appForeground]: the app came to the front or went behind. */
internal fun ApplicationController.playbackInFront(front: Boolean) {
    inFront.value = front
    if (front) leaseWake.value++
}

/**
 * Lifecycle resume after the app was behind. The lease may have lapsed meanwhile
 * (and a sleeping device stops monotonic clocks, so elapsed time cannot tell):
 * keep the player paused until the lease is renewed or re-acquired.
 */
internal fun ApplicationController.resumeAfterLease(input: PlaybackInput) {
    resumeAfterRenewal = input
    leaseWake.value++
}

internal fun ApplicationController.keepPlaybackLease(lease: RemotePlaybackLease, source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    playbackRenewal = scope.launch {
        val interval = lease.renewSeconds.coerceIn(1, 30) * 1000
        while (true) {
            // One interval, cut short by a return to the front or a held resume.
            val wake = leaseWake.value
            withTimeoutOrNull(interval) { leaseWake.first { it != wake } }
            awaitLeaseNeeded()
            if (remotePlayback !== lease) return@launch
            if (retryTransient(RENEW_RETRY_MILLIS) { lease.renew() } != null) break
            if (resumeAfterRenewal === input) {
                resumeAfterRenewal = null
                if (mutable.value.playback === input) mutable.value = mutable.value.copy(paused = false)
            }
        }
        reacquirePlayback(lease, source, forceGateway, input)
    }
}

/** Behind with playback paused nothing needs the lease; wait for the app to return or media to play. */
private suspend fun ApplicationController.awaitLeaseNeeded() {
    combine(inFront, mutable) { front, state -> front || !state.paused }.first { it }
}

/**
 * Runs [block] until it succeeds, fails for a non-transient reason, or the next
 * backoff would exceed [budgetMillis]. Null on success, otherwise the last failure.
 */
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

/** Open the same source again where the viewer was, paused only if they had paused. */
private suspend fun ApplicationController.reacquirePlayback(lease: RemotePlaybackLease, source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    // Nothing needs a new lease while the app is behind and paused; Android may have no network there.
    awaitLeaseNeeded()
    // Let a viewer action already in the job lane finish; if it replaced this playback, there is nothing to reopen.
    job?.join()
    if (remotePlayback !== lease || mutable.value.playback !== input) return
    val position = if (input.live) null else lastProgress?.positionMillis ?: input.positionMillis
    val paused = mutable.value.paused && resumeAfterRenewal !== input
    resumeAfterRenewal = null
    if (!completionSeen) saveProgress(false)
    // The controller's job lane, not this renewal coroutine: opening releases the old
    // lease, which cancels the renewal, and closing the player cancels the reopen.
    run {
        val failure = retryTransient(REACQUIRE_RETRY_MILLIS) { openSourceNow(source, forceGateway, position, pausedAfterOpen = paused) }
            ?: return@run
        if (failure is RemoteFailure && failure.status == 401) throw failure
        releaseRemotePlayback()
        val gone = failure is RemoteFailure && failure.status in listOf(404, 410)
        mutable.value = mutable.value.copy(playback = null, screen = Screen.Sources, error =
            if (gone) "The server playback session ended and this source is no longer available. Choose the source again."
            else "The server playback session ended and could not be reopened. Check the connection and choose the source again.")
    }
}
