package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RunCancellationBusyTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun runCancelledBeforeItIsDispatchedClearsBusy() = runTest {
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "profile")
        var ran = false
        controller.run { ran = true }
        assertTrue(controller.state.value.busy)
        requireNotNull(controller.job).cancel()
        advanceUntilIdle()
        assertFalse(ran, "A run cancelled before it starts never runs its body")
        assertFalse(controller.state.value.busy)
    }

    @Test fun leavingThePlayerClearsBusyWhenItsRunNeverStarted() = runTest {
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = "profile",
            playback = PlaybackInput("fixture", "https://media.example/fixture"))
        var ran = false
        controller.run { ran = true }
        assertTrue(controller.state.value.busy)
        controller.navigate(Screen.Connect)
        advanceUntilIdle()
        assertFalse(ran, "Leaving the player stops the run before its body")
        assertFalse(controller.state.value.busy)
    }

    @Test fun aSupersededRunFinishingLateLeavesTheNewerRunBusy() = runTest {
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "profile")
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        controller.run { first.await() }
        advanceUntilIdle()
        val superseded = requireNotNull(controller.job)
        controller.run { second.await() }
        advanceUntilIdle()
        assertTrue(superseded.isCompleted)
        assertTrue(controller.state.value.busy, "A superseded run must not clear the run that replaced it")
        second.complete(Unit)
        advanceUntilIdle()
        assertFalse(controller.state.value.busy)
    }
}
