package org.viptv.app.player

sealed interface PlayerCommand {
    data class Seek(val positionMillis: Long) : PlayerCommand
    data class Volume(val value: Float) : PlayerCommand
    data class SelectTrack(val kind: PlaybackTrackKind, val id: Int?) : PlayerCommand
    data class SubtitleSize(val value: Float) : PlayerCommand
    data class Speed(val value: Double) : PlayerCommand
}
