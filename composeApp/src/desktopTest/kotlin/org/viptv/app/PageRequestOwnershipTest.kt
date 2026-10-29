package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class PageRequestOwnershipTest {
    private val card = Card(MediaIdentity("fixture", "movie", emptyList()), provider_id = "fixture", title = "Fixture", badges = emptyList())

    @Test fun detailReadCannotRestoreDetailsAfterNavigationHome() = verify("details", false)
    @Test fun playIntentCannotOpenSourcesAfterNavigationAwayAndBack() = verify("play", false)
    @Test fun historyReadCannotRestoreHistoryAfterNavigationHome() = verify("history", false)
    @Test fun abandonedPageFailureDoesNotAttachItsErrorToHome() = verify("details", true)

    private fun verify(action: String, fail: Boolean) = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var sourceCalls = 0
        suspend fun waitForResult() { entered.complete(Unit); withContext(NonCancellable) { release.await() }; if (fail) throw LocalFailure("Retired page failure") }
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot { waitForResult(); return DetailSnapshot(card, episodes = emptyList()) }
            override suspend fun history(profile: String): List<HistoryDisplay> { waitForResult(); return emptyList() }
            override suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit): SourcesOutcome { sourceCalls++; return SourcesOutcome() }
        }
        val controller = fixture.controller { device }; controller.local = device
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", localMode = true, preferences = PreferencesSnapshot())
        try {
            when (action) { "details" -> controller.open(card); "play" -> controller.playTitle(card); else -> controller.navigate(Screen.History) }
            val pending = requireNotNull(if (action == "play") controller.sourceDetailJob else controller.job)
            withTimeout(2000) { entered.await() }
            controller.navigate(Screen.Search); controller.navigate(Screen.Home)
            assertFalse(controller.state.value.busy, "Leaving a page immediately releases its loading UI")
            release.complete(Unit); pending.join(); controller.job?.join()
            controller.sourceDetailJob?.join()
            assertEquals(Screen.Home, controller.state.value.screen)
            assertNull(controller.state.value.detail); assertNull(controller.state.value.error)
            if (action == "play") assertEquals(card, controller.state.value.sourceTitle) else assertNull(controller.state.value.sourceTitle)
            assertEquals(0, sourceCalls); assertEquals("default", controller.state.value.selectedProfile); assertFalse(controller.state.value.busy)
        } finally { release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun queuedPlayDoesNotDispatchMetadataAfterNavigationWhileWaitingForRetiredWork() = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); val priorEntered = CompletableDeferred<Unit>(); val finishPrior = CompletableDeferred<Unit>()
        var metadataCalls = 0
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot { metadataCalls++; return DetailSnapshot(card, episodes = emptyList()) }
        }
        val controller = fixture.controller { device }; controller.local = device
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", localMode = true)
        controller.job = fixture.scope.launch { withContext(NonCancellable) { priorEntered.complete(Unit); finishPrior.await() } }
        try {
            priorEntered.await(); controller.playTitle(card)
            controller.navigate(Screen.Search); controller.navigate(Screen.Home)
            finishPrior.complete(Unit); controller.job?.join()
            assertEquals(0, metadataCalls); assertEquals(Screen.Home, controller.state.value.screen)
            assertFalse(controller.state.value.findingSources); assertFalse(controller.state.value.busy)
        } finally { finishPrior.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun expiredSameSessionStillRetiresAfterLeavingItsPage() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val controller = ApplicationController(this)
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "one")
        val navigation = controller.navigationGeneration
        controller.run { controller.readCurrentPage<Unit>(navigation) {
            entered.complete(Unit); release.await(); throw RemoteFailure(401, "Session expired")
        } }
        entered.await(); controller.navigate(Screen.Home); release.complete(Unit); controller.job?.join()
        assertEquals(Screen.Connect, controller.state.value.screen)
        assertNull(controller.state.value.selectedProfile)
        assertEquals("Session expired. Sign in again.", controller.state.value.error)
    }
}
