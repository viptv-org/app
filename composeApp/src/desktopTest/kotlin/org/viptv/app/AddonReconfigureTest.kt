package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.viptv.app.connection.*
import org.viptv.app.features.settings.addonConfigureFailureText
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import kotlin.test.*

class AddonReconfigureTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun server(configureBody: String = """{"configure_url":"/api/addons/configure/ticket-1","expires_in":300}""",
        status: HttpStatusCode = HttpStatusCode.OK, addonRows: String = """[{"id":7,"name":"Torrentio","enabled":true,"configurable":true},
            {"id":9,"name":"Comet","enabled":true,"configurable":false}]""") = HttpClient(MockEngine { request ->
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath == "/api/addons" -> respond("""{"items":$addonRows,"next_cursor":null}""", headers = json)
            request.url.encodedPath == "/api/profiles/default/integrations" -> respond("""{"configuration":{"scope":{"connection_id":"server","account_id":"1","profile_id":"default","revision":1},
                "mode":"Server","name":"Parent","assignments":[],"browsing_plugin":"stremio"},"connections":[]}""", headers = json)
            request.url.encodedPath == "/api/addons/7/configure-link" ->
                respond(configureBody, status, json)
            else -> respond("{}", HttpStatusCode.NotFound, json)
        }
    }) { configureTransport() }

    @Test fun addOnRowsCarryTheirConfigurability(): Unit = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, server())
        try {
            val addons = remote.addons("default")
            assertEquals(setOf("addon:7"), addons.configurable)
            assertEquals(listOf("addon:7", "addon:9"), addons.addons.map { it.id })
        } finally { remote.close() }
    }

    @Test fun theConfigureLinkIsAbsoluteAndAddressedToTheServer(): Unit = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, server())
        try {
            val link = remote.addonConfigureLink("addon:7")
            assertEquals("https://watch.example/api/addons/configure/ticket-1", link.url)
            assertEquals(300, link.expiresInSeconds)
        } finally { remote.close() }
    }

    @Test fun aConfigureLinkThatIsNotATicketIsRefused(): Unit = runBlocking {
        val remote = RemoteConnection("https://watch.example", store,
            server(configureBody = """{"configure_url":"https://elsewhere.invalid/steal"}"""))
        try {
            assertFailsWith<RemoteFailure> { remote.addonConfigureLink("addon:7") }
        } finally { remote.close() }
    }

    private class Host(client: HttpClient) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example", store, client)
        val opened = mutableListOf<String>()
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.browserOpen = { url -> opened += url }
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "default", localMode = false,
                addons = emptyList())
        }
        override fun close() { remote.close(); scope.cancel() }
    }

    @Test fun thePointerLayoutOpensTheConfigurePageInTheBrowser(): Unit = runBlocking {
        Host(server()).use { host ->
            host.controller.configureAddon("addon:7")
            withTimeout(2_000) { while (host.opened.isEmpty()) delay(10) }
            assertEquals(listOf("https://watch.example/api/addons/configure/ticket-1"), host.opened)
            assertEquals("https://watch.example/api/addons/configure/ticket-1", host.controller.mutable.value.addonConfigure?.link)
        }
    }

    @Test fun theTvLayoutKeepsTheLinkForItsQrCode(): Unit = runBlocking {
        Host(server()).use { host ->
            host.controller.configureAddon("addon:7", openInBrowser = false)
            withTimeout(2_000) { while (host.controller.mutable.value.addonConfigure?.link == null) delay(10) }
            assertTrue(host.opened.isEmpty(), "TV shows the link as a code instead of leaving")
            assertNull(host.controller.mutable.value.addonConfigure?.failure)
            assertEquals("addon:7", host.controller.mutable.value.addonConfigure?.addon)
        }
    }

    @Test fun aRefusedLinkLeavesTheFailureLineAndTryAgain(): Unit = runBlocking {
        Host(server(configureBody = """{"error":"addon_unavailable"}""", status = HttpStatusCode.Conflict)).use { host ->
            host.controller.configureAddon("addon:7")
            withTimeout(2_000) { while (host.controller.mutable.value.addonConfigure?.failure == null) delay(10) }
            val request = requireNotNull(host.controller.mutable.value.addonConfigure)
            assertNull(request.link)
            assertNotNull(request.failure)
            assertEquals(request.failure, addonConfigureFailureText(request))
            assertTrue(host.opened.isEmpty())
        }
    }

    @Test fun thePlayerConfigureSourceNamesTheAddOnBehindThePlayingSource() {
        val source = SourceCandidate("s", "addon:7", "1080p", detail = null)
        assertEquals("addon:7", ApplicationState(playingSource = source).playerAddonTarget())
        assertEquals(null, ApplicationState(playingSource = source.copy(provider_id = "iptv:2")).playerAddonTarget())
        assertEquals(null, ApplicationState().playerAddonTarget())
    }
}

class AddonReconfigureRenderTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun addon(index: Int) = org.viptv.engine.wire.IntegrationConnection("addon:$index", "stremio", "1", null,
        "Fixture $index", true, 1, listOf(org.viptv.engine.wire.Capability.DISCOVERY))

    private class Shot(width: Int, height: Int, television: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) : AutoCloseable {
        private val scene = androidx.compose.ui.ImageComposeScene(width, height, androidx.compose.ui.unit.Density(1f)) {
            org.viptv.app.theme.ViptvTheme(television, pointer = !television) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(), color = org.viptv.app.theme.Palette.background) { content() }
            }
        }
        fun capture(dir: String, name: String) {
            java.io.File(dir).mkdirs()
            repeat(20) { scene.render(0).close() }
            scene.render(0).use { image -> image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }

    @Test fun rendersForEvidence(): Unit = runBlocking {
        val dir = System.getenv("VIPTV_RECONFIGURE_CAPTURE_DIR") ?: return@runBlocking
        val addons = (1..4).map(::addon)
        Shot(1280, 800, television = false) {
            org.viptv.app.features.settings.AddonSettings(false, addons, addons.map { it.id }.toSet(), false, null, null,
                {}, { _, _ -> }, {}, managementAvailable = true, configurableAddons = setOf("addon:2"))
        }.use { it.capture(dir, "addons-desktop-1280") }
        Shot(390, 844, television = false) {
            org.viptv.app.features.settings.AddonSettings(false, addons, addons.map { it.id }.toSet(), false, null, null,
                {}, { _, _ -> }, {}, managementAvailable = true, configurableAddons = setOf("addon:2"),
                configureFailure = "addon:2" to org.viptv.app.state.ADDON_CONFIGURE_FAILURE)
        }.use { it.capture(dir, "addons-phone-390") }
        Shot(1920, 1080, television = true) {
            org.viptv.app.features.settings.TvAddonManageDialog(addons[1], true, true, false, 1f, {}, {}, {})
        }.use { it.capture(dir, "manage-tv-1920") }
        Shot(1920, 1080, television = true) {
            org.viptv.app.features.settings.TvAddonConfigureDialog(
                org.viptv.app.state.AddonConfigureRequest("addon:2", "Fixture 2", "https://watch.example/api/addons/configure/ticket-1", 300), 1f, {}, {})
        }.use { it.capture(dir, "qr-tv-1920") }
    }
}
