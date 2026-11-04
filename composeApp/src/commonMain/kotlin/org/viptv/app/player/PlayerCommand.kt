package org.viptv.app.player

sealed interface PlayerCommand {
    // An automatic skip (intro, recap, credits) is a seek nobody asked for; its failure is not the viewer's to see.
    data class Seek(val positionMillis: Long, val automatic: Boolean = false) : PlayerCommand
    data class Volume(val value: Float) : PlayerCommand
    data class SelectTrack(val kind: PlaybackTrackKind, val id: Int?) : PlayerCommand
    data class SubtitleSize(val value: Float) : PlayerCommand
    data class SubtitleBackground(val color: String?) : PlayerCommand
    data class Speed(val value: Double) : PlayerCommand
}