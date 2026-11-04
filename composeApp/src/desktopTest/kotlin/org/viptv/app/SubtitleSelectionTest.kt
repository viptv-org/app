package org.viptv.app

import org.viptv.app.features.player.SubtitleSelection
import org.viptv.app.features.player.idOrNull
import org.viptv.app.features.player.resolved
import org.viptv.app.player.PlaybackTrack
import org.viptv.app.player.PlaybackTracks
import kotlin.test.*

class SubtitleSelectionTest {
    @Test fun aPendingTrackIsNotResolvedUntilTheRendererSelectsIt() {
        val pending = SubtitleSelection.Track(Int.MIN_VALUE)
        assertFalse(pending.resolved(PlaybackTracks(subtitles = listOf(PlaybackTrack(Int.MIN_VALUE, "English", false)))))
        assertFalse(pending.resolved(PlaybackTracks(subtitles = listOf(PlaybackTrack(3, "English", true)))))
        assertTrue(pending.resolved(PlaybackTracks(subtitles = listOf(PlaybackTrack(Int.MIN_VALUE, "English", true)))))
    }

    @Test fun pendingOffResolvesWhenNothingIsSelected() {
        val pending = SubtitleSelection.Off
        assertFalse(pending.resolved(PlaybackTracks(subtitles = listOf(PlaybackTrack(3, "English", true)))))
        assertTrue(pending.resolved(PlaybackTracks(subtitles = listOf(PlaybackTrack(3, "English", false)))))
        assertTrue(pending.resolved(PlaybackTracks()))
    }

    @Test fun idOrNullKeepsOffApartFromARealTrack() {
        assertEquals(null, SubtitleSelection.Off.idOrNull)
        assertEquals(7, SubtitleSelection.Track(7).idOrNull)
    }
}
