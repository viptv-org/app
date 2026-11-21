package org.viptv.app.player

import kotlin.test.*

class PlaybackOffsetsTest {
    private fun offsets(initial: TimingOffsets = TimingOffsets(), sync: SubtitleSyncSession = SubtitleSyncSession()) = PlaybackOffsets(initial, sync)

    @Test fun eachPressMovesOneTenthAndClampsAtTenSeconds() {
        val offsets = offsets()
        offsets.shiftAudio(OFFSET_STEP_US)
        offsets.shiftAudio(OFFSET_STEP_US)
        offsets.shiftAudio(OFFSET_STEP_US)
        assertEquals(300_000L, offsets.audioUs)
        repeat(200) { offsets.shiftAudio(OFFSET_STEP_US) }
        assertEquals(OFFSET_LIMIT_US, offsets.audioUs)
        repeat(400) { offsets.shiftSubtitle(-OFFSET_STEP_US) }
        assertEquals(-OFFSET_LIMIT_US, offsets.subtitleUs)
        assertEquals(TimingOffsets(OFFSET_LIMIT_US, -OFFSET_LIMIT_US), offsets(TimingOffsets(60_000_000, -60_000_000)).state.value)
    }

    @Test fun resetsAreIndependent() {
        val offsets = offsets()
        offsets.shiftAudio(500_000)
        offsets.shiftSubtitle(-1_200_000)
        offsets.resetAudio()
        assertEquals(TimingOffsets(0, -1_200_000), offsets.state.value)
        offsets.shiftAudio(100_000)
        offsets.resetSubtitle()
        assertEquals(TimingOffsets(100_000, 0), offsets.state.value)
    }

    @Test fun labelsAreSignedTenths() {
        assertEquals("+0.3 s", offsetLabel(300_000))
        assertEquals("−1.2 s", offsetLabel(-1_200_000))
        assertEquals("0.0 s", offsetLabel(0))
        assertEquals("+10.0 s", offsetLabel(12_000_000))
    }

    @Test fun subtitleTimingDrivesProviderCuesAndARememberedValueAppliesAtOpen() {
        val sync = SubtitleSyncSession()
        val offsets = offsets(TimingOffsets(subtitleUs = 700_000), sync)
        assertEquals(700_000L, offsets.remembered.subtitleUs)
        assertEquals(9_300_000L, sync.subtitleTimeUs(10_000_000))
        offsets.shiftSubtitle(-OFFSET_STEP_US)
        assertEquals(9_400_000L, sync.subtitleTimeUs(10_000_000))
        offsets.resetSubtitle()
        assertEquals(10_000_000L, sync.subtitleTimeUs(10_000_000))
    }

    @Test fun automaticTimingClearsTheManualSubtitleOffset() {
        val sync = SubtitleSyncSession()
        val offsets = offsets(sync = sync)
        offsets.shiftSubtitle(500_000)
        offsets.automaticSubtitleTiming(true)
        assertEquals(0L, offsets.subtitleUs)
        assertTrue(sync.ui.value.automatic)
        assertEquals(10_000_000L, sync.subtitleTimeUs(10_000_000))
    }
}
