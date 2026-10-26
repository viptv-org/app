package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate
import kotlin.time.Duration.Companion.seconds

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

/** A lease confirmed this recently still has at least half its 60-second server life. */
private val LEASE_CERTAIN = 30.seconds
private const val RENEW_ATTEMPTS = 5
private const val REACQUIRE_ATTEMPTS = 3
private const val FIRST_RETRY_MILLIS = 1_000L

/** Called from [appForeground]: the app came to the front or went behind. */
internal fun ApplicationController.playbackInFront(front: Boolean) {
    inFront.value = front
    if (front) frontReturns.value++
}

internal fun ApplicationController.keepPlaybackLease(lease: RemotePlaybackLease, source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    leaseConfirmedAt = playbackTime.markNow()
    playbackRenewal = scope.launch {
        val interval = lease.renewSeconds.coerceIn(1, 30) * 1000
        while (true) {
            // One interval, cut short by a return to the front.
            val returns = frontReturns.value
            withTimeoutOrNull(interval) { frontReturns.first { it != returns } }
            awaitLeaseNeeded()
            if (remotePlayback !== lease) return@launch
            if (!renewWithRetry(lease)) break
            leaseConfirmedAt = playbackTime.markNow()
            if (resumeAfterRenewal === input) {
                resumeAfterRenewal = null
                if (mutable.value.playback === input) mutable.value = mutable.value.copy(paused = false)
            }
        }
        if (remotePlayback === lease) reacquirePlayback(source, forceGateway, input)
    }
}

/** True when the lease may have lapsed, so the player must wait for it before using the stream. */
internal fun ApplicationController.leaseUnconfirmed(): Boolean =
    remotePlayback != null && (leaseConfirmedAt?.elapsedNow()?.let { it >= LEASE_CERTAIN } ?: true)

/** Behind with playback paused nothing needs the lease; wait for the app to return or media to play. */
private suspend fun ApplicationController.awaitLeaseNeeded() {
    combine(inFront, mutable) { front, state -> front || !state.paused }.first { it }
}

/** Transient failures retry with a short backoff; false once the lease is lost. */
private suspend fun renewWithRetry(lease: RemotePlaybackLease): Boolean {
    var wait = FIRST_RETRY_MILLIS
    for (attempt in 1..RENEW_ATTEMPTS) {
        try { lease.renew(); return true }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!error.transientFailure() || attempt == RENEW_ATTEMPTS) return false
            delay(wait); wait *= 2
        }
    }
    return false
}

/** Open the same source again where the viewer was, paused only if they had paused. */
private suspend fun ApplicationController.reacquirePlayback(source: SourceCandidate, forceGateway: Boolean, input: PlaybackInput) {
    // Nothing needs a new lease while the app is behind and paused; Android may have no network there.
    awaitLeaseNeeded()
    if (mutable.value.playback !== input) return
    val position = if (input.live) null else lastProgress?.positionMillis ?: input.positionMillis
    val paused = mutable.value.paused && resumeAfterRenewal !== input
    resumeAfterRenewal = null
    if (!completionSeen) saveProgress(false)
    run {
        var wait = FIRST_RETRY_MILLIS
        for (attempt in 1..REACQUIRE_ATTEMPTS) {
            val failure = try { openSourceNow(source, forceGateway, position, pausedAfterOpen = paused); return@run }
                catch (error: TimeoutCancellationException) { error }
                catch (error: CancellationException) { throw error }
                catch (error: RemoteFailure) { if (error.status == 401) throw error else error }
                catch (error: Exception) { error }
            if (attempt == REACQUIRE_ATTEMPTS || !(failure is TimeoutCancellationException || failure.transientFailure())) {
                releaseRemotePlayback()
                val gone = failure is RemoteFailure && failure.status in listOf(404, 410)
                mutable.value = mutable.value.copy(playback = null, screen = Screen.Sources, error =
                    if (gone) "The server playback session ended and this source is no longer available. Choose the source again."
                    else "The server playback session ended and could not be reopened. Choose the source again.")
                return@run
            }
            delay(wait); wait *= 2
        }
    }
}
