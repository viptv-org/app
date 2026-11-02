package org.viptv.app

import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PlaybackTracks
import org.viptv.app.player.PresentationProof
import kotlin.test.*

class PlaybackSeekFeedbackTest {
    @Test fun waitingStatusesKeepTheSeekPending() {
        assertFalse(PlayerFact.Status("Opening").settlesSeek(60_000))
        assertFalse(PlayerFact.Status("Buffering").settlesSeek(60_000))
        assertFalse(PlayerFact.Status("Preparing").settlesSeek(60_000))
    }

    @Test fun playedPausedAndPresentedPlaybackResolveTheSeek() {
        assertTrue(PlayerFact.Status("Playing").settlesSeek(60_000))
        assertTrue(PlayerFact.Status("Paused").settlesSeek(60_000))
        assertTrue(PlayerFact.Presentation(PresentationProof.BrowserCompositorPresented).settlesSeek(60_000))
    }

    @Test fun theSeekTargetOnItsOwnIsNotAResumedClock() {
        assertFalse(PlayerFact.Progress(60_000, 300_000).settlesSeek(60_000))
        assertFalse(PlayerFact.Progress(60_400, 300_000).settlesSeek(60_000))
        assertTrue(PlayerFact.Progress(60_600, 300_000).settlesSeek(60_000))
        assertFalse(PlayerFact.Progress(90_000, 300_000).settlesSeek(null))
    }

    @Test fun failureAndARefusedControlResolveTheSeek() {
        assertTrue(PlayerFact.Failed.settlesSeek(60_000))
        assertTrue(PlayerFact.ControlFailed.settlesSeek(60_000))
    }

    @Test fun propertiesAndTrackFactsNeverResolveTheSeek() {
        assertFalse(PlayerFact.Properties(1f, 1.0).settlesSeek(60_000))
        assertFalse(PlayerFact.Tracks(PlaybackTracks()).settlesSeek(60_000))
    }
}
