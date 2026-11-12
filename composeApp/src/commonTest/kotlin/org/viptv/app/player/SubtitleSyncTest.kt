package org.viptv.app.player

import kotlin.test.*

class SubtitleSyncTest {
    private fun session(audio: String = "audio") = SubtitleSyncSession().apply {
        select(SubtitleSyncIdentity("media", audio, "subtitle"), listOf(SubtitleCueInterval(0, 20_000_000)))
        setLocalAudioAvailable(true)
        setAutomatic(true)
        subtitleTimeUs(10_000_000)
    }

    @Test fun anchoredProjectionAndDelaySign() {
        val correction = SubtitleCorrection(600_000_000, 2_000_000, 1.001)
        val playerTimeUs = 592_050_000L
        val effectiveOriginUs = 60_000_000L
        assertEquals(650_000_000L, correction.subtitleTimeUs(playerTimeUs + effectiveOriginUs))
        assertEquals(650_000_000L, correction.subtitleTimeUs(653_050_000, 1_000_000))
    }

    @Test fun changingAudioInvalidatesAcceptedCorrection() {
        val session = session("original")
        session.offer(session.epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 1_000_000))
        assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000, true))
        val previous = session.epoch
        session.audioChanged("dub")
        assertFalse(session.offer(previous, 99, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
    }

    @Test fun laterAbstentionKeepsAcceptedCorrectionAcrossPendingSwap() {
        val session = session()
        assertTrue(session.offer(session.epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 1_000_000), 5))
        assertTrue(session.offer(session.epoch, 1, SubtitleSyncAvailability.Ambiguous, observationRevision = 6))
        assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000, true))
        assertEquals(SubtitleSyncAvailability.Ambiguous, session.availability)
        assertFalse(session.offer(session.epoch, 2, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0), 5))
    }

    @Test fun gapSwapManualFreezeAndStaleResults() {
        val session = session()
        val epoch = session.epoch
        assertTrue(session.offer(epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 2_000_000)))
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        assertEquals(8_000_000L, session.subtitleTimeUs(10_000_000, cueGap = true))
        assertFalse(session.offer(epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
        session.manualDelay(1_000_000)
        assertFalse(session.offer(epoch, 2, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
        assertEquals(7_000_000L, session.subtitleTimeUs(10_000_000, cueGap = true))
        session.resumeAutomatic()
        assertFalse(session.offer(epoch, 3, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        session.select(SubtitleSyncIdentity("media", "other dub", "subtitle"))
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        assertEquals(SubtitleSyncAvailability.Observing, session.availability)
    }

    @Test fun discontinuityRetainsOnlyAcceptedCorrection() {
        val session = session()
        session.offer(session.epoch, 0, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, -1_000_000))
        val previous = session.epoch
        session.discontinuity()
        assertEquals(5_000_000L, session.subtitleTimeUs(5_000_000, true))
        assertFalse(session.offer(previous, 99, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0)))
    }

    @Test fun defaultOffAndExplicitOnOffReset() {
        val session = SubtitleSyncSession()
        session.select(SubtitleSyncIdentity("media", "audio", "subtitle"), listOf(SubtitleCueInterval(0, 20_000_000)))
        session.setLocalAudioAvailable(true)
        assertFalse(session.ui.value.automatic)
        assertNull(session.target.value)
        assertFalse(session.offer(session.epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 1_000_000)))
        session.setAutomatic(true)
        assertNotNull(session.target.value)
        session.offer(session.epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 1_000_000))
        assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000, true))
        session.adjustManual(500_000)
        assertNull(session.target.value)
        assertEquals(8_500_000L, session.subtitleTimeUs(10_000_000))
        session.setAutomatic(false)
        assertEquals(9_500_000L, session.subtitleTimeUs(10_000_000))
        session.setAutomatic(true)
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        session.adjustManual(-500_000)
        assertEquals(10_500_000L, session.subtitleTimeUs(10_000_000))
        session.resetTiming()
        assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        assertNotNull(session.target.value)
    }

    @Test fun acceptedSwapWaitHasBoundedContentTime() {
        val session = session()
        session.offer(session.epoch, 1, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 1_000_000))
        assertEquals(14_999_999L, session.subtitleTimeUs(14_999_999))
        assertEquals(14_000_000L, session.subtitleTimeUs(15_000_000))
    }

    @Test fun vttAndSrtCanonicalOverlapAndBounds() {
        val timeline = parseProviderTextCues("WEBVTT\n\nfirst\n00:01.000 --> 00:03.000 align:center\n<v Person>Hello &amp; welcome</v>\n\n00:00:02.000 --> 00:00:04.000\nOther".encodeToByteArray())
        assertEquals(listOf("Hello & welcome", "Other"), timeline.cuesAt(2_000_000))
        assertEquals(listOf("Other"), timeline.cuesAt(3_000_000))
        assertTrue(timeline.cuesAt(4_000_000).isEmpty())
        val srt = parseProviderTextCues("1\r\n00:00:01,000 --> 00:00:02,000\r\nHello\r\nworld".encodeToByteArray())
        assertEquals(listOf("Hello\nworld"), srt.cuesAt(1_500_000))
        assertFailsWith<IllegalArgumentException> { parseProviderTextCues("1\n00:00:02,000 --> 00:00:01,000\nbad".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { parseProviderTextCues(ByteArray(8 * 1024 * 1024 + 1)) }
    }
}
