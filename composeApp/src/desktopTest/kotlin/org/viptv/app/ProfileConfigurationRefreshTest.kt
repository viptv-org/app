package org.viptv.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.configureSourceIntegration
import org.viptv.app.state.removeSourceIntegration
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.LiveChannel
import org.viptv.engine.wire.LivePage
import org.viptv.engine.wire.LiveQuery
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileConfigurationRefreshTest {
    private fun channel(id: String, connection: String) = LiveChannel(id, connection,
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
            override suspend fun liveChannels(profile: String, query: LiveQuery): LivePage {
                liveReads++
                return if (configured) LivePage(channels = listOf(channel("xtream:1", "xtream:1")), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE)
                else LivePage(channels = emptyList(), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE)
            }
        }
        val controller = fixture.controller { local }
        controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Live, localMode = true, selectedProfile = "default",
            sourceIntegrations = SourceIntegrationSnapshot(revision = 1), livePage = LivePage(channels = emptyList(), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE))
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
            override suspend fun liveChannels(profile: String, query: LiveQuery): LivePage {
                liveReads++
                return if (removed) LivePage(channels = emptyList(), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE)
                else LivePage(channels = listOf(channel("xtream:1", "xtream:1")), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE)
            }
        }
        val controller = fixture.controller { local }
        controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Live, localMode = true, selectedProfile = "default",
            sourceIntegrations = configuredSnapshot(), livePage = LivePage(channels = listOf(channel("xtream:1", "xtream:1")), groups = emptyList(), connections = emptyList(), complete = true, availability = Availability.AVAILABLE))
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