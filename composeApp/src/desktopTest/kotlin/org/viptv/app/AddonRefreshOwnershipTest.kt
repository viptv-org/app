package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class AddonRefreshOwnershipTest {
    private val addon = IntegrationConnection("addon:fixture", "stremio", "local", null, "Installed fixture", true, 1, listOf(Capability.SOURCES))
    private val home = HomeSnapshot(Scope("local", "device", "default", 1), 2, rows = emptyList(), complete = true)
    private val schemas = SourceIntegrationSnapshot(listOf(SourceIntegrationSchema("fixture", "Installed schema", "Ready", true)), 2)

    @Test fun ordinaryHomeNavigationDuringInventoryKeepsDestinationAndAcceptsOwnedResults() = runBlocking {
        val fixture = IptvSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) = Unit
            override suspend fun addons(): List<IntegrationConnection> { entered.complete(Unit); release.await(); return listOf(addon) }
            override suspend fun enabledAddons(profile: String) = setOf(addon.id)
            override suspend fun browsingConfiguration(profile: String): BrowsingConfiguration? = null
            override suspend fun refreshHome(profile: String) = home
            override suspend fun sourceIntegrationSchemas(profile: String) = schemas
        }
        val controller = ApplicationController(this, fixture.store); controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        try {
            controller.installAddon("https://fixture.invalid/manifest.json") {}
            entered.await(); controller.navigate(Screen.Home)
            release.complete(Unit); controller.job!!.join()
            assertEquals(Screen.Home, controller.state.value.screen, "Suspended inventory must not restore its captured Settings state")
            assertEquals(listOf(addon), controller.state.value.addons); assertEquals(setOf(addon.id), controller.state.value.enabledAddonIds)
            assertEquals(home, controller.state.value.home); assertEquals(schemas, controller.state.value.sourceIntegrations)
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); fixture.close(controller) }
    }

    @Test fun everyFollowupSuppressesLateResultsAndErrorsAfterCoroutineOrOwnerChanges() = runBlocking {
        for (phase in listOf("inventory", "home", "schemas")) for (change in listOf("cancel", "generation", "device", "remote", "profile")) for (fails in listOf(false, true)) {
            val label = "$phase/$change/fails=$fails"
            val fixture = IptvSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val reads = mutableListOf<String>(); val results = mutableListOf<Boolean>()
            suspend fun read(name: String) {
                reads += name
                if (phase == name) {
                    entered.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    if (fails) throw LocalFailure("Retired $name failure")
                }
            }
            val local = object : LocalConnection by fixture.local {
                override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) = Unit
                override suspend fun addons(): List<IntegrationConnection> { read("inventory"); return listOf(addon) }
                override suspend fun enabledAddons(profile: String) = setOf(addon.id)
                override suspend fun browsingConfiguration(profile: String): BrowsingConfiguration? = null
                override suspend fun refreshHome(profile: String): HomeSnapshot { read("home"); return home }
                override suspend fun sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot { read("schemas"); return schemas }
            }
            val controller = ApplicationController(this, fixture.store); controller.local = local
            controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
            var replacement: RemoteConnection? = null
            try {
                controller.installAddon("https://fixture.invalid/manifest.json", results::add)
                entered.await(); val operation = controller.job!!
                when (change) {
                    "cancel" -> operation.cancel()
                    "generation" -> controller.generation++
                    "device" -> controller.local = fixture.local
                    "remote" -> {
                        replacement = RemoteConnection("https://replacement.example", fixture.store,
                            HttpClient(MockEngine { error("Retired followup must not request replacement connection") }) { configureTransport() }, browserMode = false)
                        controller.connection = replacement
                    }
                    "profile" -> controller.mutable.value = controller.mutable.value.copy(selectedProfile = "other")
                }
                val current = controller.mutable.value.copy(screen = Screen.Home, busy = false, home = null, addons = emptyList(),
                    enabledAddonIds = emptySet(), sourceIntegrations = SourceIntegrationSnapshot(revision = 99),
                    sourceIntegrationError = "Current schema warning", error = "Current warning")
                controller.mutable.value = current
                release.complete(Unit); operation.join()
                assertEquals(current.screen, controller.state.value.screen, label)
                assertEquals(current.selectedProfile, controller.state.value.selectedProfile, label)
                assertEquals(current.addons, controller.state.value.addons, label)
                assertEquals(current.enabledAddonIds, controller.state.value.enabledAddonIds, label)
                assertEquals(current.home, controller.state.value.home, label)
                assertEquals(current.sourceIntegrations, controller.state.value.sourceIntegrations, label)
                assertEquals(current.sourceIntegrationError, controller.state.value.sourceIntegrationError, label)
                assertEquals(current.error, controller.state.value.error, label)
                assertEquals(listOf(true), results, label)
                assertEquals(listOf("inventory", "home", "schemas").takeWhile { it != phase } + phase, reads, label)
            } finally { release.complete(Unit); replacement?.close(); fixture.close(controller) }
        }
    }

    @Test fun busyUnrelatedOperationRejectsSubmissionAndReleasesItsCallbackWithoutChangingTheJob() = runBlocking {
        val fixture = IptvSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var commits = 0; val results = mutableListOf<Boolean>()
        val local = object : LocalConnection by fixture.local {
            override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) { commits++ }
            override suspend fun sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot {
                entered.complete(Unit); release.await(); return schemas
            }
        }
        val controller = ApplicationController(this, fixture.store); controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        try {
            controller.refreshSourceIntegrations(); entered.await()
            val existingJob = controller.job; val existingState = controller.state.value; val existingGeneration = controller.generation
            controller.installAddon("https://fixture.invalid/manifest.json", results::add)
            assertEquals(listOf(false), results); assertEquals(0, commits)
            assertSame(existingJob, controller.job); assertFalse(existingJob!!.isCancelled)
            assertEquals(existingGeneration, controller.generation); assertEquals(existingState, controller.state.value)
            release.complete(Unit); existingJob.join()
            assertEquals(schemas, controller.state.value.sourceIntegrations); assertNull(controller.state.value.error)
        } finally { release.complete(Unit); fixture.close(controller) }
    }
}
