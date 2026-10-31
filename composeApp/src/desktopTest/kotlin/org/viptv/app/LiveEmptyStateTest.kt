package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.live.*
import org.viptv.app.state.*
import kotlin.test.*

class LiveEmptyStateTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun page(connections: Int = 1, channels: Int = 0, availability: String = "Available") =
        LiveCatalogPage(channels = List(channels) { LiveCatalogChannel("$it", "m3u:1", org.viptv.engine.wire.Card(
            org.viptv.engine.wire.MediaIdentity("$it", "live", emptyList()), provider_id = "m3u:1", title = "Channel $it", badges = emptyList())) },
            availability = availability, connections = List(connections) {
                org.viptv.engine.wire.IntegrationConnection("m3u:$it", "m3u", "7", name = "Feed $it", enabled = true, revision = 1, capabilities = emptyList())
            })

    @Test fun thePageFactsPickTheStateInTheSpecifiedOrder() {
        assertEquals(LiveSpread.Channels, liveSpread(page(channels = 1), busy = false, filtered = false))
        assertEquals(LiveSpread.Channels, liveSpread(page(channels = 1), busy = false, filtered = true), "a full page is a full page")
        assertEquals(LiveSpread.Filtered, liveSpread(page(channels = 0), busy = false, filtered = true), "a filter applied wins over the empty states")
        assertEquals(LiveSpread.Filtered, liveSpread(null, busy = true, filtered = true))
        assertEquals(LiveSpread.NoSource, liveSpread(page(connections = 0), busy = false, filtered = false))
        assertEquals(LiveSpread.NoSource, liveSpread(page(availability = "NeedsConfiguration"), busy = false, filtered = false))
        assertEquals(LiveSpread.Loading, liveSpread(page(channels = 0), busy = true, filtered = false))
        assertEquals(LiveSpread.Loading, liveSpread(null, busy = true, filtered = false), "the first request is open before the page exists")
        assertEquals(LiveSpread.NoChannels, liveSpread(page(channels = 0), busy = false, filtered = false))
        assertEquals(LiveSpread.NoChannels, liveSpread(null, busy = false, filtered = false), "a failed first request is answered with nothing")
    }

    @Test fun onlyTheChannelLessStatesHideGroupsAndFavourites() {
        assertFalse(LiveSpread.Channels.showsChannelRows())
        assertFalse(LiveSpread.Filtered.showsChannelRows())
        assertFalse(LiveSpread.NoSource.showsChannelRows())
        assertTrue(LiveSpread.Loading.showsChannelRows())
        assertTrue(LiveSpread.NoChannels.showsChannelRows())
    }

    @Test fun whoMayChangeSourcesFollowsTheIdentity() {
        assertTrue(AccountAccess("owner", true).managesSources)
        assertFalse(AccountAccess("member", true).managesSources, "a member is refused by the server")
        assertFalse(AccountAccess("device", false).managesSources, "a paired TV is refused by the server")
        assertFalse(AccountAccess("owner", false).managesSources, "no capability, no source writes")
        assertTrue(AccountAccess("owner", false).pairedDevice)
    }

    @Test fun setupActionsOpenSettingsOnTheirOwnSection() = runBlocking<Unit> {
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "12", localMode = true)
        controller.openLiveSetup(LiveSourceIntegrationsSection)
        assertEquals(Screen.Settings, controller.state.value.screen)
        assertEquals(LiveSourceIntegrationsSection, controller.state.value.settingsEntrySection)
        assertTrue(controller.state.value.liveSetupReturnFocus)
        controller.consumeLiveSetupFocus()
        assertFalse(controller.state.value.liveSetupReturnFocus)
        controller.navigate(Screen.Live)
        controller.openLiveSetup(LiveAddonsSection)
        assertEquals(LiveAddonsSection, controller.state.value.settingsEntrySection)
        controller.navigate(Screen.Search)
        assertFalse(controller.state.value.liveSetupReturnFocus, "leaving for another page drops the return focus")
        controller.job?.cancel()
    }

    @Test fun accountAccessReadsTheManageAccountCapability() = runBlocking {
        val responses = listOf(
            """{"account":{"id":"1","username":"owner","name":"Owner","role":"owner"},"role":"owner","capabilities":{"manage_account":true}}""" to AccountAccess("owner", true),
            """{"account":{"id":"2","username":"member","name":"Member","role":"member"},"role":"member","capabilities":{"manage_account":true}}""" to AccountAccess("member", true),
            """{"account":{"id":"1","username":"owner","name":"Owner","role":"owner"},"role":"device","capabilities":{"manage_account":false}}""" to AccountAccess("device", false),
            """{"account":{"id":"1","username":"owner","name":"Owner","role":"owner"},"role":"device"}""" to AccountAccess("device", false),
        )
        for ((body, expected) in responses) {
            val paths = mutableListOf<String>()
            val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
                paths += request.url.encodedPath
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }, browserMode = false)
            val controller = ApplicationController(this, store); controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "12")
            try {
                controller.loadLiveAccountAccess()
                withTimeout(10_000) { while (controller.state.value.accountAccess == null) delay(10) }
                assertEquals(expected, controller.state.value.accountAccess)
                assertEquals(listOf("/api/auth/me"), paths)
                assertFalse(controller.state.value.busy, "reading the identity never blocks the page")
            } finally { remote.close() }
        }
    }

    @Test fun localModeNeverAsksTheServerForAnIdentity() = runBlocking<Unit> {
        val paths = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            paths += request.url.encodedPath; respond("{}")
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "12", localMode = true)
        try {
            controller.loadLiveAccountAccess()
            assertNull(controller.state.value.accountAccess)
            assertTrue(paths.isEmpty())
        } finally { remote.close() }
    }
}