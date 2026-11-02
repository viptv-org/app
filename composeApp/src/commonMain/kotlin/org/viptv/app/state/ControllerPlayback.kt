package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.crash.CrashFacts
import org.viptv.app.crash.playbackAttempt
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

fun ApplicationController.playSource(source: SourceCandidate) = playSource(source, forceGateway = false)

fun ApplicationController.playSource(source: SourceCandidate, forceGateway: Boolean) {
    val current = mutable.value.sources.firstOrNull { it.id == source.id } ?: return
    val restart = restartRequested
    restartRequested = false
    openSource(current, forceGateway, positionMillis = if (restart) 0L else null)
}

internal fun ApplicationController.openSource(source: SourceCandidate, forceGateway: Boolean, positionMillis: Long? = null, pausedAfterOpen: Boolean = false) {
    updateState { next -> next.copy(playingSource = source, openingSource = source) }
    run { openSourceNow(source, forceGateway, positionMillis, pausedAfterOpen) }
}

internal suspend fun ApplicationController.openSourceNow(source: SourceCandidate, forceGateway: Boolean, positionMillis: Long?, pausedAfterOpen: Boolean) {
    progressJob?.join()
    val stopping = releaseRemotePlayback()
    updateState { next -> next.copy(playback = null, playbackSegments = emptyList(), presentation = null, findingSources = false) }
    stopping?.join()
    val store = local
    val remote = connection
    val profile = requireNotNull(mutable.value.selectedProfile)
    val title = mutable.value.sourceTitle
    if (mutable.value.preferences == null) loadPreferences()
    val preferences = mutable.value.preferences?.preferences ?: ProfilePreferences()
    val lease = if (store == null) try {
        leaseOpener.open(requireNotNull(remote), source, profile, forceGateway, positionMillis) { text -> updateState { next -> next.copy(openingStage = text) } }
    } finally { updateState { next -> next.copy(openingStage = null) } } else null
    val opened = store?.openSource(source.id) ?: requireNotNull(lease).input
    // An open outlives the picker that started it: another profile or title meanwhile owns the player.
    if (mutable.value.selectedProfile != profile || mutable.value.sourceTitle?.let(::cardLookupKey) != title?.let(::cardLookupKey)) {
        withContext(NonCancellable) { runCatching { lease?.close() } }
        return
    }
    val card = requireNotNull(title)
    val input = PlaybackInput(opened.id, opened.uri, opened.headers, opened.positionMillis, opened.profileId,
        opened.revision, timelineOffsetMillis = opened.timelineOffsetMillis, durationMillis = opened.durationMillis, preferences = preferences, live = opened.live,
        diagnostics = playbackDiagnostics, mimeType = opened.mimeType ?: org.viptv.app.player.playbackMimeType(source.presentation?.delivery_format),
        requiredAudioIndex = opened.requiredAudioIndex, localTorrent = opened.localTorrent,
        remoteSeek = if (lease?.reopensForSeek == true) { target ->
            if (mutable.value.playingSource?.id == source.id && mutable.value.selectedProfile == profile) {
                saveProgress(false); openSource(source, forceGateway = true, positionMillis = target, pausedAfterOpen = mutable.value.paused)
            }
        } else null,
        subtitleContent = { id ->
            // A device-local store owns its subtitles: a missing track is empty, never a remote call it cannot make.
            if (store != null) store.subtitleContent(id, opened) ?: ByteArray(0)
            else requireNotNull(remote).subtitleContent(id).encodeToByteArray()
        })
    remotePlayback = lease
    CrashFacts.playbackAttempt = playbackAttempt(source, when {
        store != null -> "direct"
        lease?.gateway == true -> "gateway"
        else -> "direct"
    })
    tracking = if (lease != null && remote != null) {
        val events = RemotePlaybackEvents(remote, input, card)
        PlaybackTracking(scope, { event, position, duration, ended -> events.emit(event, position, duration, ended); Unit }, {
            if (mutable.value.playback === input) updateState { next -> next.copy(error = "Could not update your tracking account.") }
        })
    } else null
    firstFrameSeen = false
    if (lease != null) keepPlaybackLease(lease, source, forceGateway, input)
    lastProgress=null
    completionSeen=false
    progressSavedAt=TimeSource.Monotonic.markNow()
    val screen = if (mutable.value.inPlayerSettings()) Screen.Settings else Screen.Player
    updateState { next -> next.copy(playback = input, playingSource = source, paused = pausedAfterOpen, presentation = null, screen = screen, error = null, navForward = emptyList()) }
    loadNextEpisode()
    playbackSegmentsJob?.cancel()
    playbackSegmentsJob = scope.launch {
        val segments = try { withTimeoutOrNull(8_000) { lease?.segments() } ?: emptyList() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { emptyList() }
        if (mutable.value.playback === input) updateState { next -> next.copy(playbackSegments = segments) }
    }
    subtitleDiscovery = scope.launch {
        try {
            val snapshot = store?.subtitles(profile, card) ?: requireNotNull(remote).subtitles(card)
            if (mutable.value.playback === input && mutable.value.selectedProfile == profile)
                input.updateProviderSubtitles(snapshot.candidates)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }
}

fun ApplicationController.retrySource(source: SourceCandidate) {
    if (!completionSeen) saveProgress(false)
    playSource(source)
}

fun ApplicationController.pausePlayback() {
    backgroundPlayback = null
    resumeAfterRenewal = null
    val paused=!mutable.value.paused
    if (!paused) completionSeen = false
    updateState { next -> next.copy(paused = paused) }
    if(paused && !completionSeen) saveProgress(false)
}

fun ApplicationController.suspendPlayback() {
    val state = mutable.value
    if (state.playback == null || backgroundPlayback != null) return
    if (!state.paused) {
        backgroundPlayback = state.playback
        updateState { next -> next.copy(paused = true) }
    }
    if (!completionSeen) saveProgress(false, final = true)
}

fun ApplicationController.resumePlayback() {
    val input = backgroundPlayback ?: return
    backgroundPlayback = null
    if (mutable.value.playback !== input || completionSeen) return
    if (remotePlayback != null) resumeAfterLease(input)
    else updateState { next -> next.copy(paused = false) }
}

fun ApplicationController.observePlaybackFor(input: PlaybackInput?, fact: PlayerFact) {
    if (input != null && mutable.value.playback === input) observePlayback(fact)
}

internal fun ApplicationController.observePlayback(fact: PlayerFact) {
    if (fact is PlayerFact.Presentation && !firstFrameSeen) {
        updateState { next -> next.copy(presentation = fact, openingSource = null) }
        firstFrameSeen = true
        if (!mutable.value.paused) tracking?.transition(PlaybackEvent.Start, lastProgress)
        val capturedInput = mutable.value.playback
        remotePlayback?.let { lease -> frameAcknowledgment = scope.launch {
            fun active() = remotePlayback === lease && mutable.value.playback === capturedInput
            try { acknowledgePlaybackFrame(::active, lease::firstFrame) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (active()) updateState { next -> next.copy(error = "Could not confirm playback with the server.") } }
        } }
    }
    if (fact is PlayerFact.Progress) {
        val finalBackgroundSample = backgroundPlayback === mutable.value.playback && backgroundPlayback != null && fact != lastProgress
        lastProgress=fact
        // ON_STOP pauses the renderer after the controller suspends: flush this last sample even if the 15s timer just ran.
        if (finalBackgroundSample && !completionSeen) saveProgress(false, final = true)
        else if (fact.positionMillis>0 && progressSavedAt.elapsedNow()>=15.seconds) saveProgress(false)
    }
    if (fact is PlayerFact.Status && fact.name in listOf("Playing", "Paused") && mutable.value.playback != null) {
        updateState { next -> next.copy(paused = fact.name == "Paused") }
        if (firstFrameSeen) tracking?.transition(if (fact.name == "Paused") PlaybackEvent.Pause else PlaybackEvent.Start, lastProgress)
    }
    if (fact is PlayerFact.Status && fact.name=="Ended" && !completionSeen) {
        completionSeen=true
        if (firstFrameSeen) tracking?.transition(PlaybackEvent.Stop, lastProgress, ended = true)
        updateState { next -> next.copy(paused = true) }
        if (mutable.value.nextEpisode == null) loadNextEpisode(force = true)
        saveProgress(true)
    }
    if (fact is PlayerFact.Failed) updateState { next -> next.copy(error = "This source could not play. Choose another source.") }
}

fun ApplicationController.closePlayer() {
    job?.cancel()
    playbackSegmentsJob?.cancel()
    val live = mutable.value.playback?.live == true
    releaseRemotePlayback()
    if(!completionSeen) saveProgress(false, final = true)
    updateState { next -> next.copy(playback = null, playbackSegments = emptyList(), screen = Screen.Sources, error = null, busy = false, openingSource = null) }
    lastProgress=null
    afterPlayback(live)
}
