package org.viptv.app.connection

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DOLBY_VISION_MIME = "video/dolby-vision"

private val VIDEO_CODECS = listOf(
    "avc" to "video/avc",
    "hevc" to "video/hevc",
    "av1" to "video/av01",
    "vp9" to "video/x-vnd.on2.vp9",
)

private val AUDIO_CODECS = listOf(
    "ac3" to "audio/ac3",
    "eac3" to "audio/eac3",
    "truehd" to "audio/true-hd",
    "dts" to "audio/vnd.dts",
)

private val AUDIO_ENCODINGS = mapOf(
    "audio/ac3" to setOf(AudioFormat.ENCODING_AC3),
    "audio/eac3" to setOf(AudioFormat.ENCODING_E_AC3),
    "audio/true-hd" to setOf(AudioFormat.ENCODING_DOLBY_TRUEHD),
    "audio/vnd.dts" to setOf(AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD),
)

private val HEIGHT_TIERS = listOf(720, 1080, 1440, 2160)

internal data class DecoderFact(val mime: String, val hardware: Boolean, val maxHeight: Int?)

internal actual suspend fun platformDeviceCapability(): DeviceCapability {
    val context = AndroidDisplayCapability.context ?: return DeviceCapability()
    return withContext(Dispatchers.IO) { androidDeviceCapability(context) }
}

private fun androidDeviceCapability(context: Context): DeviceCapability {
    val facts = runCatching { decoderFacts(MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }) }
        .getOrNull()?.takeIf { it.isNotEmpty() }
    return DeviceCapability(
        videoCodecs = facts?.let(::videoCodecSupports) ?: emptyList(),
        dolbyVision = facts?.let(::dolbyVisionSupport) ?: DecodeSupport.UNKNOWN,
        displayHdr = displayHdrSupport(context),
        audioCodecs = facts?.let { audioCodecSupports(it, runCatching { passthroughEncodings(context) }.getOrNull()) } ?: emptyList(),
    )
}

internal fun videoCodecSupports(facts: List<DecoderFact>): List<VideoCodecSupport> =
    VIDEO_CODECS.map { (codec, mime) ->
        val matching = facts.filter { it.mime == mime }
        if (matching.isEmpty()) VideoCodecSupport(codec, DecodeSupport.UNSUPPORTED)
        else VideoCodecSupport(codec, DecodeSupport.SUPPORTED, matching.filter { it.hardware }.mapNotNull { it.maxHeight }.maxOrNull())
    }

private fun dolbyVisionSupport(facts: List<DecoderFact>): DecodeSupport =
    if (facts.any { it.mime == DOLBY_VISION_MIME }) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED

internal fun audioCodecSupports(facts: List<DecoderFact>, passthrough: Set<Int>?): List<AudioCodecSupport> =
    AUDIO_CODECS.map { (codec, mime) ->
        val decoded = facts.any { it.mime == mime }
        val routed = passthrough?.any { it in AUDIO_ENCODINGS[mime].orEmpty() } == true
        when {
            decoded || routed -> AudioCodecSupport(codec, DecodeSupport.SUPPORTED)
            passthrough == null -> AudioCodecSupport(codec, DecodeSupport.UNKNOWN)
            else -> AudioCodecSupport(codec, DecodeSupport.UNSUPPORTED)
        }
    }

private fun decoderFacts(decoders: List<MediaCodecInfo>): List<DecoderFact> = decoders.flatMap { info ->
    val hardware = hardwareDecode(info)
    info.supportedTypes.map { mime -> DecoderFact(mime, hardware, if (hardware) hardwareHeight(info, mime) else null) }
}

// isHardwareAccelerated needs API 29; older devices answer no hardware height rather than guess.
private fun hardwareDecode(info: MediaCodecInfo): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isHardwareAccelerated

private fun hardwareHeight(info: MediaCodecInfo, mime: String): Int? {
    val video = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: return null
    return HEIGHT_TIERS.lastOrNull { video.isSizeSupported(it * 16 / 9, it) }
}

private fun displayHdrSupport(context: Context): List<HdrSupport> = runCatching {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    val supported = display?.hdrCapabilities?.supportedHdrTypes?.toSet() ?: return emptyList()
    listOf(
        "hdr10" to Display.HdrCapabilities.HDR_TYPE_HDR10,
        "hdr10_plus" to Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
        "hlg" to Display.HdrCapabilities.HDR_TYPE_HLG,
        "dv" to Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
    ).map { (hdr, type) -> HdrSupport(hdr, if (type in supported) DecodeSupport.SUPPORTED else DecodeSupport.UNSUPPORTED) }
}.getOrElse { emptyList() }

private fun passthroughEncodings(context: Context): Set<Int>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val manager = context.getSystemService(AudioManager::class.java) ?: return null
    return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).flatMap { it.encodings.toList() }.toSet()
}
