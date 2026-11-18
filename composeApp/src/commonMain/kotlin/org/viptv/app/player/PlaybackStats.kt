package org.viptv.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class PlaybackStats(private val time: TimeSource = TimeSource.Monotonic) {
    private data class Window(
        val reported: Boolean = false,
        val rebuffers: Int = 0,
        val rebufferMillis: Long = 0,
        val stalledSince: TimeMark? = null,
        val droppedFrames: Long = 0,
        val bufferedAheadMillis: Long? = null,
        val throughputKbps: Long? = null,
        val sourceBitrateKbps: Long? = null,
        val videoDecoder: String? = null,
        val audioDecoder: String? = null,
    )

    private val window = MutableStateFlow(Window())

    fun stalled(value: Boolean) = window.update { current ->
        val since = current.stalledSince
        when {
            value && since == null -> current.copy(reported = true, rebuffers = current.rebuffers + 1, stalledSince = time.markNow())
            !value && since != null -> current.copy(reported = true, rebufferMillis = current.rebufferMillis + since.elapsedNow().inWholeMilliseconds, stalledSince = null)
            else -> current.copy(reported = true)
        }
    }

    fun droppedFrames(count: Int) = window.update { it.copy(reported = true, droppedFrames = it.droppedFrames + count.coerceAtLeast(0)) }

    fun bufferedAhead(millis: Long) = window.update { it.copy(reported = true, bufferedAheadMillis = millis.coerceAtLeast(0)) }

    fun throughput(bitsPerSecond: Long) = window.update { it.copy(reported = true, throughputKbps = (bitsPerSecond / 1000).takeIf { kbps -> kbps > 0 }) }

    fun sourceBitrate(bitsPerSecond: Long?) = window.update { it.copy(reported = true, sourceBitrateKbps = bitsPerSecond?.let { bits -> bits / 1000 }?.takeIf { kbps -> kbps > 0 }) }

    fun decoders(video: String?, audio: String?) = window.update {
        it.copy(reported = true, videoDecoder = video?.decoderName() ?: it.videoDecoder, audioDecoder = audio?.decoderName() ?: it.audioDecoder)
    }

    fun drain(): JsonObject? {
        val now = time.markNow()
        val previous = window.getAndUpdate { it.copy(rebuffers = 0, rebufferMillis = 0, droppedFrames = 0, stalledSince = it.stalledSince?.let { now }) }
        if (!previous.reported) return null
        val ongoing = previous.stalledSince?.let { (it.elapsedNow() - now.elapsedNow()).inWholeMilliseconds } ?: 0
        return buildJsonObject {
            put("rebuffer_count", previous.rebuffers.coerceAtMost(MAX_REBUFFERS))
            put("rebuffer_ms", (previous.rebufferMillis + ongoing).coerceIn(0, MAX_REBUFFER_MILLIS))
            put("dropped_frames", previous.droppedFrames.coerceAtMost(MAX_DROPPED_FRAMES))
            previous.bufferedAheadMillis?.let { put("buffered_ahead_seconds", (it / 1000.0).coerceAtMost(MAX_BUFFERED_AHEAD_SECONDS)) }
            previous.throughputKbps?.let { put("throughput_kbps", it.coerceAtMost(MAX_THROUGHPUT_KBPS)) }
            previous.sourceBitrateKbps?.let { put("source_bitrate_kbps", it.coerceAtMost(MAX_SOURCE_BITRATE_KBPS)) }
            previous.videoDecoder?.let { put("video_decoder", it) }
            previous.audioDecoder?.let { put("audio_decoder", it) }
        }
    }
}

private fun String.decoderName(): String? =
    filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "._- " }.trim().take(64).takeIf { it.isNotEmpty() }

private const val MAX_REBUFFERS = 10_000
private const val MAX_REBUFFER_MILLIS = 3_600_000L
private const val MAX_DROPPED_FRAMES = 10_000_000L
private const val MAX_BUFFERED_AHEAD_SECONDS = 86_400.0
private const val MAX_THROUGHPUT_KBPS = 10_000_000L
private const val MAX_SOURCE_BITRATE_KBPS = 1_000_000L
