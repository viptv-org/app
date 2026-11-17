package org.viptv.app

import org.viptv.app.features.player.SegmentAutoSkip
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.player.PlaybackInput
import kotlin.test.*

class SegmentAutoSkipTest {
    private val intro = TvSkipRange(60_000, 150_000, TvSkipKind.Intro)
    private val openingIntro = TvSkipRange(0, 90_000, TvSkipKind.Intro)
    private val recap = TvSkipRange(0, 60_000, TvSkipKind.Recap)
    private val credits = TvSkipRange(1_290_000, 1_380_000, TvSkipKind.Credits)
    private fun input(position: Long = 0, chosen: Boolean = false) = PlaybackInput("p", "https://media.invalid/v", positionMillis = position, startChosen = chosen)
    private val all: (TvSkipRange) -> Boolean = { true }

    @Test fun naturalPlaybackIntoTheSegmentSkipsIt() {
        val skip = SegmentAutoSkip(); val opened = input(30_000)
        assertNull(skip.due(opened, 59_000, listOf(intro), all))
        assertEquals(intro, skip.due(opened, 60_400, listOf(intro), all))
    }

    @Test fun aSeekIntoTheSegmentPlaysIt() {
        val skip = SegmentAutoSkip(); val opened = input(30_000)
        skip.due(opened, 50_000, listOf(intro), all)
        skip.seeked(70_000)
        assertNull(skip.due(opened, 70_000, listOf(intro), all))
        assertNull(skip.due(opened, 120_000, listOf(intro), all), "It keeps playing to its end")
    }

    @Test fun aSeekToTheSegmentStartPlaysIt() {
        val skip = SegmentAutoSkip(); val opened = input(30_000)
        skip.due(opened, 31_000, listOf(intro), all)
        skip.seeked(60_000)
        assertNull(skip.due(opened, 61_000, listOf(intro), all))
    }

    @Test fun aSeekBeforeTheSegmentSkipsItWhenPlaybackReachesIt() {
        val skip = SegmentAutoSkip(); val opened = input(30_000)
        skip.seeked(70_000)
        skip.seeked(40_000)
        assertNull(skip.due(opened, 40_000, listOf(intro), all))
        assertEquals(intro, skip.due(opened, 60_100, listOf(intro), all))
    }

    @Test fun resumingInsideTheSegmentPlaysIt() {
        val skip = SegmentAutoSkip(); val opened = input(100_000)
        assertNull(skip.due(opened, 101_000, listOf(intro), all))
    }

    @Test fun aFreshEpisodeSkipsAnIntroAtZeroEvenWhenTheMarkersArriveLate() {
        val skip = SegmentAutoSkip(); val opened = input()
        assertNull(skip.due(opened, 6_000, emptyList(), all))
        assertEquals(openingIntro, skip.due(opened, 8_000, listOf(openingIntro), all))
    }

    @Test fun watchFromTheBeginningPlaysAnIntroAtZero() {
        val skip = SegmentAutoSkip(); val opened = input(0, chosen = true)
        assertNull(skip.due(opened, 2_000, listOf(openingIntro), all))
    }

    @Test fun anUndoneSegmentNeverSkipsAgainThisPlayback() {
        val skip = SegmentAutoSkip(); val opened = input(30_000)
        val due = assertNotNull(skip.due(opened, 60_400, listOf(intro), all))
        skip.skipped(due)
        assertEquals(intro, skip.notice)
        skip.undo(intro); skip.seeked(intro.startMillis)
        assertNull(skip.notice)
        assertNull(skip.due(opened, 61_000, listOf(intro), all))
        skip.seeked(30_000)
        assertNull(skip.due(opened, 60_400, listOf(intro), all), "Running into it again still plays it")
    }

    @Test fun backToBackSegmentsSkipInTurn() {
        val skip = SegmentAutoSkip(); val opened = input()
        val first = assertNotNull(skip.due(opened, 500, listOf(recap, intro), all))
        assertEquals(recap, first); skip.skipped(first)
        assertEquals(intro, skip.due(opened, 60_000, listOf(recap, intro), all))
    }

    @Test fun aDisabledKindOnlyPlays() {
        val skip = SegmentAutoSkip(); val opened = input(1_200_000)
        assertNull(skip.due(opened, 1_291_000, listOf(credits)) { it.kind != TvSkipKind.Credits })
    }

    @Test fun aNewOpeningOfTheSamePlaybackStartsANewStretch() {
        val skip = SegmentAutoSkip()
        skip.due(input(30_000), 40_000, listOf(intro), all)
        assertNull(skip.due(input(90_000, chosen = true), 91_000, listOf(intro), all))
    }
}
