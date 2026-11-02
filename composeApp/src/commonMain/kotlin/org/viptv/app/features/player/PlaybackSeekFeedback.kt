package org.viptv.app.features.player

import org.viptv.app.player.PlayerFact

internal val PlaybackWaitingStatuses = listOf("Opening", "Buffering", "Preparing")
internal val PlaybackSettledStatuses = listOf("Playing", "Paused", "Ended")

// A replaced source reports no facts until its new element has data, so a clock past the target is its receipt.
internal const val SeekClockResumedMillis = 500L
internal const val SeekAbandonSeconds = 10

internal fun PlayerFact.settlesSeek(targetMillis: Long?): Boolean = when (this) {
    is PlayerFact.Status -> name in PlaybackSettledStatuses
    is PlayerFact.Progress -> targetMillis != null && positionMillis > targetMillis + SeekClockResumedMillis
    PlayerFact.Failed, PlayerFact.ControlFailed, is PlayerFact.Presentation -> true
    is PlayerFact.Properties, is PlayerFact.Tracks -> false
}
