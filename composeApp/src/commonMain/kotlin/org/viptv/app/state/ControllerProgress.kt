package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

internal fun ApplicationController.releaseRemotePlayback(): Job? {
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

internal data class ProgressWrite(val sequence: Long, val input: PlaybackInput, val card: Card, val positionMillis: Long, val durationMillis: Long?,
    val completed: Boolean, val remote: RemoteConnection?, val store: LocalConnection?)

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
    val write=ProgressWrite(++progressSequence, input, title, position, snapshot.durationMillis, completed, remote, store)
    progressSavedAt=TimeSource.Monotonic.markNow()
    return scope.launch(start = if (final) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
        withContext(if (final) NonCancellable else currentCoroutineContext()) { settleProgress(write) }
    }.also { progressJob=it }
}

private suspend fun ApplicationController.settleProgress(write: ProgressWrite) {
    val failure = writeProgress(write)
    if (failure != null && failure.transientFailure()) {
        if ((pendingProgress?.sequence ?: 0) <= write.sequence) pendingProgress = write
        retryProgress()
    } else clearPendingProgress(write.sequence)
}

private suspend fun ApplicationController.writeProgress(write: ProgressWrite): Exception? {
    if (connection !== write.remote || local !== write.store) return null
    return try {
        withTimeout(5_000) { progressGate.withLock {
            val store = write.store
            if (store != null) store.recordProgress(write.input, write.card, write.positionMillis, write.durationMillis)
            else requireNotNull(write.remote).recordProgress(write.input, write.card, write.positionMillis, write.durationMillis, ended = write.completed)
        } }
        null
    }
    catch (error: CancellationException) { if (error is TimeoutCancellationException) error else throw error }
    catch (error: Exception) { error }
}

private fun ApplicationController.latestPosition(write: ProgressWrite): ProgressWrite {
    if (mutable.value.playback !== write.input) return write
    val snapshot = lastProgress ?: return write
    val position = if (write.completed && snapshot.durationMillis != null) snapshot.durationMillis else snapshot.positionMillis
    return if (position <= 0) write else write.copy(positionMillis = position, durationMillis = snapshot.durationMillis ?: write.durationMillis)
}

private fun ApplicationController.clearPendingProgress(through: Long) {
    if ((pendingProgress?.sequence ?: 0) <= through) pendingProgress = null
}

private fun ApplicationController.retryProgress() {
    if (progressRetryJob?.isActive == true) return
    progressRetryJob = scope.launch {
        var attempts = 0
        var sequence = pendingProgress?.sequence ?: return@launch
        while (true) {
            delay(progressRetryDelays[attempts])
            val write = pendingProgress ?: return@launch
            if (write.sequence != sequence) { sequence = write.sequence; attempts = 0 } else attempts++
            val failure = writeProgress(latestPosition(write))
            if (failure != null && failure.transientFailure() && attempts < progressRetryDelays.size) continue
            clearPendingProgress(write.sequence)
            val next = pendingProgress ?: return@launch
            sequence = next.sequence
            attempts = 0
        }
    }.also { job -> job.invokeOnCompletion { cause -> if (cause == null && pendingProgress != null) retryProgress() } }
}
