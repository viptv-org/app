package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class ErrorSlateDetectTest {
    private val clip = 120_000L
    private val episode = 23 * 60_000L

    private fun started(at: Long = 0, runtimeMillis: Long? = null, duration: Long? = clip) =
        ErrorSlateDetector(runtimeMillis).also { assertFalse(it.due(at, duration), "the first observation only marks where playback started") }

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
        val detector = started()
        for ((index, position) in SlateSamplePoints.withIndex()) {
            assertTrue(detector.due(position, clip), "sample $index is due at $position")
            assertEquals(index == SlateSamplePoints.lastIndex, detector.take(position, clip, slate(), lowBitrate = false),
                "only the third sample completes the evidence")
        }
        assertTrue(detector.done)
        assertFalse(detector.due(30_000, clip), "a reported source is not sampled again")
    }

    @Test fun aLowBitrateSourceIsKnownFromTheSecondSample() {
        val detector = started()
        assertFalse(detector.take(2_000, clip, slate(), lowBitrate = true), "one sample is never enough")
        assertTrue(detector.take(8_000, clip, slate(), lowBitrate = true), "two identical samples at a still image's bitrate")
        val patient = started()
        patient.take(2_000, clip, slate(), lowBitrate = false)
        assertFalse(patient.take(8_000, clip, slate(), lowBitrate = false), "a normal bitrate waits for the third sample")
        assertTrue(patient.take(20_000, clip, slate(), lowBitrate = false))
    }

    @Test fun aSlowSceneNeverReportsASlate() {
        val detector = started()
        assertFalse(detector.take(2_000, clip, talkingHead(4), lowBitrate = true))
        assertFalse(detector.take(8_000, clip, talkingHead(6), lowBitrate = true), "a moved jaw is motion, not a still")
        assertFalse(detector.take(20_000, clip, talkingHead(5), lowBitrate = true))
        assertTrue(detector.done, "the three samples are spent on a scene that is not a slate")
        assertFalse(detector.due(25_000, clip))
    }

    @Test fun aMovingPictureNeverReportsASlate() {
        val detector = started()
        assertFalse(detector.take(2_000, clip, movingFrame(1), lowBitrate = true))
        assertFalse(detector.take(8_000, clip, movingFrame(2), lowBitrate = true))
        assertFalse(detector.take(20_000, clip, movingFrame(3), lowBitrate = true))
    }

    @Test fun aSampleIsSkippedRatherThanTakenEarly() {
        val detector = started()
        assertFalse(detector.due(1_500, clip), "nothing is sampled before the first point")
        assertFalse(detector.take(2_000, clip, slate(), lowBitrate = false), "one sample is never enough")
        assertFalse(detector.due(2_500, clip), "the playhead has not advanced a second since the last sample")
        assertFalse(detector.take(2_500, clip, slate(), lowBitrate = true), "a sample that cannot be taken is not recorded")
        assertTrue(detector.due(8_000, clip))
        assertTrue(detector.take(8_000, clip, slate(), lowBitrate = true), "the second sample still fires on a low bitrate")
    }

    @Test fun aSeekStartsTheEvidenceAgain() {
        val detector = started()
        detector.take(2_000, clip, slate(), lowBitrate = false)
        detector.reset()
        assertFalse(detector.due(60_000, clip), "after a seek the clock starts where playback resumed")
        assertFalse(detector.due(61_000, clip), "one second after the seek is not two")
        assertTrue(detector.due(62_000, clip))
        assertFalse(detector.take(62_000, clip, slate(), lowBitrate = true), "a restart still needs two samples")
        assertFalse(detector.due(62_500, clip), "the advance rule survives the restart")
        assertFalse(detector.due(67_000, clip), "the second point is 8 s after the seek")
        assertTrue(detector.take(68_000, clip, slate(), lowBitrate = true))
    }

    @Test fun aRealEpisodeNeverReportsWhateverItsPictureDoes() {
        for (open in listOf(0L, 160_000L)) {
            val detector = started(open, duration = episode)
            for (point in SlateSamplePoints) {
                assertFalse(detector.due(open + point, episode), "a 23-minute media opened at $open is never sampled")
                assertFalse(detector.take(open + point, episode, slate(), lowBitrate = true))
            }
            detector.reset()
            assertFalse(detector.due(400_000, episode))
            for (point in SlateSamplePoints) assertFalse(detector.take(400_000 + point, episode, slate(), lowBitrate = true), "nor after a seek")
        }
    }

    @Test fun aResumedSourceSamplesFromWherePlaybackStarted() {
        val detector = started(160_000)
        assertFalse(detector.due(161_000, clip), "every point lies ahead of where this source started")
        assertTrue(detector.due(162_000, clip))
    }

    @Test fun anUnknownOrLongMediaIsNeverSampled() {
        val unknown = started(duration = null)
        for (point in SlateSamplePoints) assertFalse(unknown.take(point, null, slate(), lowBitrate = true))
        assertFalse(slateMediaEligible(null, null))
        assertFalse(slateMediaEligible(0, null))
        assertTrue(slateMediaEligible(SlateMaxMediaMillis, null))
        assertFalse(slateMediaEligible(SlateMaxMediaMillis + 1, null))
        assertTrue(slateMediaEligible(clip, episode), "a slate is well under the episode it stands in for")
        assertFalse(slateMediaEligible(clip, 200_000), "a short title is not half again its own length")
    }

    @Test fun aSlateAgainstAKnownRuntimeStillReports() {
        val detector = started(runtimeMillis = episode)
        assertFalse(detector.take(2_000, clip, slate(), lowBitrate = false))
        assertFalse(detector.take(8_000, clip, slate(), lowBitrate = false))
        assertTrue(detector.take(20_000, clip, slate(), lowBitrate = false))
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
        val detector = started()
        val frame = slate()
        detector.take(2_000, clip, frame, lowBitrate = false)
        frame.fill(0)
        assertFalse(detector.take(8_000, clip, slate(), lowBitrate = true), "the recorded sample still describes the picture it was taken from")
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