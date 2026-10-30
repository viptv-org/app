package org.viptv.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object PictureInPictureWindow {
    private val shown = MutableStateFlow(false)
    val active: StateFlow<Boolean> = shown.asStateFlow()
    fun set(active: Boolean) { shown.value = active }
}

internal enum class PictureInPictureBlocker { Television, NotPlaying, Stream, Device }

internal fun pictureInPictureBlocker(television: Boolean, playing: Boolean, streamSupports: Boolean, deviceSupports: Boolean): PictureInPictureBlocker? = when {
    television -> PictureInPictureBlocker.Television
    !playing -> PictureInPictureBlocker.NotPlaying
    !streamSupports -> PictureInPictureBlocker.Stream
    !deviceSupports -> PictureInPictureBlocker.Device
    else -> null
}
