package org.viptv.app.connection

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import kotlin.coroutines.resume

private val VIDEO_PROBES = listOf(
    "avc" to "video/mp4; codecs=\"avc1.640028\"",
    "hevc" to "video/mp4; codecs=\"hvc1.1.6.L123.B0\"",
    "av1" to "video/mp4; codecs=\"av01.0.08M.08\"",
    "vp9" to "video/webm; codecs=\"vp09.00.10.08\"",
)

private val AUDIO_PROBES = listOf(
    "ac3" to "audio/mp4; codecs=\"ac-3\"",
    "eac3" to "audio/mp4; codecs=\"ec-3\"",
    "dts" to "audio/mp4; codecs=\"dtsc\"",
)

private val VIDEO_HEIGHT_PROBES = listOf(2160, 1080)

private data class MediaProbe(val kind: String, val contentType: String, val height: Int)

@JsFun("""(configsJson, answered, failed) => {
  try {
    const requests = JSON.parse(configsJson).map(entry => {
      const configuration = entry.kind === 'audio'
        ? { type: 'file', audio: { contentType: entry.contentType } }
        : { type: 'file', video: { contentType: entry.contentType, width: Math.round(entry.height * 16 / 9), height: entry.height, bitrate: 12000000, framerate: 30 } };
      return navigator.mediaCapabilities.decodingInfo(configuration).then(info => info.supported ? 'supported' : 'unsupported', () => 'unknown');
    });
    Promise.all(requests).then(answers => answered(JSON.stringify(answers)), () => failed());
  } catch (error) { failed(); }
}""")
private external fun probeDecodingSupport(configsJson: String, answered: (String) -> Unit, failed: () -> Unit)

@JsFun("""() => {
  try { return window.matchMedia ? (window.matchMedia('(dynamic-range: high)').matches ? 'supported' : 'unsupported') : 'unknown'; }
  catch (error) { return 'unknown'; }
}""")
private external fun browserDynamicRange(): String

internal actual suspend fun platformDeviceCapability(): DeviceCapability {
    val answers = decodingAnswers() ?: return DeviceCapability(displayHdr = displayHdrSupport())
    val videoAnswers = answers.take(VIDEO_PROBES.size * VIDEO_HEIGHT_PROBES.size)
    val audioAnswers = answers.drop(VIDEO_PROBES.size * VIDEO_HEIGHT_PROBES.size)
    return DeviceCapability(
        videoCodecs = VIDEO_PROBES.mapIndexed { index, (codec, _) ->
            val perHeight = videoAnswers.drop(index * VIDEO_HEIGHT_PROBES.size).take(VIDEO_HEIGHT_PROBES.size)
            when {
                perHeight.first() == DecodeSupport.SUPPORTED -> VideoCodecSupport(codec, DecodeSupport.SUPPORTED, VIDEO_HEIGHT_PROBES.first())
                perHeight.getOrNull(1) == DecodeSupport.SUPPORTED -> VideoCodecSupport(codec, DecodeSupport.SUPPORTED, VIDEO_HEIGHT_PROBES.last())
                perHeight.all { it == DecodeSupport.UNSUPPORTED } -> VideoCodecSupport(codec, DecodeSupport.UNSUPPORTED)
                else -> VideoCodecSupport(codec, DecodeSupport.UNKNOWN)
            }
        },
        displayHdr = displayHdrSupport(),
        audioCodecs = AUDIO_PROBES.mapIndexed { index, (codec, _) ->
            AudioCodecSupport(codec, audioAnswers.getOrElse(index) { DecodeSupport.UNKNOWN })
        },
    )
}

private fun displayHdrSupport(): List<HdrSupport> {
    val support = DecodeSupport.of(browserDynamicRange())
    return if (support == DecodeSupport.UNKNOWN) emptyList() else listOf(HdrSupport("hdr10", support))
}

private suspend fun decodingAnswers(): List<DecodeSupport>? {
    val probes = VIDEO_PROBES.flatMap { (_, contentType) ->
        VIDEO_HEIGHT_PROBES.map { height -> MediaProbe("video", contentType, height) }
    } + AUDIO_PROBES.map { (_, contentType) -> MediaProbe("audio", contentType, 0) }
    val configs = buildJsonArray {
        probes.forEach { probe -> addJsonObject {
            put("kind", probe.kind)
            put("contentType", probe.contentType)
            if (probe.kind == "video") put("height", probe.height)
        } }
    }
    return suspendCancellableCoroutine<List<DecodeSupport>?> { continuation ->
        var settled = false
        fun finish(answers: List<DecodeSupport>?) {
            if (settled) return
            settled = true
            if (continuation.isActive) continuation.resume(answers)
        }
        try {
            probeDecodingSupport(configs.toString(), { raw ->
                val parsed = runCatching {
                    Json.parseToJsonElement(raw).jsonArray.map { DecodeSupport.of(it.jsonPrimitive.contentOrNull) }
                }.getOrNull()
                finish(parsed)
            }, { finish(null) })
        } catch (_: Throwable) { finish(null) }
    }
}
