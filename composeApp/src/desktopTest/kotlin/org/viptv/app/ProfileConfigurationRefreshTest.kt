package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.connection.LiveCatalogChannel
import org.viptv.app.connection.LiveCatalogPage
import org.viptv.app.connection.LiveQuery
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.configureSourceIntegration
import org.viptv.app.state.removeSourceIntegration
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileConfigurationRefreshTest {
    private fun channel(id: String, connection: String) = LiveCatalogChannel(id, connection,
        Card(identity = MediaIdentity(id, "live", emptyList(), null), provider_id = "xtream", title = id, badges = emptyList()))

    private fun configuredSnapshot() = SourceIntegrationSnapshot(
        listOf(SourceIntegrationSchema("xtream", "Xtream", "Ready", true, connection_id = "xtream:1")), revision = 2)

    @Test fun savingAnIntegrationReloadsTheSettingsSnapshotAndLiveChannels() = runBlocking {
        val fixture = LocalSetupFixture()
        var configured = false
        var liveReads = 0
        var requestedRevision = -1L
        val local = object : LocalConnection by fixture.local {
            override suspend fun sourceIntegrationSchemas(profile: String) =
                if (configured) configuredSnapshot() else SourceIntegrationSnapshot(revision = 1)
            override suspend fun configureSourceIntegration(profile: String, revision: Long, plugin: String, configuration: JsonObject) {
                requestedRevision = revision
                configured = true
            }
            override suspend fun liveChannels(profile: String, query: LiveQuery): LiveCatalogPage {
                liveReads++
                return if (configured) LiveCatalogPage(channels = listOf(channel("xtream:1", "xtream:1")), availability = "Available")
                else LiveCatalogPage(availability = "Available")
            }
        }
        val controller = fixture.controller { local }
        controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Live, localMode = true, selectedProfile = "default",
            sourceIntegrations = SourceIntegrationSnapshot(revision = 1), livePage = LiveCatalogPage(availability = "Available"))
        try {
            controller.configureSourceIntegration("xtream", buildJsonObject { put("url", "https://provider.example") })
            controller.job!!.join()

            assertEquals(1L, requestedRevision, "the write uses the revision the card is showing")
            assertEquals(configuredSnapshot(), controller.state.value.sourceIntegrations, "the integration card reloads after saving")
            assertEquals(listOf("xtream:1"), controller.state.value.livePage?.channels?.map { it.id },
                "the Live page reloads from the new integration")
            assertEquals(1, liveReads, "Live channels are reread once when the Live screen is showing")
            assertSame(local, controller.local); assertNull(controller.connection)
            assertNull(controller.state.value.error); assertFalse(controller.state.value.busy)
        } finally { fixture.close(controller) }
    }

    @Test fun removingAnIntegrationReloadsTheSettingsSnapshotAndLiveChannels() = runBlocking {
        val fixture = LocalSetupFixture()
        var removed = false
        var liveReads = 0
        val local = object : LocalConnection by fixture.local {
            override suspend fun sourceIntegrationSchemas(profile: String) =
                if (removed) SourceIntegrationSnapshot(revision = 3) else configuredSnapshot()
            override suspend fun removeSourceIntegration(profile: String, revision: Long, connection: String) { removed = true }
            override suspend fun liveChannels(profile: String, query: LiveQuery): LiveCatalogPage {
                liveReads++
                return if (removed) LiveCatalogPage(availability = "Available")
                else LiveCatalogPage(channels = listOf(channel("xtream:1", "xtream:1")), availability = "Available")
            }
        }
        val controller = fixture.controller { local }
        controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Live, localMode = true, selectedProfile = "default",
            sourceIntegrations = configuredSnapshot(), livePage = LiveCatalogPage(channels = listOf(channel("xtream:1", "xtream:1")), availability = "Available"))
        try {
            controller.removeSourceIntegration("xtream:1")
            controller.job!!.join()

            assertEquals(SourceIntegrationSnapshot(revision = 3), controller.state.value.sourceIntegrations, "the integration card reloads after removal")
            assertTrue(controller.state.value.livePage?.channels?.isEmpty() ?: true, "the removed integration's channels leave the Live page")
            assertEquals(1, liveReads, "Live channels are reread once when the Live screen is showing")
            assertSame(local, controller.local); assertNull(controller.connection)
            assertNull(controller.state.value.error); assertFalse(controller.state.value.busy)
        } finally { fixture.close(controller) }
    }
}