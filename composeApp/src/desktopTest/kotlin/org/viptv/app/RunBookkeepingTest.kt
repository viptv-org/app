package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * The controller starts runs from the UI thread and from jobs on `Dispatchers.Default`, so a run's
 * claim and another run's clearing settle can cross. Whichever run claimed last owns and clears.
 */
class RunBookkeepingTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun overlappingRunsFromBothThreadsAlwaysClearBusy() = runBlocking {
        repeat(40) { round ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = ApplicationController(scope, store)
            controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "profile")
            val together = CountDownLatch(1)
            var backgroundFailure: Throwable? = null
            try {
                val background = thread {
                    together.await()
                    try { repeat(4) { controller.run { yield() } } }
                    catch (error: Throwable) { backgroundFailure = error }
                }
                together.countDown()
                repeat(4) { controller.run { yield() } }
                background.join()
                backgroundFailure?.let { throw it }
                withTimeoutOrNull(10_000) { while (controller.state.value.busy) delay(1) }
                assertFalse(controller.state.value.busy, "round $round: busy stayed true after both threads stopped running")
            } finally {
                scope.cancel()
            }
        }
    }
}
