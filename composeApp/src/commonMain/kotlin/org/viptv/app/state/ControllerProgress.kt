package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

internal fun ApplicationController.releaseRemotePlayback(): Job? {
    cancelNextEpisode()
    cancelPlaybackSeason()
    cancelPlaybackStall()
    sourceChoice = SourceChoice.None
    backgroundPlayback = null
    resumeAfterRenewal = null
    val trackingStop = if (firstFrameSeen) tracking?.transition(PlaybackEvent.Stop, lastProgress, ended = completionSeen) else null
    tracking = null
    val acknowledgment = frameAcknowledgment
    acknowledgment?.cancel(); frameAcknowledgment = null
    subtitleDiscovery?.cancel(); subtitleDiscovery = null
    playbackRenewal?.cancel(); playbackRenewal = null
    val lease = remotePlayback ?: return null
    remotePlayback = null
    return scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { withTimeoutOrNull(5_000) { try { trackingStop?.join(); acknowledgment?.join(); lease.close() } catch (_: Exception) { } } } }
}

internal fun ApplicationController.releasePlaybackSession(): Job? {
    val releasingPrepared = cancelNextEpisodePreparation()
    val releasingPlayback = releaseRemotePlayback()
    return when {
        releasingPrepared == null -> releasingPlayback
        releasingPlayback == null -> releasingPrepared
        else -> scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { releasingPrepared.join(); releasingPlayback.join() }
        }
    }
}

// Detach the outgoing playback without releasing it server-side: promotion's replaces does that in one step with the start.
internal fun ApplicationController.detachRemotePlaybackForPromotion(): Pair<RemotePlaybackLease?, Job?> {
    cancelNextEpisode()
    cancelPlaybackSeason()
    sourceChoice = SourceChoice.None
    backgroundPlayback = null
    resumeAfterRenewal = null
    val trackingStop = if (firstFrameSeen) tracking?.transition(PlaybackEvent.Stop, lastProgress, ended = completionSeen) else null
    tracking = null
    val acknowledgment = frameAcknowledgment
    acknowledgment?.cancel(); frameAcknowledgment = null
    subtitleDiscovery?.cancel(); subtitleDiscovery = null
    playbackRenewal?.cancel(); playbackRenewal = null
    val lease = remotePlayback ?: return null to null
    remotePlayback = null
    return lease to scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { withTimeoutOrNull(5_000) { try { trackingStop?.join(); acknowledgment?.join() } catch (_: Exception) { } } } }
}

internal suspend fun ApplicationController.closePlaybackQuietly(lease: RemotePlaybackLease?) {
    if (lease == null) return
    withContext(NonCancellable) { withTimeoutOrNull(5_000) { try { lease.close() } catch (_: Exception) { } } }
}

internal suspend fun ApplicationController.closeForExit(timeoutMillis: Long, onExit: () -> Unit) {
    withTimeoutOrNull(timeoutMillis) {
        if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
        releasePlaybackSession()?.join()
    }
    onExit()
}

internal fun ApplicationController.saveProgress(completed: Boolean, final: Boolean = false): Job? {
    val input=mutable.value.playback ?: return null
    if (input.live) return null
    val sourceTitle=mutable.value.sourceTitle ?: return null
    val parent = mutable.value.detail?.item?.takeIf {
        it.identity.id == sourceTitle.parent_identity?.id && it.identity.kind == sourceTitle.parent_identity?.kind && it.metadata_origin == sourceTitle.metadata_origin
    }
    val title = if (parent != null) sourceTitle.copy(title = parent.title) else sourceTitle
    val snapshot=lastProgress ?: return null
    val store=local
    val remote=connection
    if(store==null && remote==null)return null
    val position=if(completed && snapshot.durationMillis!=null) snapshot.durationMillis else snapshot.positionMillis
    if(position<=0)return null
    progressSavedAt=TimeSource.Monotonic.markNow()
    return scope.launch(start = if (final) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
        withContext(if (final) NonCancellable else currentCoroutineContext()) {
        withTimeoutOrNull(5_000) { progressGate.withLock {
            try {
                if (store != null) store.recordProgress(input,title,position,snapshot.durationMillis)
                else requireNotNull(remote).recordProgress(input,title,position,snapshot.durationMillis,ended=completed)
            }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if(mutable.value.playback===input) updateState { next -> next.copy(error="Could not save your viewing position.") }
            }
        } }
        }
    }.also { progressJob=it }
}
