package org.viptv.app.player

import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.TrackId
import io.github.yuroyami.kiteplayer.TrackKind
import io.github.yuroyami.kiteplayer.TrackChange
import kotlinx.coroutines.CancellationException

/** Resolve an audio-only ordinal to Kite's actual demuxer track ID. */
internal suspend fun applyRequiredAudio(player: KitePlayer, selection: RequiredAudioSelection,
    emit: (PlayerFact) -> Unit): Boolean {
    val candidates = player.state.value.tracks.audio.map { AudioCandidate(it.id.value) }
    return try {
        when (val choice = selection.choose(candidates, tracksKnown = true)) {
            is RequiredAudioChoice.Select -> {
                val result = player.selectTrack(TrackKind.Audio, TrackId(choice.id))
                check(result is TrackChange.Applied && result.track?.value == choice.id)
                true
            }
            RequiredAudioChoice.Unchanged -> true
            RequiredAudioChoice.Unavailable -> { player.pause(); emit(PlayerFact.Failed); false }
        }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { player.pause(); emit(PlayerFact.Failed); false }
}
