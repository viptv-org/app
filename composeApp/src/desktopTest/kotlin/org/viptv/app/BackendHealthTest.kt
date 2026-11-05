package org.viptv.app

import org.viptv.app.state.BackendHealth
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BackendHealthTest {
    private val time = TestTimeSource()
    private val health = BackendHealth(time)

    @Test fun aFirstUnreachableBackendOpensAnOutageAndRepeatsWidenTheRetry() {
        assertTrue(health.retryDue(), "The first attempt is never delayed")
        assertTrue(health.unreachable())
        assertTrue(health.offline())
        assertFalse(health.retryDue())
        time += 9.seconds
        assertFalse(health.retryDue())
        time += 1.seconds
        assertTrue(health.retryDue(), "The first retry waits the copy's ten seconds")

        assertFalse(health.unreachable(), "A repeat failure is the same outage")
        assertTrue(health.offline())
        time += 19.seconds
        assertFalse(health.retryDue())
        time += 1.seconds
        assertTrue(health.retryDue(), "The second retry doubles")

        health.unreachable()
        time += 39.seconds
        assertFalse(health.retryDue())
        time += 1.seconds
        assertTrue(health.retryDue(), "The third retry doubles again")

        health.unreachable()
        time += 59.seconds
        assertFalse(health.retryDue())
        time += 1.seconds
        assertTrue(health.retryDue(), "The fourth retry reaches the cap")

        health.unreachable()
        time += 60.seconds
        assertTrue(health.retryDue(), "The cap holds")
    }

    @Test fun anAnswerEndsTheOutageAndResetsTheRetry() {
        health.unreachable()
        time += 10.seconds
        assertTrue(health.retryDue())
        health.answered()
        assertFalse(health.offline())
        assertTrue(health.retryDue())
        health.unreachable()
        time += 9.seconds
        assertFalse(health.retryDue(), "The first retry after recovery waits ten seconds again")
        time += 1.seconds
        assertTrue(health.retryDue())
    }

    @Test fun aReachableBackendThatFailedTheWorkRetriesSlowerWithoutClaimingAnOutage() {
        health.failed()
        assertFalse(health.offline(), "A server that answered is not unreachable")
        time += 9.seconds
        assertFalse(health.retryDue())
        time += 1.seconds
        assertTrue(health.retryDue())
        health.failed()
        time += 19.seconds
        assertFalse(health.retryDue(), "Its repeats widen the interval too")
        health.answered()
        assertTrue(health.retryDue())
    }
}
