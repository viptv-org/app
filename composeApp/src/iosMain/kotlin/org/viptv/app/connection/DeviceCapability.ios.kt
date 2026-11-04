package org.viptv.app.connection

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.invoke
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVPlayerHDRModeDolbyVision
import platform.AVFoundation.AVPlayerHDRModeHDR10
import platform.AVFoundation.AVPlayerHDRModeHLG
import platform.CoreMedia.kCMVideoCodecType_AV1
import platform.CoreMedia.kCMVideoCodecType_DolbyVisionHEVC
import platform.CoreMedia.kCMVideoCodecType_H264
import platform.CoreMedia.kCMVideoCodecType_HEVC
import platform.CoreMedia.kCMVideoCodecType_VP9
import platform.VideoToolbox.VTIsHardwareDecodeSupported
import platform.objc.class_getClassMethod
import platform.objc.method_getImplementation
import platform.objc.objc_lookUpClass
import platform.objc.sel_registerName

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
    val modes = availableHDRModes()
    return DISPLAY_HDR.map { (hdr, mode) ->
        HdrSupport(hdr, if (modes and mode != 0L) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED)
    }
}

// Kotlin/Native's AVFoundation interop does not export AVPlayer.availableHDRModes, so call it through the ObjC runtime.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun availableHDRModes(): Long {
    val cls = objc_lookUpClass("AVPlayer") ?: return 0L
    val sel = sel_registerName("availableHDRModes") ?: return 0L
    val method = class_getClassMethod(cls, sel) ?: return 0L
    val imp = method_getImplementation(method) ?: return 0L
    val fn = imp.reinterpret<CFunction<Function2<COpaquePointer?, COpaquePointer?, Long>>>()
    val self = interpretCPointer<COpaque>(cls.objcPtr()) ?: return 0L
    return fn(self, sel)
}
