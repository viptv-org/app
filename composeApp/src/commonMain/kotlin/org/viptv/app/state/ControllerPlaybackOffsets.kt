package org.viptv.app.state

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

internal fun ApplicationController.rememberPlaybackOffsets(input: PlaybackInput, profile: String, source: SourceCandidate) {
    playbackOffsetsJob?.cancel()
    playbackOffsetsJob = scope.launch {
        input.offsets.state.drop(1).collect { sourceMemory.rememberOffsets(profile, source, it) }
    }
}
