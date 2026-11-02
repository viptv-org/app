package org.viptv.app.connection

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

enum class DecodeSupport(val wire: String) {
    UNKNOWN("unknown"), SUPPORTED("supported"), UNSUPPORTED("unsupported");

    companion object {
        fun of(value: String?): DecodeSupport = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

@Immutable
data class VideoCodecSupport(val codec: String, val support: DecodeSupport, val maxHeight: Int? = null)

@Immutable
data class AudioCodecSupport(val codec: String, val support: DecodeSupport)

@Immutable
data class HdrSupport(val hdr: String, val support: DecodeSupport)

@Immutable
data class DeviceCapability(
    val maxHeight: Int? = null,
    val videoCodecs: List<VideoCodecSupport> = emptyList(),
    val dolbyVision: DecodeSupport = DecodeSupport.UNKNOWN,
    val displayHdr: List<HdrSupport> = emptyList(),
    val audioCodecs: List<AudioCodecSupport> = emptyList(),
)

@Immutable
data class SeriesMemory(val providerId: String, val bingeGroup: String)

@Immutable
data class RankingContext(val device: DeviceCapability = DeviceCapability(), val seriesMemory: SeriesMemory? = null)

internal expect suspend fun platformDeviceCapability(): DeviceCapability

private const val PROBE_TIMEOUT_MILLIS = 1_500L
private const val MAX_ENTRIES = 8
private const val MAX_MEMORY_LENGTH = 128

private val probeGate = Mutex()
private var probed: DeviceCapability? = null

internal suspend fun deviceCapability(): DeviceCapability = probed ?: probeGate.withLock {
    probed ?: (withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) { platformDeviceCapability() } ?: DeviceCapability())
        .also { probed = it }
}

internal suspend fun rankingContext(seriesMemory: SeriesMemory?, capability: suspend () -> DeviceCapability): RankingContext =
    RankingContext(capability().copy(maxHeight = deviceMaxVideoHeight()), seriesMemory)

internal fun rankingContextJson(context: RankingContext): JsonObject = buildJsonObject {
    deviceCapabilityJson(context.device)?.let { put("device", it) }
    context.seriesMemory?.let(::seriesMemoryJson)?.let { put("series_memory", it) }
}

internal fun deviceCapabilityJson(device: DeviceCapability): JsonObject? {
    val video = device.videoCodecs.filter { capabilityName(it.codec) && it.support != DecodeSupport.UNKNOWN }
        .distinctBy { it.codec.lowercase() }.take(MAX_ENTRIES)
    val hdr = device.displayHdr.filter { capabilityName(it.hdr) && it.support != DecodeSupport.UNKNOWN }
        .distinctBy { it.hdr.lowercase() }.take(MAX_ENTRIES)
    val audio = device.audioCodecs.filter { capabilityName(it.codec) && it.support != DecodeSupport.UNKNOWN }
        .distinctBy { it.codec.lowercase() }.take(MAX_ENTRIES)
    val value = buildJsonObject {
        wireHeight(device.maxHeight)?.let { put("max_height", it) }
        if (video.isNotEmpty()) putJsonArray("video_codecs") {
            video.forEach { entry -> addJsonObject {
                put("codec", entry.codec)
                put("support", entry.support.wire)
                wireHeight(entry.maxHeight)?.let { put("max_height", it) }
            } }
        }
        if (device.dolbyVision != DecodeSupport.UNKNOWN) put("dolby_vision", device.dolbyVision.wire)
        if (hdr.isNotEmpty()) putJsonArray("display_hdr") {
            hdr.forEach { entry -> addJsonObject { put("hdr", entry.hdr); put("support", entry.support.wire) } }
        }
        if (audio.isNotEmpty()) putJsonArray("audio_codecs") {
            audio.forEach { entry -> addJsonObject { put("codec", entry.codec); put("support", entry.support.wire) } }
        }
    }
    return value.takeIf { it.isNotEmpty() }
}

internal fun seriesMemoryJson(memory: SeriesMemory): JsonObject? {
    if (!memoryText(memory.providerId) || !memoryText(memory.bingeGroup)) return null
    return buildJsonObject {
        put("provider_id", memory.providerId)
        put("binge_group", memory.bingeGroup)
    }
}

internal fun wireHeight(value: Int?): Int? = value?.takeIf { it in 144..4320 }

private fun capabilityName(value: String): Boolean =
    value.length in 1..16 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

private fun memoryText(value: String): Boolean =
    value.isNotBlank() && value.length <= MAX_MEMORY_LENGTH && value.none { it.isISOControl() }
