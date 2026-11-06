package org.viptv.app

import kotlin.concurrent.thread
import kotlin.test.*

// The engine answers on its own dispatcher while the test thread reads: see EngineRequestRecorderScanTest for the guard.
class EngineRequestsTest {
    @Test fun anEngineAppendingWhileTheTestReadsCannotThrow() {
        val requests = EngineRequests<String>()
        val engine = thread { repeat(4_000) { index -> requests += "GET /api/$index" } }
        repeat(4_000) {
            requests.count { it.startsWith("GET ") }
            requests.snapshot().size
            requests.toSet().size
            requests.filter { it.endsWith("0") }
        }
        engine.join()
        assertEquals(4_000, requests.size)
        assertEquals(4_000, requests.snapshot().distinct().size)
    }

    @Test fun anIterationKeepsTheSnapshotItStartedWith() {
        val requests = EngineRequests<String>()
        requests += "GET /first"
        val seen = mutableListOf<String>()
        for (request in requests) {
            seen += request
            if (seen.size == 1) requests += "GET /second"
        }
        assertEquals(listOf("GET /first"), seen, "an append during iteration lands after that iteration's snapshot")
        assertEquals(listOf("GET /first", "GET /second"), requests.snapshot())
    }
}
