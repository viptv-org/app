package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.RankedSources
import org.viptv.app.player.PlaybackTracks
import org.viptv.engine.wire.SourceCandidate

internal fun ApplicationController.startBestMatch(ranked: RankedSources, target: SourceCandidate) {
    updateState { it.copy(bestMatch = BestMatchSession(ranked.rows, tried = listOf(target.id), playingId = target.id),
        fallbackNotice = null, audioNotice = null) }
    openSource(target, forceGateway = false, positionMillis = null)
}

internal fun ApplicationController.startFailed(reason: String, dolbyVision: Boolean = false) {
    startDeadlineJob?.cancel(); startDeadlineJob = null
    val failure = "$reason. Choose another source."
    val session = mutable.value.bestMatch
    val playing = session?.playingId
    if (session == null || playing == null || session.started || session.exhausted) {
        updateState { it.copy(error = failure) }
        return
    }
    val after = session.failed(playing, dolbyVision)
    val next = after.nextTarget()
    if (next == null || after.attempts >= BEST_MATCH_ATTEMPT_LIMIT) { exhaustFallback(after); return }
    val notice = BestMatchFallback(reason, next)
    updateState { it.copy(bestMatch = after.copy(attempts = after.attempts + 1, playingId = next.id),
        fallbackNotice = notice, error = failure) }
    fallbackJob?.cancel()
    fallbackJob = scope.launch {
        delay(bestMatchNoticeMillis)
        if (mutable.value.fallbackNotice == notice) openFallback(next)
    }
}

fun ApplicationController.stopBestMatchFallback() {
    fallbackJob?.cancel(); fallbackJob = null
    updateState { it.copy(fallbackNotice = null, bestMatch = null) }
}

fun ApplicationController.playNextSource() {
    val session = mutable.value.bestMatch ?: return
    val next = session.nextTarget() ?: return
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
        if (mutable.value.playback === input && !firstFrameSeen) startFailed(FALLBACK_REASON_TIMEOUT)
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
        playback = null, playbackSegments = emptyList(), playingSource = null, paused = false,
        presentation = null, screen = Screen.Sources, error = null) }
    lastProgress = null
    firstFrameSeen = false
}
