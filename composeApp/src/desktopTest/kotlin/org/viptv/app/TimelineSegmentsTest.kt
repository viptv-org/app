package org.viptv.app

import org.viptv.app.features.player.TimelineSegment
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.features.player.timelineSegments
import kotlin.test.*

class TimelineSegmentsTest {
    private val intro = TvSkipRange(30_000, 60_000, TvSkipKind.Intro)
    private val credits = TvSkipRange(1_290_000, 1_380_000, TvSkipKind.Credits)

    @Test fun noDurationOrNoRangesProduceNothing() {
        assertTrue(emptyList<TvSkipRange>().timelineSegments(1_800_000).isEmpty())
        assertTrue(listOf(intro).timelineSegments(null).isEmpty())
        assertTrue(listOf(intro).timelineSegments(0).isEmpty())
    }

    @Test fun invertedAndEmptyRangesAreDropped() {
        assertEquals(emptyList(), listOf(TvSkipRange(60_000, 30_000, TvSkipKind.Intro)).timelineSegments(1_800_000))
        assertEquals(emptyList(), listOf(TvSkipRange(60_000, 60_000, TvSkipKind.Intro)).timelineSegments(1_800_000))
    }

    @Test fun rangesOutsideTheDurationAreClampedOrDropped() {
        val late = TvSkipRange(2_000_000, 2_100_000, TvSkipKind.Credits)
        assertEquals(emptyList(), listOf(late).timelineSegments(1_800_000), "A range after the end has nothing to draw")
        assertEquals(listOf(TimelineSegment(1_290_000, 1_800_000, TvSkipKind.Credits, 1_800_000)),
            listOf(credits.copy(endMillis = 2_000_000)).timelineSegments(1_800_000), "A range crossing the end is clamped to it")
        assertEquals(listOf(TimelineSegment(0, 60_000, TvSkipKind.Intro, 1_800_000)),
            listOf(intro.copy(startMillis = -10_000)).timelineSegments(1_800_000), "A range before the start is clamped to it")
    }

    @Test fun fractionsAndDescriptionsFollowTheKindAndTimes() {
        val segments = listOf(intro, credits).timelineSegments(1_800_000)
        assertEquals(listOf(30_000L, 1_290_000L), segments.map { it.startMillis })
        assertEquals(30_000f / 1_800_000f, segments[0].startFraction)
        assertEquals(60_000f / 1_800_000f, segments[0].endFraction)
        assertEquals("Intro 0:30–1:00", segments[0].description)
        assertEquals("Credits 21:30–23:00", segments[1].description)
    }
}