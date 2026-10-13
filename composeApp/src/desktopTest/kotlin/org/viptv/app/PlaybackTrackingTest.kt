package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.PlaybackEvent
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.PlaybackTracking
import kotlin.test.*

class PlaybackTrackingTest {
    @Test fun transitionsRemainOrderedAndClosedAttemptsCannotRestart() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val received = mutableListOf<Pair<PlaybackEvent, Long>>()
        val tracker = PlaybackTracking(this, { event, position, _, _ ->
            if (event == PlaybackEvent.Start) first.await()
            received += event to position
        }, { fail("Unexpected tracking failure") })
        tracker.transition(PlaybackEvent.Start, PlayerFact.Progress(10, 100))
        tracker.transition(PlaybackEvent.Start, PlayerFact.Progress(20, 100))
        tracker.transition(PlaybackEvent.Pause, PlayerFact.Progress(30, 100))
        val stopped = tracker.transition(PlaybackEvent.Stop, PlayerFact.Progress(40, 100))
        tracker.transition(PlaybackEvent.Start, PlayerFact.Progress(50, 100))
        first.complete(Unit); stopped?.join()
        assertEquals(listOf(PlaybackEvent.Start to 10L, PlaybackEvent.Pause to 30L, PlaybackEvent.Stop to 40L), received)
    }
    @Test fun stopSurvivesAnAlreadyCancelledUiScope() = runBlocking {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Default)
        owner.cancel()
        val received = CompletableDeferred<PlaybackEvent>()
        val tracker = PlaybackTracking(scope, { event, _, _, _ -> received.complete(event); Unit }, { fail("Stop failed") })
        tracker.transition(PlaybackEvent.Stop, PlayerFact.Progress(12, 100))?.join()
        assertEquals(PlaybackEvent.Stop, withTimeout(1_000) { received.await() })
    }

}
