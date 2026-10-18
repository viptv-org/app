package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class HomeRefreshRecoveryTest {
    private val scope = Scope("local", "device", "default", 1)
    private val cached = HomeSnapshot(scope, 1, rows = listOf(HomeRow(id = "cached", title = "Saved catalog", provider_id = "fixture", state = RowState.EMPTY, cards = emptyList())), complete = true)
    private val prepared = cached.copy(revision = 2, rows = cached.rows.map { it.copy(state = RowState.PENDING) }, complete = false)
    private val finished = cached.copy(revision = 3)

    @Test fun failedRefreshKeepsCachedHomeAndBusyUntilProviderFinishesThenRetryReplacesIt() = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var fails = true
        val local = object : LocalConnection by fixture.local {
            override suspend fun bootstrap(profile: String?) = Bootstrap(listOf(Profile("default", "Default", null)), profile, prepared, library = emptyList())
            override suspend fun refreshHome(profile: String): HomeSnapshot {
                if (fails) { entered.complete(Unit); release.await(); throw LocalFailure("Provider unavailable. Try again.") }
                return finished
            }
        }
        val controller = fixture.controller { local }
        controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", home = cached, localMode = true)
        try {
            controller.refresh(); withTimeout(2000) { entered.await() }
            assertTrue(controller.state.value.busy)
            assertEquals(cached, controller.state.value.home, "Prepared plans must not discard the last usable shelves")
            release.complete(Unit); controller.job?.join()
            assertEquals(cached, controller.state.value.home); assertFalse(controller.state.value.busy)
            assertEquals("Provider unavailable. Try again.", controller.state.value.error)
            fails = false; controller.refresh(); controller.job?.join()
            assertEquals(finished.revision, controller.state.value.home?.revision)
            assertEquals(finished.rows.single(), controller.state.value.home?.rows?.last())
            assertNull(controller.state.value.error)
            assertFalse(controller.state.value.busy)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun navigationDuringRefreshKeepsDestinationAndSearchDraft() = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun bootstrap(profile: String?) = Bootstrap(listOf(Profile("default", "Default", null)), profile, prepared, library = emptyList())
            override suspend fun refreshHome(profile: String): HomeSnapshot { entered.complete(Unit); release.await(); return finished }
        }
        val controller = fixture.controller { local }; controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", home = cached, localMode = true, searchTerm = "retained query")
        try {
            controller.refresh(); withTimeout(2000) { entered.await() }; controller.navigate(Screen.Search)
            release.complete(Unit); controller.job?.join()
            assertEquals(Screen.Search, controller.state.value.screen)
            assertEquals("retained query", controller.state.value.searchTerm)
            assertEquals(finished, controller.state.value.home); assertFalse(controller.state.value.busy)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun canceledRefreshCannotPublishLateHomeIntoAnotherSession() = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun bootstrap(profile: String?) = Bootstrap(listOf(Profile("default", "Default", null)), profile, prepared, library = emptyList())
            override suspend fun refreshHome(profile: String): HomeSnapshot {
                entered.complete(Unit); withContext(NonCancellable) { release.await() }; return finished
            }
        }
        val controller = fixture.controller { local }; controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", home = cached, localMode = true)
        try {
            controller.refresh(); val refreshing = requireNotNull(controller.job); withTimeout(2000) { entered.await() }
            controller.disconnect(); release.complete(Unit); refreshing.join()
            assertEquals(Screen.Connect, controller.state.value.screen); assertNull(controller.state.value.home)
            assertNull(controller.state.value.selectedProfile); assertFalse(controller.state.value.busy)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }
}
