package org.viptv.app.state

import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

internal fun ApplicationController.rememberPlaybackOffsets(input: PlaybackInput, profile: String, source: SourceCandidate) {
    input.offsets.changed = { sourceMemory.rememberOffsets(profile, source, it) }
}
