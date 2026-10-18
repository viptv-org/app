package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.IntegrationConnection
import kotlin.test.*

class AddonInstallCompletionTest {
    @Test fun failureReleasesForRetryAndSuccessPrecedesAncillaryRefreshFailure() = runBlocking {
        val fixture = LocalSetupFixture()
        var attempts = 0
        var committed = false
        val results = mutableListOf<Boolean>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) {
                assertEquals("default", profile); assertEquals("https://fixture.invalid/manifest.json", manifestUrl)
                attempts++
                if (attempts == 1) throw LocalFailure("Install failed. Try again.")
                committed = true
                return
            }
            override suspend fun addons(): List<IntegrationConnection> {
                assertTrue(committed)
                assertEquals(listOf(false, true), results, "Install must complete before ancillary inventory/Home/schema refresh")
                throw LocalFailure("Installed, but inventory could not refresh.")
            }
        }
        val controller = ApplicationController(this, fixture.store); controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        try {
            controller.installAddon("https://fixture.invalid/manifest.json", results::add); controller.job!!.join()
            assertEquals(listOf(false), results); assertFalse(controller.state.value.busy)
            assertEquals("Install failed. Try again.", controller.state.value.error)
            controller.installAddon("https://fixture.invalid/manifest.json", results::add); controller.job!!.join()
            assertEquals(2, attempts); assertEquals(listOf(false, true), results)
            assertEquals("Installed, but inventory could not refresh.", controller.state.value.error)
            assertFalse(controller.state.value.busy)
        } finally { fixture.close(controller) }
    }

    @Test fun immediateDuplicateDoesNotCancelTheAcceptedInstallOrSubmitAgain() = runBlocking {
        val fixture = LocalSetupFixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var installs = 0; val results = mutableListOf<Boolean>(); val duplicateResults = mutableListOf<Boolean>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) {
                installs++; entered.complete(Unit); release.await(); return
            }
            override suspend fun addons(): List<IntegrationConnection> = throw LocalFailure("Refresh unavailable")
        }
        val controller = ApplicationController(this, fixture.store); controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        try {
            controller.installAddon("https://fixture.invalid/manifest.json", results::add)
            val accepted = controller.job
            controller.installAddon("https://fixture.invalid/manifest.json", duplicateResults::add)
            assertSame(accepted, controller.job)
            entered.await(); assertEquals(1, installs)
            release.complete(Unit); controller.job!!.join()
            assertEquals(listOf(true), results); assertEquals(listOf(false), duplicateResults)
        } finally { release.complete(Unit); fixture.close(controller) }
    }

    @Test fun cancelledInstallCannotPublishLateSuccessOrFailure() = runBlocking {
        for (fails in listOf(false, true)) {
            val fixture = LocalSetupFixture()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val results = mutableListOf<Boolean>(); var refreshes = 0
            val local = object : LocalConnection by fixture.local {
                override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) {
                    entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    if (fails) throw LocalFailure("Retired install failure")
                    return
                }
                override suspend fun addons(): List<IntegrationConnection> { refreshes++; return emptyList() }
            }
            val controller = ApplicationController(this, fixture.store); controller.local = local
            controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
            try {
                controller.installAddon("https://fixture.invalid/manifest.json", results::add)
                entered.await(); val installing = controller.job!!
                controller.refreshSourceIntegrations()
                release.complete(Unit); installing.join(); controller.job!!.join()
                assertTrue(installing.isCancelled); assertTrue(results.isEmpty()); assertEquals(0, refreshes)
                assertNull(controller.state.value.error)
            } finally { release.complete(Unit); fixture.close(controller) }
        }
    }

    @Test fun changedProfileOrConnectionSuppressesLateCompletionAndRefresh() = runBlocking {
        for (changeProfile in listOf(false, true)) {
            val fixture = LocalSetupFixture()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val results = mutableListOf<Boolean>(); var refreshes = 0
            val local = object : LocalConnection by fixture.local {
                override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) {
                    entered.complete(Unit); release.await(); return
                }
                override suspend fun addons(): List<IntegrationConnection> { refreshes++; return emptyList() }
            }
            val controller = ApplicationController(this, fixture.store); controller.local = local
            controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
            try {
                controller.installAddon("https://fixture.invalid/manifest.json", results::add); entered.await()
                if (changeProfile) controller.mutable.value = controller.mutable.value.copy(selectedProfile = "other")
                else controller.local = fixture.local
                release.complete(Unit); controller.job!!.join()
                assertTrue(results.isEmpty()); assertEquals(0, refreshes); assertNull(controller.state.value.error)
            } finally { release.complete(Unit); fixture.close(controller) }
        }
    }
}
