package org.viptv.app.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class SubtitlePcmSpan(val epoch: Long, val startUs: Long, val sampleRate: Int,
    val channels: Int, val pcm16: ByteArray)
internal data class SubtitlePcmFormat(val sampleRate: Int, val channels: Int)

@UnstableApi
internal class Media3PcmProbe(sink: AudioSink) : ForwardingAudioSink(sink) {
    private class Slot {
        val bytes = ByteArray(32_768)
        var size = 0
        var epoch = 0L
        var startUs = 0L
        var rate = 0
        var channels = 0
    }
    private val slots = Array(8) { Slot() }
    private val head = AtomicLong()
    private val tail = AtomicLong()
    private val generation = AtomicLong()
    private val observing = AtomicBoolean(true)
    private val configured = AtomicReference<SubtitlePcmFormat?>()
    val format: SubtitlePcmFormat? get() = configured.get()
    val epoch: Long get() = generation.get()
    val gaps = AtomicLong()
    private var sampleRate = 0
    private var channels = 0
    private var outputOffsetUs = 0L
    private var copied: ByteBuffer? = null

    override fun configure(config: AudioSink.AudioSinkConfig) {
        super.configure(config)
        invalidate()
        val format = config.format
        val usable = format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_16BIT &&
            format.sampleRate in 8_000..192_000 && format.channelCount in 1..8
        sampleRate = if (usable) format.sampleRate else 0
        channels = if (usable) format.channelCount else 0
        configured.set(if (usable) SubtitlePcmFormat(sampleRate, channels) else null)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (buffer !== copied) {
            copied = buffer
            if (observing.get() && sampleRate > 0 && buffer.hasRemaining()) capture(buffer, presentationTimeUs - outputOffsetUs)
        }
        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (consumed) copied = null
        return consumed
    }

    private fun capture(buffer: ByteBuffer, startUs: Long) {
        val write = tail.get()
        val size = buffer.remaining()
        if (size > slots[0].bytes.size || size % (channels * 2) != 0 || write - head.get() >= slots.size || startUs < 0) {
            gaps.incrementAndGet()
            generation.incrementAndGet()
            return
        }
        val slot = slots[(write % slots.size).toInt()]
        val operation = generation.get()
        buffer.duplicate().get(slot.bytes, 0, size)
        slot.size = size
        slot.startUs = startUs
        slot.rate = sampleRate
        slot.channels = channels
        slot.epoch = operation
        tail.lazySet(write + 1)
    }

    fun poll(): SubtitlePcmSpan? {
        val available = tail.get()
        while (head.get() < available) {
            val read = head.get()
            val slot = slots[(read % slots.size).toInt()]
            val value = if (slot.epoch == generation.get()) SubtitlePcmSpan(slot.epoch, slot.startUs, slot.rate,
                slot.channels, slot.bytes.copyOf(slot.size)) else null
            head.lazySet(read + 1)
            if (value != null) return value
        }
        return null
    }

    fun observe(value: Boolean) {
        if (observing.getAndSet(value) != value) generation.incrementAndGet()
    }

    private fun invalidate() { generation.incrementAndGet(); copied = null }
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        invalidate()
        outputOffsetUs = outputStreamOffsetUs
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }
    override fun handleDiscontinuity() { invalidate(); super.handleDiscontinuity() }
    override fun flush() { invalidate(); super.flush() }
    override fun reset() { invalidate(); sampleRate = 0; channels = 0; configured.set(null); super.reset() }
}
