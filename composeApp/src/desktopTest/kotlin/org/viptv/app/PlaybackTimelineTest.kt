package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class PlaybackTimelineTest {
    @Test fun gatewayCutPreservesOriginalHistoryClockAndFullDuration() {
        val input = PlaybackInput("lease", "https://media.example/clip", timelineOffsetMillis = 48_600, durationMillis = 1_855_968)
        val progress = input.mediaProgress(12_300, 30_000)
        assertEquals(60_900L, progress.positionMillis)
        assertEquals(1_855_968L, progress.durationMillis)
        assertEquals(48_600L, input.mediaProgress(-10, null).positionMillis)
    }
    @Test fun standaloneAndDirectPlaybackKeepTheirOwnClock() {
        val input = PlaybackInput("local", "file:///movie")
        assertEquals(PlayerFact.Progress(12_300, 30_000), input.mediaProgress(12_300, 30_000))
        assertNull(input.mediaProgress(0, null).durationMillis)
    }
    @Test fun rollingHlsWindowPreservesFortyMinutePositionAndTrackingClock() {
        val input = PlaybackInput("lease", "https://media.example/index.m3u8", durationMillis = 2_700_000)
        assertEquals(PlayerFact.Progress(2_400_000, 2_700_000),
            input.mediaProgress(720_000, 720_000, windowOffsetMillis = 1_680_000))
        assertEquals(PlayerFact.Progress(2_400_000, 2_700_000),
            input.mediaProgress(714_000, 720_000, windowOffsetMillis = 1_686_000))
    }
    @Test fun rollingWindowAndGatewaySeekHaveIndependentOrigins() {
        val input = PlaybackInput("lease", "https://media.example/index.m3u8", timelineOffsetMillis = 600_000, durationMillis = 2_700_000)
        assertEquals(PlayerFact.Progress(2_400_000, 2_700_000),
            input.mediaProgress(720_000, 720_000, windowOffsetMillis = 1_080_000))
        assertEquals(720_000L, input.mediaSeekPosition(2_400_000, windowOffsetMillis = 1_080_000))
        assertEquals(0L, input.mediaSeekPosition(1_000, windowOffsetMillis = 1_080_000))
    }
}
