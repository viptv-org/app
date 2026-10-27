package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

fun ApplicationController.playSource(source: SourceCandidate) = playSource(source, forceGateway = false)

fun ApplicationController.playSource(source: SourceCandidate, forceGateway: Boolean) {
    // A queued row click can outlive the title/discovery that rendered it.
    // Resolve the current choice by ID; display/tracking must use its metadata.
    val current = mutable.value.sources.firstOrNull { it.id == source.id } ?: return
    val restart = restartRequested
    restartRequested = false
    openSource(current, forceGateway, positionMillis = if (restart) 0L else null)
}

internal fun ApplicationController.openSource(source: SourceCandidate, forceGateway: Boolean, positionMillis: Long? = null, pausedAfterOpen: Boolean = false) =
    run { openSourceNow(source, forceGateway, positionMillis, pausedAfterOpen) }

internal suspend fun ApplicationController.openSourceNow(source: SourceCandidate, forceGateway: Boolean, positionMillis: Long?, pausedAfterOpen: Boolean) {
    progressJob?.join()
    val stopping = releaseRemotePlayback()
    mutable.value = mutable.value.copy(playback = null, presentation = null, findingSources = false)
    stopping?.join()
    val store = local
    val remote = connection
    val profile = requireNotNull(mutable.value.selectedProfile)
    if (mutable.value.preferences == null) loadPreferences()
    val preferences = mutable.value.preferences?.preferences ?: ProfilePreferences()
    val lease = if (store == null) try {
        leaseOpener.open(requireNotNull(remote), source, profile, forceGateway, positionMillis) { text -> mutable.value = mutable.value.copy(openingStage = text) }
    } finally { mutable.value = mutable.value.copy(openingStage = null) } else null
    val opened = store?.openSource(source.id) ?: requireNotNull(lease).input
    val card = requireNotNull(mutable.value.sourceTitle)
    val input = PlaybackInput(opened.id, opened.uri, opened.headers, opened.positionMillis, opened.profileId,
        opened.revision, timelineOffsetMillis = opened.timelineOffsetMillis, durationMillis = opened.durationMillis, preferences = preferences, live = opened.live,
        diagnostics = playbackDiagnostics, mimeType = opened.mimeType ?: org.viptv.app.player.playbackMimeType(source.presentation?.delivery_format),
        requiredAudioIndex = opened.requiredAudioIndex, localTorrent = opened.localTorrent,
        remoteSeek = if (lease?.reopensForSeek == true) { target ->
            if (mutable.value.playingSource?.id == source.id && mutable.value.selectedProfile == profile) {
                saveProgress(false); openSource(source, forceGateway = true, positionMillis = target, pausedAfterOpen = mutable.value.paused)
            }
        } else null,
        // Local playback never asks the server for subtitles: a device-local store
        // owns them, and a missing track must not fall through to a remote call it
        // cannot make.
        subtitleContent = { id ->
            if (store != null) store.subtitleContent(id, opened) ?: ByteArray(0)
            else requireNotNull(remote).subtitleContent(id).encodeToByteArray()
        })
    remotePlayback = lease
    tracking = if (lease != null && remote != null) {
        val events = RemotePlaybackEvents(remote, input, card)
        PlaybackTracking(scope, { event, position, duration, ended -> events.emit(event, position, duration, ended); Unit }, {
            if (mutable.value.playback === input) mutable.value = mutable.value.copy(error = "Could not update your tracking account.")
        })
    } else null
    firstFrameSeen = false
    if (lease != null) keepPlaybackLease(lease, source, forceGateway, input)
    lastProgress=null
    completionSeen=false
    progressSavedAt=TimeSource.Monotonic.markNow()
    // A lease re-acquired during a player visit to Settings waits behind it (app#44).
    val screen = if (mutable.value.inPlayerSettings()) Screen.Settings else Screen.Player
    mutable.value = mutable.value.copy(playback = input, playingSource = source, paused = pausedAfterOpen, presentation = null, screen = screen, error = null, navForward = emptyList())
    loadNextEpisode()
    subtitleDiscovery = scope.launch {
        try {
            val snapshot = store?.subtitles(profile, card) ?: requireNotNull(remote).subtitles(card)
            if (mutable.value.playback === input && mutable.value.selectedProfile == profile)
                input.updateProviderSubtitles(snapshot.candidates)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Subtitle discovery cannot interrupt media playback. */ }
    }
}

fun ApplicationController.retrySource(source: SourceCandidate) {
    if (!completionSeen) saveProgress(false)
    // R6: desktop/phone retry with the device engine on the direct URL; a live
    // or IPTV retry is not a reason to open a server gateway.
    playSource(source)
}

fun ApplicationController.pausePlayback() {
    backgroundPlayback = null
    resumeAfterRenewal = null
    val paused=!mutable.value.paused
    if (!paused) completionSeen = false
    mutable.value = mutable.value.copy(paused = paused)
    if(paused && !completionSeen) saveProgress(false)
}

/** Keep the route and lease while the Activity is away; save the last renderer clock. */
fun ApplicationController.suspendPlayback() {
    val state = mutable.value
    if (state.playback == null || state.paused || backgroundPlayback != null) return
    backgroundPlayback = state.playback
    mutable.value = state.copy(paused = true)
    if (!completionSeen) saveProgress(false, final = true)
}

fun ApplicationController.resumePlayback() {
    val input = backgroundPlayback ?: return
    backgroundPlayback = null
    if (mutable.value.playback !== input || completionSeen) return
    if (remotePlayback != null) resumeAfterLease(input)
    else mutable.value = mutable.value.copy(paused = false)
}

fun ApplicationController.observePlaybackFor(input: PlaybackInput?, fact: PlayerFact) {
    if (input != null && mutable.value.playback === input) observePlayback(fact)
}

internal fun ApplicationController.observePlayback(fact: PlayerFact) {
    if (fact is PlayerFact.Presentation && !firstFrameSeen) {
        mutable.value = mutable.value.copy(presentation = fact)
        firstFrameSeen = true
        if (!mutable.value.paused) tracking?.transition(PlaybackEvent.Start, lastProgress)
        val capturedInput = mutable.value.playback
        remotePlayback?.let { lease -> frameAcknowledgment = scope.launch {
            fun active() = remotePlayback === lease && mutable.value.playback === capturedInput
            try { acknowledgePlaybackFrame(::active, lease::firstFrame) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (active()) mutable.value = mutable.value.copy(error = "Could not confirm playback with the server.") }
        } }
    }
    if (fact is PlayerFact.Progress) {
        val finalBackgroundSample = backgroundPlayback === mutable.value.playback && backgroundPlayback != null && fact != lastProgress
        lastProgress=fact
        // ON_STOP pauses the renderer synchronously after the Activity suspends
        // the controller. Flush that final sample even if the 15s timer just ran.
        if (finalBackgroundSample && !completionSeen) saveProgress(false, final = true)
        else if (fact.positionMillis>0 && progressSavedAt.elapsedNow()>=15.seconds) saveProgress(false)
    }
    if (fact is PlayerFact.Status && fact.name in listOf("Playing", "Paused") && mutable.value.playback != null) {
        mutable.value = mutable.value.copy(paused = fact.name == "Paused")
        if (firstFrameSeen) tracking?.transition(if (fact.name == "Paused") PlaybackEvent.Pause else PlaybackEvent.Start, lastProgress)
    }
    if (fact is PlayerFact.Status && fact.name=="Ended" && !completionSeen) {
        completionSeen=true
        if (firstFrameSeen) tracking?.transition(PlaybackEvent.Stop, lastProgress, ended = true)
        mutable.value = mutable.value.copy(paused = true)
        if (mutable.value.nextEpisode == null) loadNextEpisode(force = true)
        saveProgress(true)
    }
    if (fact is PlayerFact.Failed) mutable.value = mutable.value.copy(error = "This source could not play. Choose another source.")
}

fun ApplicationController.closePlayer() {
    job?.cancel()
    val live = mutable.value.playback?.live == true
    releaseRemotePlayback()
    if(!completionSeen) saveProgress(false, final = true)
    mutable.value = mutable.value.copy(playback = null, screen = Screen.Sources, error = null, busy = false)
    lastProgress=null
    afterPlayback(live)
}
