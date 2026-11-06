package org.viptv.app.player

internal const val SlateSampleWidth = 32
internal const val SlateSampleHeight = 18
internal const val SlateSampleBytes = SlateSampleWidth * SlateSampleHeight

// About 2 s, 8 s and 20 s of the source's own clock.
internal val SlateSamplePoints = listOf(2_000L, 8_000L, 20_000L)

// A sample never stands in for an earlier one: the playhead has to have moved this far since the last.
internal const val SlateSampleAdvanceMillis = 1_000L

// Under this the stream barely needs a bit per pixel: only a still image encodes that cheaply.
internal const val SlateLowBitratePerMegapixel = 250_000L

// A downscaled frame this close to another one is the same picture: only encoder noise differs, not motion.
internal const val SlateSampleTolerance = 2

// 18 rows of 31 comparisons between a sample pixel and its right neighbour: 558 bits.
internal fun slateFingerprint(gray: ByteArray): IntArray {
    require(gray.size == SlateSampleBytes) { "a slate sample is ${SlateSampleWidth}x$SlateSampleHeight" }
    return IntArray(SlateSampleHeight) { y ->
        var row = 0
        for (x in 0 until SlateSampleWidth - 1) {
            if ((gray[y * SlateSampleWidth + x].toInt() and 0xFF) > (gray[y * SlateSampleWidth + x + 1].toInt() and 0xFF)) row = row or (1 shl x)
        }
        row
    }
}

internal fun slateSamplesMatch(a: ByteArray, b: ByteArray): Boolean {
    if (!slateFingerprint(a).contentEquals(slateFingerprint(b))) return false
    for (index in a.indices) if (kotlin.math.abs((a[index].toInt() and 0xFF) - (b[index].toInt() and 0xFF)) > SlateSampleTolerance) return false
    return true
}

internal fun slateLowBitrate(bitrate: Long?, width: Int, height: Int): Boolean {
    if (bitrate == null || bitrate <= 0 || width <= 0 || height <= 0) return false
    return bitrate * 1_000_000L / (width.toLong() * height.toLong()) < SlateLowBitratePerMegapixel
}

internal fun slateNoticeDetail(provider: String?): String {
    val name = provider?.trim()?.takeIf { it.isNotEmpty() } ?: return "This source's settings may need a new key."
    return "$name handed back this video. Its settings may need a new key."
}

internal fun graySample(pixels: IntArray): ByteArray {
    require(pixels.size == SlateSampleBytes)
    return ByteArray(SlateSampleBytes) { index ->
        val pixel = pixels[index]
        ((((pixel shr 16) and 0xFF) * 299 + ((pixel shr 8) and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000).toByte()
    }
}

// A source that plays one still image: every sample of the presented picture has to match, so an
// almost-still scene never matches.
internal class ErrorSlateDetector(private val samplePoints: List<Long> = SlateSamplePoints) {
    private val samples = mutableListOf<ByteArray>()
    private var lastPosition = Long.MIN_VALUE
    private var reported = false

    val done: Boolean get() = reported || samples.size >= samplePoints.size

    fun reset() {
        samples.clear()
        lastPosition = Long.MIN_VALUE
        reported = false
    }

    // Cheap enough to ask every frame; building the sample is what costs.
    fun due(positionMillis: Long): Boolean {
        if (done) return false
        if (positionMillis < samplePoints[samples.size]) return false
        return samples.isEmpty() || positionMillis - lastPosition >= SlateSampleAdvanceMillis
    }

    // Returns true once, on the sample that completes the evidence.
    fun take(positionMillis: Long, gray: ByteArray, lowBitrate: Boolean): Boolean {
        if (!due(positionMillis)) return false
        lastPosition = positionMillis
        samples += gray
        if (samples.any { !slateSamplesMatch(it, gray) }) return false
        if (samples.size < samplePoints.size && !(samples.size >= 2 && lowBitrate)) return false
        reported = true
        return true
    }
}