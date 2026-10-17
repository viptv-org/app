package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class StartupStorageFailureTest {
    @Test fun unreadableVaultShowsSafeConnectionErrorWithoutClearingCredentialsOrStartingNetwork() = runBlocking<Unit> {
        var cleared = 0
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = error("private vault diagnostic")
            override suspend fun save(session: SavedRemoteSession) = error("unexpected save")
            override suspend fun clear() { cleared++ }
        }
        val controller = ApplicationController(this, store, remoteFactory = { _, _ -> error("unexpected network") })
        controller.restore(); controller.job?.join()
        assertEquals(Screen.Connect, controller.state.value.screen)
        assertTrue(controller.state.value.error.orEmpty().contains("secure storage"))
        assertFalse(controller.state.value.error.orEmpty().contains("private"))
        assertFalse(controller.state.value.busy); assertFalse(controller.state.value.starting)
        assertEquals(0, cleared); assertTrue(controller.state.value.profiles.isEmpty())
    }

    @Test fun canceledUnreadableVaultCannotPublishAnErrorIntoReplacementState() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? {
                entered.complete(Unit); withContext(NonCancellable) { release.await() }; error("late vault failure")
            }
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(this, store)
        try {
            controller.restore(); val restoring = requireNotNull(controller.job); entered.await()
            controller.disconnect(); release.complete(Unit); restoring.join()
            assertNull(controller.state.value.error); assertEquals(Screen.Connect, controller.state.value.screen)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin() }
    }
}
