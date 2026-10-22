package org.viptv.app

import kotlin.test.*
import org.viptv.app.features.player.*
import org.viptv.app.player.*

class PlayerBufferSeekTest {
    private fun state() = TvPlayerState(PlaybackInput("lease", "https://media.example/movie.mp4",
        positionMillis = 60_000, durationMillis = 300_000), emptyList(), TvPlayerScene.Controls)

    @Test fun bufferingAfterPresentationDoesNotReturnToInitialStartup() {
        val state = state()
        assertFalse(state.hasPresented)
        state.observe(PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
        state.observe(PlayerFact.Status("Playing"))
        state.observe(PlayerFact.Status("Buffering"))
        assertTrue(state.buffering)
        assertTrue(state.hasPresented)
        assertEquals(60_000, state.position)
    }

    @Test fun slowStartWithStalledClockIsNotUndecodable() {
        val state = state()
        state.observe(PlayerFact.Status("Buffering"))
        repeat(30) { state.checkFirstFrame() }
        assertFalse(state.undecodable)
        state.observe(PlayerFact.Status("Playing"))
        repeat(5) { state.observe(PlayerFact.Progress(60_000L + (it + 1) * 1000, 300_000)); state.checkFirstFrame() }
        assertFalse(state.undecodable)
        state.observe(PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
        repeat(10) { state.observe(PlayerFact.Progress(70_000L + it * 1000, 300_000)); state.checkFirstFrame() }
        assertFalse(state.undecodable)
    }

    @Test fun runningClockWithoutAFrameIsUndecodableUntilAFrameArrives() {
        val state = state()
        state.observe(PlayerFact.Status("Playing"))
        repeat(6) { state.observe(PlayerFact.Progress(60_000L + (it + 1) * 1000, 300_000)); state.checkFirstFrame() }
        assertTrue(state.undecodable)
        state.observe(PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
        assertFalse(state.undecodable)
    }

    @Test fun committedSeekKeepsTargetWhileDecoderReportsItsResetPosition() {
        val state = state()
        var command: PlayerCommand? = null
        state.scrub = 180_000
        state.commit { command = it }
        assertEquals(PlayerCommand.Seek(180_000), command)
        assertNull(state.scrub)
        state.observe(PlayerFact.Progress(0, null))
        assertEquals(180_000, state.position)
        assertEquals(300_000, state.duration)
        state.observe(PlayerFact.Progress(180_100, 300_000))
        assertEquals(180_100, state.position)
    }
}
