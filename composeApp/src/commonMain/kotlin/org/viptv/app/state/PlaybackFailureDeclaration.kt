package org.viptv.app.state

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteFailure
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact

internal enum class PlaybackFailureOrigin(val wire: String, val failure: PlaybackFailure, val renderer: Boolean) {
    Renderer("renderer", PlaybackFailure.Failed, true),
    Undecodable("undecodable", PlaybackFailure.Undecodable, true),
    Stall("stall", PlaybackFailure.Failed, false),
    StartTimeout("start_timeout", PlaybackFailure.Timeout, false),
    OpenFailed("open_failed", PlaybackFailure.Failed, false),
}

internal data class PlaybackFailureRecord(val origin: PlaybackFailureOrigin, val operation: String?, val status: Int?,
    val positionMillis: Long?, val firstFrameSeen: Boolean, val playbackId: String?)

internal class DeclaredPlaybackFailure(val origin: PlaybackFailureOrigin, val input: PlaybackInput?, val positionMillis: Long?)

internal const val PLAYBACK_FAILURES_PATH = "/api/playback-failures"

internal fun ApplicationController.declarePlaybackFailure(origin: PlaybackFailureOrigin, input: PlaybackInput?,
    operation: String? = null, status: Int? = null): Boolean {
    if (input != null && mutable.value.playback !== input) return false
    startDeadlineJob?.cancel(); startDeadlineJob = null
    cancelPlaybackStall()
    val position = if (input != null) lastProgress?.positionMillis else null
    declaredFailure = DeclaredPlaybackFailure(origin, input, position)
    val playbackId = input?.id?.takeIf { remotePlayback?.input?.id == it }
    reportPlaybackFailure(PlaybackFailureRecord(origin, operation, status, position, firstFrameSeen, playbackId))
    return true
}

internal fun ApplicationController.declareOpenFailure(error: Exception) {
    if (error is RemoteFailure && error.sessionExpired) return
    if (declarePlaybackFailure(PlaybackFailureOrigin.OpenFailed, null, error::class.simpleName, (error as? RemoteFailure)?.status))
        updateState { it.copy(playbackFailure = PlaybackFailureOrigin.OpenFailed.failure) }
}

internal fun ApplicationController.clearDisprovedFailure(fact: PlayerFact) {
    val declared = declaredFailure ?: return
    val state = mutable.value
    val input = declared.input
    if (declared.origin.renderer || input == null || state.playback !== input || state.playbackFailure == null || state.fallbackNotice != null) return
    val advancing = when (fact) {
        is PlayerFact.Progress -> fact.positionMillis > (declared.positionMillis ?: 0L)
        is PlayerFact.Status -> fact.name == "Playing"
        else -> false
    }
    if (!advancing) return
    declaredFailure = null
    input.reopenAt(lastProgress?.positionMillis, input.preferences)
    updateState { it.copy(playbackFailure = null, paused = false) }
}

internal fun ApplicationController.reportPlaybackFailure(record: PlaybackFailureRecord) {
    playbackFailureReporter?.let { return it(record) }
    val remote = connection ?: return
    scope.launch {
        try { remote.reportPlaybackFailure(record) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }
}

internal suspend fun RemoteConnection.reportPlaybackFailure(record: PlaybackFailureRecord) {
    call(PLAYBACK_FAILURES_PATH, HttpMethod.Post, buildJsonObject {
        put("cause", record.origin.wire)
        record.operation?.let { put("operation", it.take(64)) }
        record.status?.let { put("status", it) }
        record.positionMillis?.let { put("position_ms", it) }
        put("first_frame", record.firstFrameSeen)
        record.playbackId?.let { put("playback_id", it.take(128)) }
    })
}
