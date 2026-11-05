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
        val paths = mutableListOf<String>()
        private val json = headersOf(HttpHeaders.ContentType, "application/json")
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            when (path) {
                "/api/auth/me" -> respond("""{"account":{"id":"1","username":"viewer","name":"Viewer","role":"$accountRole"},"role":"$sessionRole"""" +
                    (capabilities?.let { ""","capabilities":$it""" } ?: "") + "}", headers = json)
                "/api/account/devices" -> respond(devices.second, devices.first, headers = json)
                else -> respond("{}", headers = json)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = browser)
        override fun close() = remote.close()
    }

    private class Host(capabilities: String?, accountRole: String = "owner", sessionRole: String = accountRole,
        devices: Pair<HttpStatusCode, String> = HttpStatusCode.OK to accountFixture) : AutoCloseable {
        val server = Server(capabilities, accountRole, sessionRole, browser = false, devices)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also {
            it.connection = server.remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
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
            ui.waitFor { ui.server.paths.contains("/api/auth/me") }
            ui.render()
            assertTrue(ui.shows("Devices & sessions"), "A member still reads the devices list")
            assertFalse(ui.shows("Household PIN"), "Only the owner changes the household PIN")
            assertTrue(ui.shows("Delete account"), "Any account session keeps the self-service delete row")
            ui.click("Devices & sessions")
            ui.render(24)
            assertTrue(ui.shows("Living room TV"))
            assertFalse(ui.shows("Sign out"), "A member has no device sign-out action")
            assertFalse(ui.shows("Sign out of all devices"))
        }
    }

    @Test fun aPairedTelevisionCannotReadTheDeviceListSoTheRowIsAbsent() {
        Host("""{"manage_account":false}""", accountRole = "owner", sessionRole = "device").use { ui ->
            ui.render()
            ui.waitFor { ui.server.paths.contains("/api/auth/me") }
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