package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import kotlin.test.*

class PlaybackFrameAcknowledgmentTest {
    @Test fun queuedStaleProofMakesNoRequest() = runBlocking {
        var calls = 0
        acknowledgePlaybackFrame({ false }) { calls++ }
        assertEquals(0, calls)
    }

    @Test fun transientFailureRetriesAndSuccessStops() = runBlocking {
        var calls = 0
        acknowledgePlaybackFrame({ true }) { if (++calls == 1) throw RemoteFailure(503, "Temporary") }
        assertEquals(2, calls)
    }

    @Test fun revocationBetweenAttemptsPreventsRetry() = runBlocking {
        var active = true
        var calls = 0
        acknowledgePlaybackFrame({ active }) { calls++; active = false; throw RemoteFailure(502, "Temporary") }
        assertEquals(1, calls)
    }

    @Test fun forbiddenIsNeverRetried() = runBlocking {
        var calls = 0
        assertFailsWith<RemoteFailure> {
            acknowledgePlaybackFrame({ true }) { calls++; throw RemoteFailure(403, "Revoked") }
        }
        assertEquals(1, calls)
    }
}
