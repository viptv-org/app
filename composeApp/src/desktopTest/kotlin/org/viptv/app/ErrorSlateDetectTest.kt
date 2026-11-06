package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class ErrorSlateDetectTest {
    // A dark slate with a bright caption line: what a provider's "connection expired" still looks like downscaled.
    private fun slate(shift: Int = 0): ByteArray = ByteArray(SlateSampleBytes) { index ->
        val x = index % SlateSampleWidth
        val y = index / SlateSampleWidth
        when {
            y in 7..9 && x in 6..25 -> (180 + shift).toByte()
            y in 11..12 && x in 10..22 -> (120 + shift).toByte()
            else -> (16 + shift).toByte()
        }
    }

    // The same room and the same framing, with a mouth and a jaw that moved: near-identical, never the same picture.
    private fun talkingHead(mouthY: Int): ByteArray = slate().also { frame ->
        for (x in 12..20) for (y in mouthY until mouthY + 2) frame[y * SlateSampleWidth + x] = 40
    }

    private fun movingFrame(seed: Int): ByteArray = ByteArray(SlateSampleBytes) { index ->
        ((index * 37 + seed * 91) % 251).toByte()
    }

    @Test fun threeIdenticalSamplesReportAProbableSlateOnTheThird() {
        val detector = ErrorSlateDetector()
        for ((index, position) in SlateSamplePoints.withIndex()) {
            assertTrue(detector.due(position), "sample $index is due at $position")
            assertEquals(index == SlateSamplePoints.lastIndex, detector.take(position, slate(), lowBitrate = false),
                "only the third sample completes the evidence")
        }
        assertTrue(detector.done)
        assertFalse(detector.due(30_000), "a reported source is not sampled again")
    }

    @Test fun aLowBitrateSourceIsKnownFromTheSecondSample() {
        val detector = ErrorSlateDetector()
        assertFalse(detector.take(2_000, slate(), lowBitrate = true), "one sample is never enough")
        assertTrue(detector.take(8_000, slate(), lowBitrate = true), "two identical samples at a still image's bitrate")
        val patient = ErrorSlateDetector()
        patient.take(2_000, slate(), lowBitrate = false)
        assertFalse(patient.take(8_000, slate(), lowBitrate = false), "a normal bitrate waits for the third sample")
        assertTrue(patient.take(20_000, slate(), lowBitrate = false))
    }

    @Test fun aSlowSceneNeverReportsASlate() {
        val detector = ErrorSlateDetector()
        assertFalse(detector.take(2_000, talkingHead(4), lowBitrate = true))
        assertFalse(detector.take(8_000, talkingHead(6), lowBitrate = true), "a moved jaw is motion, not a still")
        assertFalse(detector.take(20_000, talkingHead(5), lowBitrate = true))
        assertTrue(detector.done, "the three samples are spent on a scene that is not a slate")
        assertFalse(detector.due(25_000))
    }

    @Test fun aMovingPictureNeverReportsASlate() {
        val detector = ErrorSlateDetector()
        assertFalse(detector.take(2_000, movingFrame(1), lowBitrate = true))
        assertFalse(detector.take(8_000, movingFrame(2), lowBitrate = true))
        assertFalse(detector.take(20_000, movingFrame(3), lowBitrate = true))
    }

    @Test fun aSampleIsSkippedRatherThanTakenEarly() {
        val detector = ErrorSlateDetector()
        assertFalse(detector.due(1_500), "nothing is sampled before the first point")
        assertFalse(detector.take(2_000, slate(), lowBitrate = false), "one sample is never enough")
        assertFalse(detector.due(2_500), "the playhead has not advanced a second since the last sample")
        assertFalse(detector.take(2_500, slate(), lowBitrate = true), "a sample that cannot be taken is not recorded")
        assertTrue(detector.due(8_000))
        assertTrue(detector.take(8_000, slate(), lowBitrate = true), "the second sample still fires on a low bitrate")
    }

    @Test fun aSeekStartsTheEvidenceAgain() {
        val detector = ErrorSlateDetector()
        detector.take(2_000, slate(), lowBitrate = false)
        detector.reset()
        assertTrue(detector.due(600_000), "after a seek the first sample is due again")
        assertFalse(detector.take(600_000, slate(), lowBitrate = true), "a restart still needs two samples")
        assertFalse(detector.due(600_500), "the advance rule survives the restart")
        assertTrue(detector.take(601_000, slate(), lowBitrate = true))
    }

    @Test fun encoderNoiseToleranceStopsAtTwoLevels() {
        assertTrue(slateSamplesMatch(slate(), slate(1)), "a level of encoder noise is the same picture")
        assertTrue(slateSamplesMatch(slate(), slate(2)))
        assertFalse(slateSamplesMatch(slate(), slate(4)), "four levels of difference is content")
    }

    @Test fun theFingerprintComparesNeighboursAndTheToleranceCatchesBrightness() {
        assertEquals(slateFingerprint(slate()).toList(), slateFingerprint(slate()).toList())
        assertTrue(slateFingerprint(slate()).contentEquals(slateFingerprint(slate(40))),
            "every comparison holds under a uniform brightness change")
        assertFalse(slateSamplesMatch(slate(), slate(40)), "the grayscale tolerance is what rejects it")
    }

    @Test fun aBitrateIsLowOnlyWhenItCannotAffordPixels() {
        assertTrue(slateLowBitrate(21_000, 1920, 1080), "a 1080p still at 21 kbps")
        assertTrue(slateLowBitrate(200_000, 1280, 720), "a 720p still at 200 kbps")
        assertFalse(slateLowBitrate(300_000, 1280, 720), "a 720p still at 300 kbps is over the line")
        assertFalse(slateLowBitrate(3_000_000, 1920, 1080), "a normal 1080p title")
        assertFalse(slateLowBitrate(null, 1920, 1080), "an unstated bitrate is no evidence")
        assertFalse(slateLowBitrate(0, 1920, 1080))
        assertFalse(slateLowBitrate(21_000, 0, 0))
    }

    @Test fun theDetailLineNamesTheAddonThatServedTheSource() {
        assertEquals("ElfHosted handed back this video. Its settings may need a new key.", slateNoticeDetail("ElfHosted"))
        assertEquals("This source's settings may need a new key.", slateNoticeDetail(null))
        assertEquals("This source's settings may need a new key.", slateNoticeDetail("   "))
    }

    @Test fun aCopyOfTheStateIsNeverShared() {
        val detector = ErrorSlateDetector()
        val frame = slate()
        detector.take(2_000, frame, lowBitrate = false)
        frame.fill(0)
        assertFalse(detector.take(8_000, slate(), lowBitrate = true), "the recorded sample still describes the picture it was taken from")
    }

    @Test fun theGrayscaleSampleReadsTheGreenChannelMost() {
        val white = IntArray(SlateSampleBytes) { 0xFFFFFFFF.toInt() }
        assertEquals(255, graySample(white)[0].toInt() and 0xFF)
        val green = IntArray(SlateSampleBytes) { 0xFF00FF00.toInt() }
        assertEquals(149, green.let(::graySample)[0].toInt() and 0xFF)
        val red = IntArray(SlateSampleBytes) { 0xFFFF0000.toInt() }
        assertEquals(76, red.let(::graySample)[0].toInt() and 0xFF)
    }
}