package org.viptv.app

import org.viptv.app.player.PlaybackTransportGate
import kotlin.test.*

class PlaybackTransportGateTest {
    @Test fun openingAndQueuedPlayCannotBeUndoneByStalePause() {
        val gate = PlaybackTransportGate()
        assertFalse(gate.accept("Paused"))
        assertTrue(gate.accept("Opening"))
        gate.request(false, "Paused")
        assertFalse(gate.accept("Paused"))
        assertTrue(gate.accept("Playing"))
        assertTrue(gate.accept("Paused"), "Later external SDK pauses remain authoritative")
    }

    @Test fun queuedPauseRejectsLatePlayingUntilItIsObserved() {
        val gate = PlaybackTransportGate()
        gate.request(false, "Playing")
        gate.request(true, "Playing")
        assertFalse(gate.accept("Playing"))
        assertTrue(gate.accept("Paused"))
    }

    @Test fun idempotentRequestDoesNotBlockLaterExternalTransport() {
        val gate = PlaybackTransportGate()
        gate.request(true, "Paused")
        assertTrue(gate.accept("Playing"))
    }
}
