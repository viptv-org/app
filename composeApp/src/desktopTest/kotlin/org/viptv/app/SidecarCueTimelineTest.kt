package org.viptv.app

import org.viptv.app.player.*
import org.viptv.engine.wire.SubtitleCandidate
import kotlin.test.*

class SidecarCueTimelineTest {
    @Test fun absoluteCuesSurviveGatewayCutRollingWindowAndBackwardSeek() {
        val timeline = SidecarCueTimeline(listOf(SidecarCueGroup(2_399_000_000, 2_402_000_000, listOf("credits"))))
        val input = PlaybackInput("cut", "https://media.example/hls", timelineOffsetMillis = 600_000)
        val before = input.mediaProgress(720_000, null, 1_080_000).positionMillis
        val after = input.mediaProgress(714_000, null, 1_086_000).positionMillis
        assertEquals(listOf("credits"), timeline.cuesAt(before * 1000))
        assertSame(timeline.cuesAt(before * 1000), timeline.cuesAt(after * 1000))
        assertEquals(emptyList(), timeline.cuesAt(0))
        assertEquals(listOf("credits"), timeline.cuesAt(before * 1000))
        assertEquals(emptyList(), timeline.cuesAt(2_402_000_000))
    }
    @Test fun overlappingStyledCueGroupsMergeAndClearAtExactBoundaries() {
        val timeline = SidecarCueTimeline(listOf(SidecarCueGroup(100, 400, listOf("top")),
            SidecarCueGroup(200, 300, listOf("bottom")), SidecarCueGroup(400, 500, listOf("next"))))
        assertEquals(listOf("top", "bottom"), timeline.cuesAt(200))
        assertEquals(listOf("top"), timeline.cuesAt(300))
        assertEquals(listOf("next"), timeline.cuesAt(400))
        assertEquals(emptyList(), timeline.cuesAt(500))
        assertEquals(listOf("top", "bottom"), timeline.cuesAt(250))
    }
    @Test fun multiLineAndOverlappingCuesKeepEveryLineOldestFirst() {
        val twoLines = "Where are you going this late?\nHome, before it rains."
        val timeline = SidecarCueTimeline(listOf(SidecarCueGroup(6_000_000, 8_000_000, listOf("- Home.")),
            SidecarCueGroup(1_000_000, 4_000_000, listOf(twoLines)),
            SidecarCueGroup(5_000_000, 8_000_000, listOf("- Where are you going?"))))
        assertEquals(listOf(twoLines), timeline.cuesAt(2_000_000))
        assertEquals(listOf("- Where are you going?"), timeline.cuesAt(5_500_000))
        assertEquals(listOf("- Where are you going?", "- Home."), timeline.cuesAt(7_000_000))
        assertEquals(emptyList(), timeline.cuesAt(8_000_000))
    }
    @Test fun replacementCuesDoNotResurrectAnEarlierOpenEndedGroup() {
        val timeline = SidecarCueTimeline(listOf(SidecarCueGroup(0, null, listOf("earlier")),
            SidecarCueGroup(100, 200, listOf("replacement"))), replace = true)
        assertEquals(listOf("replacement"), timeline.cuesAt(150))
        assertEquals(emptyList(), timeline.cuesAt(200))
        assertFailsWith<IllegalArgumentException> { SidecarCueTimeline(listOf(SidecarCueGroup(0, null, listOf("invalid")))) }
    }
    @Test fun providerTrackIdsCannotSelectAnotherSubtitleAfterReorderingOrRemoval() {
        val first = SubtitleCandidate("first", "provider", "en", "English")
        val second = SubtitleCandidate("second", "provider", "ja", "Japanese")
        val input = PlaybackInput("media", "https://media.example/video", providerSubtitles = listOf(first, second))
        val original = input.providerTracks(null)
        input.updateProviderSubtitles(listOf(second, first))
        assertEquals(first, input.providerSubtitle(original[0].id))
        assertEquals(second, input.providerSubtitle(original[1].id))
        input.updateProviderSubtitles(listOf(second))
        assertNull(input.providerSubtitle(original[0].id))
        assertEquals(second, input.providerSubtitle(original[1].id))
    }
}
