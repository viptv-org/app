package org.viptv.app.player.mpv

internal enum class MpvRenderPath(val label: String) { Software("mpv-software"), Gl("mpv-gl") }

internal enum class MpvGlState { Unavailable, Pending, Ready }

internal sealed class MpvGlAvailability(val state: MpvGlState) {
    data object Unavailable : MpvGlAvailability(MpvGlState.Unavailable)
    data object Pending : MpvGlAvailability(MpvGlState.Pending)
    class Ready(val interop: MpvSkikoGl) : MpvGlAvailability(MpvGlState.Ready)
}

internal fun mpvGlRequested(
    property: String? = System.getProperty("viptv.mpvGl"),
    environment: String? = System.getenv("VIPTV_MPV_GL"),
): Boolean = property.equals("true", ignoreCase = true) || environment == "1"

internal const val mpvGlPendingFrameLimit = 120

// null keeps waiting: Skiko creates its GL context during its first frame, after the first onRender.
internal fun mpvRenderPath(requested: Boolean, gl: MpvGlState, pendingFrames: Int): MpvRenderPath? = when {
    !requested -> MpvRenderPath.Software
    gl == MpvGlState.Ready -> MpvRenderPath.Gl
    gl == MpvGlState.Pending && pendingFrames < mpvGlPendingFrameLimit -> null
    else -> MpvRenderPath.Software
}
