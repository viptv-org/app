package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.addDeviceSection
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.consumeDeviceCode
import org.viptv.app.state.deliverDeviceCode
import org.viptv.app.state.offerDeviceCode
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AddDeviceAccessTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = "https://watch.example"
    private val code = "00428701"

    @Test fun anAccountSessionIsOfferedAddADeviceAndOpensIt() {
        Host(role = "member").use { ui ->
            ui.render()
            assertTrue(ui.shows(addDeviceSection), "An account session keeps the row: ${ui.texts()}")
            ui.click(addDeviceSection)
            ui.render()
            assertTrue(ui.shows("Continue"), "The account session reaches the code page: ${ui.texts()}")
        }
    }

    @Test fun aPairedTelevisionIsOfferedNoAddADeviceRowAndKeepsSignOut() {
        Host(role = "device").use { ui ->
            ui.render()
            assertFalse(ui.shows(addDeviceSection), "A device grant cannot approve a code: ${ui.texts()}")
            assertTrue(ui.shows("Sign out"), "The TV keeps its way out")
            assertFalse(ui.shows("Devices & sessions"), "The other account-session rows stay absent")
            assertFalse(ui.shows("Password"))
            assertFalse(ui.shows("Delete account"))
        }
    }

    @Test fun thePairedTelevisionSectionNeverOpensThePage() {
        Host(role = "device", entrySection = addDeviceSection).use { ui ->
            ui.render()
            assertFalse(ui.shows("Continue"), "No code entry for a device session")
            assertTrue(ui.shows("Sign out"), "The fallback is the settings list, not a blank page: ${ui.texts()}")
        }
    }

    @Test fun aDeviceSignInLinkIsDroppedOnAPairedTelevision() = withController("device") { controller ->
        controller.mutable.value = controller.mutable.value.copy(screen = Screen.Home)
        controller.offerDeviceCode(origin, code)
        controller.deliverDeviceCode()
        assertEquals(Screen.Home, controller.state.value.screen, "A paired TV does not open a page it cannot use")
        assertNull(controller.deviceLink.value, "The link is consumed rather than retried")
    }

    @Test fun aDeviceSignInLinkStillOpensThePageForAnAccountSession() = withController("member") { controller ->
        controller.mutable.value = controller.mutable.value.copy(screen = Screen.Home)
        controller.offerDeviceCode(origin, code)
        controller.deliverDeviceCode()
        assertEquals(Screen.Settings, controller.state.value.screen)
        assertEquals(addDeviceSection, controller.state.value.settingsEntrySection)
        assertNotNull(controller.deviceLink.value?.takeIf { it.delivered })
        controller.consumeDeviceCode()
        assertNull(controller.deviceLink.value)
    }

    @Test fun thePageWithoutTheRowFallsBackToTheList() {
        val scene = ImageComposeScene(390, 1400, Density(1f)) {
            ViptvTheme { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                initialSection = addDeviceSection, addDevice = null) }
        }
        try {
            val probe = Probe(scene)
            probe.frames(12)
            assertTrue(probe.has("Sign out"), "The list draws: ${probe.texts()}")
            assertFalse(probe.has("Continue"), "No code entry without the row")
            assertFalse(probe.has(addDeviceSection))
        } finally { scene.close() }
    }

    @Test fun capturesSettingsForAPairedTelevisionAndForAnAccountSession() {
        val directory = System.getenv("VIPTV_ADD_DEVICE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        Host(role = "device", television = true, width = 1920, height = 1080, entrySection = "Account").use { ui ->
            ui.render(20)
            assertTrue(ui.shows("Sign out"), "The TV account page draws: ${ui.texts()}")
            assertFalse(ui.shows(addDeviceSection), "The TV account page has no approval row")
            ui.capture(File(directory, "settings-tv-device-session.png"))
        }
        Host(role = "device", width = 390, height = 1400).use { ui ->
            ui.render(20)
            assertFalse(ui.shows(addDeviceSection))
            ui.capture(File(directory, "settings-phone-device-session.png"))
        }
        Host(role = "member", width = 390, height = 1400).use { ui ->
            ui.render(20)
            assertTrue(ui.shows(addDeviceSection))
            ui.capture(File(directory, "settings-phone-account-session.png"))
        }
    }

    private fun withController(role: String, body: (ApplicationController) -> Unit) {
        val store = NoSavedSession
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val remote = RemoteConnection(origin, store, client, browserMode = false)
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1", accountRole = role,
                accountIdentity = AccountIdentity("1", "viewer", "Viewer", role))
        }
        try { body(controller) } finally { scope.cancel(); remote.close() }
    }

    private object NoSavedSession : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private class Server(role: String) : AutoCloseable {
        private val client = HttpClient(MockEngine {
            respond("""{"account":{"id":"1","username":"viewer","name":"Viewer","role":"$role"},"role":"$role"}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        override fun close() = remote.close()
    }

    private inner class Host(role: String, television: Boolean = false, width: Int = 390, height: Int = 1400,
        entrySection: String? = null) : AutoCloseable {
        val server = Server(role)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also {
            it.connection = server.remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", settingsEntrySection = entrySection,
                accountRole = role, accountIdentity = AccountIdentity("1", "viewer", "Viewer", role))
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme(television) { SettingsRoute(state, controller, browser = false, television = television, compact = width < 640) }
        }

        fun render(times: Int = 12) = repeat(times) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun shows(value: String) = value in texts()
        fun click(value: String) {
            val node = nodes().firstOrNull { node -> text(node) == value && node.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value' in ${texts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        fun capture(file: File) = scene.render(40 * 16_666_667L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        override fun close() { scene.close(); scope.cancel(); server.close() }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun has(value: String) = value in texts()
    }
}