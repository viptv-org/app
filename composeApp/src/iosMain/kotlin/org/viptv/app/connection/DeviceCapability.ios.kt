package org.viptv.app.connection

import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerHDRModeDolbyVision
import platform.AVFoundation.AVPlayerHDRModeHDR10
import platform.AVFoundation.AVPlayerHDRModeHLG
import platform.CoreMedia.kCMVideoCodecType_AV1
import platform.CoreMedia.kCMVideoCodecType_DolbyVisionHEVC
import platform.CoreMedia.kCMVideoCodecType_H264
import platform.CoreMedia.kCMVideoCodecType_HEVC
import platform.CoreMedia.kCMVideoCodecType_VP9
import platform.VideoToolbox.VTIsHardwareDecodeSupported

private val VIDEO_CODECS = listOf(
    "avc" to kCMVideoCodecType_H264,
    "hevc" to kCMVideoCodecType_HEVC,
    "av1" to kCMVideoCodecType_AV1,
    "vp9" to kCMVideoCodecType_VP9,
)

private val DISPLAY_HDR = listOf(
    "hdr10" to AVPlayerHDRModeHDR10,
    "hlg" to AVPlayerHDRModeHLG,
    "dv" to AVPlayerHDRModeDolbyVision,
)

internal actual suspend fun platformDeviceCapability(): DeviceCapability = DeviceCapability(
    videoCodecs = VIDEO_CODECS.map { (codec, type) ->
        VideoCodecSupport(codec, if (VTIsHardwareDecodeSupported(type)) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED)
    },
    dolbyVision = if (VTIsHardwareDecodeSupported(kCMVideoCodecType_DolbyVisionHEVC)) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED,
    displayHdr = displayHdrSupport(),
)

private fun displayHdrSupport(): List<HdrSupport> {
    val modes = AVPlayer.availableHDRModes
    return DISPLAY_HDR.map { (hdr, mode) ->
        HdrSupport(hdr, if (modes and mode != 0L) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED)
    }
}
