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
}
