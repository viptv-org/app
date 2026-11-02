package org.viptv.app.player

enum class PlaybackControl { PlayPause, Seek, Volume, Speed, SubtitleSize, Track }

internal const val playbackControlErrorCopy = "Could not apply this playback control."
internal const val playbackControlErrorVisibleMillis = 5_000L

internal val PlayerCommand.control: PlaybackControl get() = when (this) {
    is PlayerCommand.Seek -> PlaybackControl.Seek
    is PlayerCommand.Volume -> PlaybackControl.Volume
    is PlayerCommand.Speed -> PlaybackControl.Speed
    is PlayerCommand.SubtitleSize -> PlaybackControl.SubtitleSize
    is PlayerCommand.SelectTrack -> PlaybackControl.Track
}

// Only a control the viewer asked for is observable; automatic and teardown work passes no observer and stays in diagnostics.
internal fun PlaybackInput.controlFailed(control: PlaybackControl, cause: String, observe: ((PlayerFact) -> Unit)?) {
    diagnostics?.invoke("control_failed control=${control.name} cause=$cause user=${observe != null}")
    observe?.invoke(PlayerFact.ControlFailed(control))
}

internal fun Throwable.controlCause(): String = this::class.simpleName ?: "error"
