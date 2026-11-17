package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.player.PlaybackTracks
import org.viptv.engine.wire.SourceCandidate

internal fun ApplicationController.startBestMatch(ranked: RankedSources, target: SourceCandidate, context: RankingContext? = null) {
    val reuse = context?.takeIf { it.reuse.present && ranked.reuseSupported }
    val ids = if (reuse != null) ranked.reuseIds.toSet()
        else ranked.automaticIds?.toSet()
    val rows = ranked.rows.filter { ids == null || it.id in ids }
    val canExpand = local is PreferredSourceDiscovery || connection?.preferredSourceDiscoverySupported == true
    updateState { it.copy(bestMatch = BestMatchSession(rows, tried = listOf(target.id), playingId = target.id,
        reuseContext = reuse, reuseAudioLanguages = ranked.reuseAudioLanguages,
        expanded = ranked.discoveryPhase != "preferred" || !canExpand), fallbackNotice = null, audioNotice = null) }
    openSource(target, forceGateway = false, positionMillis = null)
}

internal fun ApplicationController.startFailed(cause: PlaybackFailure, dolbyVision: Boolean = false) {
    startDeadlineJob?.cancel(); startDeadlineJob = null
    val reason = cause.reason
    val failure = "$reason. Choose another source."
    val session = mutable.value.bestMatch
    val playing = session?.playingId
    if (session == null || playing == null || firstFrameSeen || session.exhausted) {
        updateState { it.copy(error = failure, playbackFailure = cause) }
        return
    }
    val after = session.failed(playing, dolbyVision)
    val next = after.nextTarget()
    if (next == null && after.reuseContext != null && !after.expanded && after.attempts < BEST_MATCH_ATTEMPT_LIMIT) {
        expandRememberedSources(after); return
    }
    if (next == null || after.attempts >= BEST_MATCH_ATTEMPT_LIMIT) { exhaustFallback(after); return }
    val notice = BestMatchFallback(reason, next)
    updateState { it.copy(bestMatch = after.copy(attempts = after.attempts + 1, playingId = next.id),
        fallbackNotice = notice, error = failure, playbackFailure = cause) }
    fallbackJob?.cancel()
    fallbackJob = scope.launch {
        delay(bestMatchNoticeMillis)
        if (mutable.value.fallbackNotice == notice) openFallback(next)
    }
}

fun ApplicationController.stopBestMatchFallback() {
    fallbackJob?.cancel(); fallbackJob = null
    updateState { it.copy(fallbackNotice = null, bestMatch = null, openingSource = null) }
}

fun ApplicationController.playNextSource() {
    val session = mutable.value.bestMatch ?: return
    val next = session.nextTarget() ?: return
    if (!completionSeen) saveProgress(false)
    updateState { it.copy(bestMatch = null, audioNotice = null) }
    openSource(mutable.value.sources.firstOrNull { it.id == next.id } ?: next, forceGateway = false, positionMillis = null)
}

internal fun ApplicationController.noticeMissingAudio(tracks: PlaybackTracks) {
    val state = mutable.value
    if (state.audioNotice != null) return
    val notice = missingAudioNotice(state.playback?.preferences?.preferred_audio_language, tracks) ?: return
    updateState { it.copy(audioNotice = notice) }
}

internal fun ApplicationController.armStartDeadline(input: org.viptv.app.player.PlaybackInput, source: SourceCandidate) {
    startDeadlineJob?.cancel()
    startDeadlineJob = null
    if (mutable.value.bestMatch == null || source.delivery == "torrent") return
    startDeadlineJob = scope.launch {
        delay(startDeadlineMillis)
        if (mutable.value.playback === input && !firstFrameSeen) startFailed(PlaybackFailure.Timeout)
    }
}

internal fun ApplicationController.clearBestMatchSession() {
    fallbackJob?.cancel(); fallbackJob = null
    startDeadlineJob?.cancel(); startDeadlineJob = null
    if (mutable.value.bestMatch == null && mutable.value.fallbackNotice == null && mutable.value.audioNotice == null) return
    updateState { it.copy(bestMatch = null, fallbackNotice = null, audioNotice = null) }
}

private fun ApplicationController.openFallback(next: SourceCandidate) {
    val source = mutable.value.sources.firstOrNull { it.id == next.id } ?: next
    updateState { it.copy(fallbackNotice = null, audioNotice = null) }
    openSource(source, forceGateway = false, positionMillis = null)
}

private fun ApplicationController.exhaustFallback(session: BestMatchSession) {
    fallbackJob?.cancel(); fallbackJob = null
    playbackSegmentsJob?.cancel()
    releaseRemotePlayback()
    updateState { it.copy(bestMatch = session.copy(exhausted = true), fallbackNotice = null, audioNotice = null,
        playback = null, playbackSegments = emptyList(), playingSource = null, openingSource = null, paused = false,
        presentation = null, screen = Screen.Sources, error = null, playbackFailure = null) }
    lastProgress = null
    firstFrameSeen = false
}

private fun ApplicationController.expandRememberedSources(session: BestMatchSession) {
    val state = mutable.value
    val card = state.sourceTitle ?: return
    val profile = state.selectedProfile ?: return
    val remote = connection
    val device = local
    val context = requireNotNull(session.reuseContext).copy(excludePreferred = true)
    val operation = generation
    val memoryRevision = sourceMemory.revision
    updateState { it.copy(bestMatch = session.copy(expanded = true), findingSources = true, openingSource = null) }
    fallbackJob?.cancel()
    fallbackJob = scope.launch {
        try {
            val outcome = device?.discoverSources(profile, card, context)
                ?: requireNotNull(remote).sources(card, reuse = context.reuse, device = context.device, excludePreferred = true)
            val ranked = device?.rankSources(profile, outcome.candidates, context)
                ?: requireNotNull(remote).rankSources(profile, outcome.candidates, context)
            if (operation != generation || mutable.value.selectedProfile != profile) return@launch
            if (sourcesManualSelection || sourcesScrolled || sourceMemory.revision != memoryRevision) {
                updateState { it.copy(sources = ranked.rows, sourceGroups = ranked.groups, findingSources = false, screen = Screen.Sources) }
                return@launch
            }
            val allowed = if (ranked.reuseSupported) ranked.reuseIds else emptyList()
            val after = session.copy(ranked = ranked.rows.filter { it.id in allowed }, expanded = true, reuseAudioLanguages = ranked.reuseAudioLanguages)
            val target = after.nextTarget()
            updateState { it.copy(sources = ranked.rows, sourceGroups = ranked.groups, findingSources = false) }
            if (target == null) { exhaustFallback(after); return@launch }
            val notice = BestMatchFallback(FALLBACK_REASON_FAILED, target)
            updateState { it.copy(bestMatch = after.copy(attempts = after.attempts + 1, playingId = target.id), fallbackNotice = notice) }
            delay(bestMatchNoticeMillis)
            if (operation == generation && mutable.value.fallbackNotice == notice) openFallback(target)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { if (operation == generation) exhaustFallback(session.copy(expanded = true)) }
    }
}
