package org.viptv.app

import org.viptv.app.features.player.bufferedFractions
import org.viptv.app.player.*
import kotlin.test.*

class BufferedRangesTest {
    @Test fun theRendererBufferMovesOntoTheTitleClock() {
        val input = PlaybackInput("lease", "https://media.example/clip", timelineOffsetMillis = 48_600, durationMillis = 600_000)
        assertEquals(listOf(BufferedRange(48_600, 78_600)), input.mediaBuffered(listOf(BufferedRange(0, 30_000))).ranges)
    }

    @Test fun aRollingWindowOffsetAppliesToo() {
        val input = PlaybackInput("lease", "https://media.example/index.m3u8", timelineOffsetMillis = 600_000, durationMillis = 2_700_000)
        assertEquals(listOf(BufferedRange(1_680_000, 1_740_000)),
            input.mediaBuffered(listOf(BufferedRange(0, 60_000)), windowOffsetMillis = 1_080_000).ranges)
    }

    @Test fun disjointRangesStaySeparate() {
        val input = PlaybackInput("local", "file:///movie")
        val ranges = listOf(BufferedRange(0, 30_000), BufferedRange(120_000, 180_000))
        assertEquals(ranges, input.mediaBuffered(ranges).ranges)
    }

    @Test fun emptyAndInvertedRangesAreDropped() {
        val input = PlaybackInput("local", "file:///movie")
        assertEquals(emptyList(), input.mediaBuffered(listOf(BufferedRange(5_000, 5_000), BufferedRange(9_000, 4_000))).ranges)
        assertEquals(emptyList(), input.mediaBuffered(emptyList()).ranges)
    }

    @Test fun theBarMapsRangesOntoTheMediaLength() {
        assertEquals(listOf(0f to .25f, .5f to .75f),
            bufferedFractions(listOf(BufferedRange(0, 60_000), BufferedRange(120_000, 180_000)), 240_000))
    }

    @Test fun rangesOutsideTheLengthAreClippedNotMoved() {
        assertEquals(listOf(0f to .5f), bufferedFractions(listOf(BufferedRange(-10_000, 120_000)), 240_000))
        assertEquals(listOf(.75f to 1f), bufferedFractions(listOf(BufferedRange(180_000, 400_000)), 240_000))
        assertEquals(emptyList(), bufferedFractions(listOf(BufferedRange(240_000, 260_000)), 240_000))
    }

    @Test fun anUnknownOrLiveLengthDrawsNothing() {
        val ranges = listOf(BufferedRange(0, 60_000))
        assertEquals(emptyList(), bufferedFractions(ranges, null))
        assertEquals(emptyList(), bufferedFractions(ranges, 0))
        assertEquals(emptyList(), bufferedFractions(emptyList(), 240_000))
    }
}
