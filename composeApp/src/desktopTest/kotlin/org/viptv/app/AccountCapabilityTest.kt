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

    private object NoSavedSession : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private class Server(private val capabilities: String?, private val role: String, browser: Boolean) : AutoCloseable {
        val paths = mutableListOf<String>()
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            val body = when (path) {
                "/api/auth/me" -> """{"account":{"id":"1","username":"viewer","name":"Viewer","role":"$role"}""" +
                    (capabilities?.let { ""","capabilities":$it""" } ?: "") + "}"
                "/api/account/devices" -> accountFixture
                else -> "{}"
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = browser)
        override fun close() = remote.close()
    }

    private class Host(capabilities: String?, role: String = "owner") : AutoCloseable {
        val server = Server(capabilities, role, browser = false)
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

        fun render(times: Int = 12) = repeat(times) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

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

    @Test fun anAccountSessionShowsBothRowsAndLoadsTheDeviceListOnce() {
        Host("""{"manage_account":true}""").use { ui ->
            ui.render()
            ui.waitFor { ui.controller.state.value.manageAccount }
            ui.render()
            assertTrue(ui.shows("Devices & sessions"), "A capable session offers the devices row")
            assertTrue(ui.shows("Household PIN"), "An owner keeps the household PIN row")
            ui.click("Devices & sessions")
            ui.waitFor { ui.server.paths.count { it == "/api/account/devices" } > 0 }
            ui.render(24)
            assertEquals(1, ui.server.paths.count { it == "/api/account/devices" }, "The page loads the list once")
            assertTrue(ui.shows("Living room TV"))
        }
    }

    @Test fun aSessionWithoutTheCapabilityKeepsBothRowsAway() {
        Host("""{"manage_account":false}""").use { ui ->
            ui.render()
            ui.waitFor { ui.server.paths.contains("/api/auth/me") }
            ui.render()
            assertFalse(ui.shows("Devices & sessions"), "A paired television cannot manage the account")
            assertFalse(ui.shows("Household PIN"))
            assertFalse(ui.server.paths.contains("/api/account/devices"))
        }
    }

    @Test fun aServerWithoutTheFieldFallsBackToTheNativeBehaviour() {
        Host(null).use { ui ->
            ui.render()
            ui.waitFor { ui.server.paths.contains("/api/auth/me") }
            ui.render()
            assertFalse(ui.shows("Devices & sessions"), "An older server left native clients unable to manage the account")
            assertFalse(ui.shows("Household PIN"))
        }
    }

    @Test fun aMemberReachesDevicesButKeepsThePinRowOwnersOnly() {
        Host("""{"manage_account":true}""", role = "member").use { ui ->
            ui.render()
            ui.waitFor { ui.controller.state.value.manageAccount }
            ui.render()
            assertTrue(ui.shows("Devices & sessions"), "A member still reads the devices list")
            assertFalse(ui.shows("Household PIN"), "Only the owner changes the household PIN")
        }
    }

    @Test fun theCapabilityComesFromTheServerAndFallsBackToTheBrowserRule(): Unit = runBlocking {
        Server("""{"manage_account":true}""", "owner", browser = false).use { server ->
            server.remote.accountIdentity()
            assertTrue(server.remote.accountManagementEnabled, "A native account session reports the capability")
            assertTrue(server.remote.accountOwner)
        }
        Server(null, "member", browser = true).use { server ->
            server.remote.accountIdentity()
            assertTrue(server.remote.accountManagementEnabled, "An older browser server keeps the browser rule")
            assertFalse(server.remote.accountOwner)
        }
        Server("""{"manage_account":false}""", "owner", browser = true).use { server ->
            server.remote.accountIdentity()
            assertFalse(server.remote.accountManagementEnabled, "The server's answer wins over the platform")
        }
    }

    @Test fun aPairedTelevisionSessionCannotDispatchDeviceManagement(): Unit = runBlocking {
        Server("""{"manage_account":false}""", "owner", browser = false).use { server ->
            server.remote.accountIdentity()
            assertFailsWith<IllegalStateException> { server.remote.accountDevices() }
            assertFailsWith<IllegalStateException> { server.remote.signOutAllDevices() }
            Unit
        }
    }
}
