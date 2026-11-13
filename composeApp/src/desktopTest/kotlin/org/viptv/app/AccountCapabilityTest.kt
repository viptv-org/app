package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountCapabilityTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private object NoSavedSession : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private class Server(private val capabilities: String?, private val accountRole: String,
        private val sessionRole: String, browser: Boolean,
        private val devices: Pair<HttpStatusCode, String> = HttpStatusCode.OK to accountFixture) : AutoCloseable {
        val paths = EngineRequests<String>()
        private val json = headersOf(HttpHeaders.ContentType, "application/json")
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            when (path) {
                "/api/auth/me" -> respond("""{"account":{"id":"1","username":"viewer","name":"Viewer","role":"$accountRole"},"role":"$sessionRole"""" +
                    (capabilities?.let { ""","capabilities":$it""" } ?: "") + "}", headers = json)
                "/api/account/devices" -> respond(devices.second, devices.first, headers = json)
                "/api/profiles/1/integrations" -> respond(ownedAddonConfiguration, headers = json)
                "/api/addons" -> respond("""{"items":[{"id":1,"name":"Owned add-on","enabled":true}],"next_cursor":null}""", headers = json)
                else -> respond("{}", headers = json)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = browser)
        override fun close() = remote.close()
    }

    private class Host(capabilities: String?, accountRole: String = "owner", sessionRole: String = accountRole,
        devices: Pair<HttpStatusCode, String> = HttpStatusCode.OK to accountFixture, initialSection: String? = null) : AutoCloseable {
        val server = Server(capabilities, accountRole, sessionRole, browser = false, devices)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also {
            it.connection = server.remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", settingsEntrySection = initialSection)
        }
        private var frame = 0L
        private val scene = ImageComposeScene(390, 1600, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme { SettingsRoute(state, controller, browser = false, television = false, compact = true) }
        }

        fun render(times: Int = 12) = repeat(times) { scene.render(frame++ * 16_666_667L).close() }

        fun waitFor(condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 5_000
            while (!condition()) {
                if (System.currentTimeMillis() > deadline) fail("timed out waiting for ${server.paths.joinToString()}")
                render(2)
            }
        }

        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun shows(value: String) = nodes().any { holds(it, value) }
        fun click(value: String) {
            val node = nodes().firstOrNull { holds(it, value) && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value'")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); render()
        }

        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun holds(node: SemanticsNode, value: String) =
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true

        override fun close() { scene.close(); scope.cancel(); server.close() }
    }

    @Test fun anOwnerShowsOwnerRowsAndLoadsTheDeviceListOnce() {
        Host("""{"manage_account":true}""", accountRole = "owner", sessionRole = "owner").use { ui ->
            ui.render()
            ui.waitFor { ui.controller.state.value.manageAccount }
            ui.render()
            assertTrue(ui.shows("Devices & sessions"), "An owner reads the devices list")
            assertTrue(ui.shows("Household PIN"), "An owner keeps the household PIN row")
            assertTrue(ui.shows("Delete account"), "An owner keeps the self-service delete row")
            ui.click("Devices & sessions")
            ui.waitFor { ui.server.paths.count { it == "/api/account/devices" } > 0 }
            ui.render(24)
            assertEquals(1, ui.server.paths.count { it == "/api/account/devices" }, "The page loads the list once")
            assertTrue(ui.shows("Living room TV"))
            assertTrue(ui.shows("Sign out"), "An owner can sign devices out")
        }
    }

    @Test fun aMemberReadsDevicesAndSelfServiceRowsWithoutOwnerWrites() {
        Host("""{"manage_account":false}""", accountRole = "member", sessionRole = "member").use { ui ->
            ui.render()
            ui.waitFor { ui.shows("Devices & sessions") }
            assertTrue(ui.shows("Devices & sessions"), "A member still reads the devices list")
            assertTrue(ui.shows("Household PIN"), "A member reads the household PIN status")
            assertTrue(ui.shows("Delete account"), "Any account session keeps the self-service delete row")
            ui.click("Devices & sessions")
            ui.render(24)
            assertTrue(ui.shows("Living room TV"))
            assertFalse(ui.shows("Sign out"), "A member has no device sign-out action")
            assertFalse(ui.shows("Sign out of all devices"))
        }
    }

    @Test fun anExplicitMemberIntegrationCapabilityOffersOwnAddonsWithoutAccountAdministration() {
        Host("""{"manage_account":false,"manage_integrations":true}""", accountRole = "member", initialSection = "Add-ons").use { ui ->
            ui.waitFor { ui.controller.state.value.accountIdentity != null && ui.shows("Owned add-on") && !ui.controller.state.value.busy }
            assertTrue(ui.shows("Owned add-on"))
            assertTrue(ui.shows("Add an add-on"))
            assertFalse(ui.shows("Add-on management is not available on this connection."))
            assertFalse(ui.controller.state.value.manageAccount)
            assertFalse(ui.controller.state.value.accountOwner)
        }
    }

    @Test fun aReadOnlyMemberStillReadsOwnedAddonInventory() {
        Host("""{"manage_account":false,"manage_integrations":false}""", accountRole = "member", initialSection = "Add-ons").use { ui ->
            ui.waitFor { ui.controller.state.value.accountIdentity != null && ui.shows("Owned add-on") && !ui.controller.state.value.busy }
            assertTrue(ui.shows("Owned add-on"))
            assertFalse(ui.shows("No add-ons installed."))
            assertFalse(ui.shows("Add an add-on"))
            assertFalse(ui.shows("Remove"))
        }
    }

    @Test fun ownIntegrationAuthorityIsExplicitAndDoesNotGrantGlobalAdministration(): Unit = runBlocking {
        Server("""{"manage_account":false,"manage_integrations":true}""", "member", "member", browser = false).use { server ->
            val access = server.remote.accountAccess()
            assertTrue(access.managesSources)
            assertFalse(access.manageAccount)
        }
        Server("""{"manage_account":true,"manage_integrations":false}""", "owner", "owner", browser = false).use { server ->
            assertFalse(server.remote.accountAccess().managesSources)
        }
        Server("""{"manage_account":true}""", "owner", "owner", browser = false).use { server ->
            assertTrue(server.remote.accountAccess().managesSources)
        }
        Server("""{"manage_account":true}""", "member", "member", browser = false).use { server ->
            assertFalse(server.remote.accountAccess().managesSources)
        }
        Server("""{"manage_account":true,"manage_integrations":true}""", "owner", "device", browser = false).use { server ->
            assertFalse(server.remote.accountAccess().managesSources)
        }
    }

    @Test fun aBrowserMemberCanRefreshIdentityWithoutGlobalManagement(): Unit = runBlocking {
        Server("""{"manage_account":false,"manage_integrations":true}""", "member", "member", browser = true).use { server ->
            server.remote.accountIdentity()
            server.remote.accountIdentity()
            assertEquals(2, server.paths.count { it == "/api/auth/me" })
            assertFalse(server.remote.accountManagementEnabled)
        }
    }

    @Test fun aProfileSwitchRejectsAnOlderInFlightIntegrationAuthorityRead(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/me" -> {
                    entered.complete(Unit); release.await()
                    """{"account":{"id":"1","username":"owner","name":"Owner","role":"owner"},"role":"owner","csrf_token":"older-profile-csrf","capabilities":{"manage_account":true,"manage_integrations":true}}"""
                }
                "/api/bootstrap" -> """{"identity":{"role":"device","profile_id":"2","profiles":[],"csrf_token":"current-profile-csrf"},"capabilities":{"manage_account":false,"manage_integrations":false}}"""
                "/api/profiles/2/integrations" -> { assertEquals("current-profile-csrf", request.headers["x-csrf-token"]); "{}" }
                else -> "{}"
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        try {
            val read = async { remote.accountIdentity() }
            entered.await(); remote.switchProfile("2"); remote.bootstrap(); release.complete(Unit); read.await()
            assertEquals("device", remote.accountRole)
            assertEquals(false, remote.integrationManagementEnabled)
            remote.call("/api/profiles/2/integrations", HttpMethod.Put)
        } finally { release.complete(Unit); remote.close() }
    }

    @Test fun aBrowserMemberOwnAddonWriteUsesTheFreshIdentityCsrfWithoutGlobalAuthority(): Unit = runBlocking {
        var reads = 0
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/me" -> {
                    reads++
                    """{"account":{"id":"1","username":"member","name":"Member","role":"member"},"role":"member","csrf_token":"csrf-$reads","capabilities":{"manage_account":false,"manage_integrations":true}}"""
                }
                "/api/addons" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("csrf-2", request.headers["x-csrf-token"])
                    """{"id":1}"""
                }
                else -> {
                    if (request.method == HttpMethod.Put) assertEquals("csrf-2", request.headers["x-csrf-token"])
                    ownedAddonConfiguration
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = true)
        try {
            remote.accountIdentity(); remote.accountIdentity()
            remote.installAddon("1", "https://fixture.example/manifest.json")
            assertFalse(remote.accountManagementEnabled)
            assertEquals(true, remote.integrationManagementEnabled)
        } finally { remote.close() }
    }

    @Test fun aPairedTelevisionCannotReadTheDeviceListSoTheRowIsAbsent() {
        Host("""{"manage_account":false}""", accountRole = "owner", sessionRole = "device").use { ui ->
            ui.render()
            ui.waitFor { ui.controller.state.value.accountRole == "device" }
            ui.render()
            assertFalse(ui.shows("Devices & sessions"), "GET /account/devices answers account_session_required to a device session")
            assertFalse(ui.shows("Household PIN"))
            assertFalse(ui.shows("Delete account"), "Only an account session gets the self-service delete row")
            assertFalse(ui.server.paths.contains("/api/account/devices"), "The refused page is never opened")
        }
    }

    @Test fun aRefusedDeviceListSaysWhereToManageDevicesWithoutARetry() {
        Host("""{"manage_account":false}""", accountRole = "member", sessionRole = "member",
            devices = HttpStatusCode.Forbidden to """{"error":"Account session required","error_code":"account_session_required"}""").use { ui ->
            ui.render()
            ui.waitFor { ui.shows("Devices & sessions") }
            ui.click("Devices & sessions")
            ui.waitFor { ui.shows("Manage this from a signed-in phone, computer or the web dashboard.") }
            ui.render()
            assertFalse(ui.shows("Retry"), "A refusal can never succeed")
        }
    }

    @Test fun aServerWithoutTheDeviceListKeepsItsReasonWithoutARetry() {
        Host("""{"manage_account":false}""", accountRole = "member", sessionRole = "member",
            devices = HttpStatusCode.NotFound to "{}").use { ui ->
            ui.render()
            ui.waitFor { ui.shows("Devices & sessions") }
            ui.click("Devices & sessions")
            ui.waitFor { ui.shows("Update the server to manage devices here.") }
            ui.render()
            assertFalse(ui.shows("Retry"), "A server without the route cannot answer a retry")
        }
    }

    @Test fun theCapabilityComesFromTheServerAndFallsBackToTheAccountRole(): Unit = runBlocking {
        Server("""{"manage_account":true}""", "owner", "owner", browser = false).use { server ->
            server.remote.accountIdentity()
            assertTrue(server.remote.accountManagementEnabled, "A native account session reports the capability")
            assertTrue(server.remote.accountOwner)
            assertEquals("owner", server.remote.accountRole)
        }
        Server(null, "member", "member", browser = true).use { server ->
            server.remote.accountIdentity()
            assertTrue(server.remote.accountManagementEnabled, "An older browser server keeps the browser rule")
            assertFalse(server.remote.accountOwner)
            assertEquals("member", server.remote.accountRole)
        }
        Server("""{"manage_account":false}""", "owner", "device", browser = false).use { server ->
            server.remote.accountIdentity()
            assertFalse(server.remote.accountOwner, "A paired television is not the account owner")
            assertEquals("device", server.remote.accountRole)
        }
    }

    @Test fun aServerWithoutTheTopLevelRoleFallsBackToTheAccountRole(): Unit = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath == "/api/auth/me")
                """{"account":{"id":"1","username":"viewer","name":"Viewer","role":"owner"}}"""
            else "{}"
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        try {
            remote.accountIdentity()
            assertTrue(remote.accountOwner, "An older server without the session role falls back to the account role")
            assertEquals("owner", remote.accountRole)
        } finally { remote.close() }
    }

    @Test fun aPairedTelevisionSessionCannotDispatchADeviceSignOut(): Unit = runBlocking {
        Server("""{"manage_account":false}""", "owner", "device", browser = false).use { server ->
            server.remote.accountIdentity()
            assertFalse(server.remote.accountManagementEnabled)
            assertFailsWith<IllegalStateException> { server.remote.signOutAllDevices() }
            Unit
        }
    }
}

private const val ownedAddonConfiguration = """{"configuration":{"scope":{"connection_id":"server","account_id":"1","profile_id":"1","revision":9},"mode":"Server","name":"Profile","browsing_plugin":"stremio","assignments":[]},"connections":[{"id":"addon:1","plugin_id":"stremio","account_id":"1","name":"Owned add-on","enabled":true,"revision":3,"capabilities":["Sources"]}]}"""
