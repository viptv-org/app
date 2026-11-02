package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.viptv.app.player.mpv.mpvDecoderCodecs

private val VIDEO_CODECS = listOf(
    "avc" to "h264",
    "hevc" to "hevc",
    "av1" to "av1",
    "vp9" to "vp9",
)

internal fun desktopCapability(decoders: Set<String>): DeviceCapability = DeviceCapability(
    videoCodecs = VIDEO_CODECS.map { (codec, mpv) ->
        VideoCodecSupport(codec, if (mpv in decoders) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED)
    },
)

internal actual suspend fun platformDeviceCapability(): DeviceCapability = withContext(Dispatchers.IO) {
    mpvDecoderCodecs()?.let(::desktopCapability) ?: DeviceCapability()
}
