package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
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
import org.junit.Rule
import org.viptv.app.components.SecureWindowRecorder
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.features.entry.ProfilePinScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.settings.ChangePasswordScreen
import org.viptv.app.features.settings.LeavePinDialog
import org.viptv.app.features.settings.ParentPinDialog
import org.viptv.app.features.settings.XtreamConnectionsSettings
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SecureWindowTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("1", "Viewer", null)
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun scene(content: @Composable () -> Unit, width: Int = 390, height: Int = 844) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { Box(Modifier.fillMaxSize()) { content() } } } }

    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun click(scene: ImageComposeScene, label: String) {
        val node = assertNotNull(nodes(scene).lastOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true &&
                it.config.contains(SemanticsActions.OnClick)
        }, "no clickable '$label'")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene)
    }

    private fun assertSecure(name: String, content: @Composable () -> Unit) {
        SecureWindowRecorder.reset()
        val scene = scene(content)
        try {
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active, "$name must flag the window while it is on screen")
            assertEquals(1, SecureWindowRecorder.calls, "$name must invoke the secure-window hook")
        } finally {
            scene.close()
            assertEquals(0, SecureWindowRecorder.active, "$name must clear the flag when it leaves")
        }
    }

    @Test fun homeNeverFlagsTheWindow() {
        SecureWindowRecorder.reset()
        val scene = scene({ HomeScreen(home = null, television = false, open = {}, retry = {}) }, width = 1280, height = 800)
        try {
            frames(scene)
            assertEquals(0, SecureWindowRecorder.active, "Home must not flag the window")
            assertEquals(0, SecureWindowRecorder.calls, "Home must not invoke the secure-window hook")
        } finally { scene.close() }
    }

    @Test fun pinSurfacesFlagTheWindow() {
        assertSecure("profile PIN (phone)") {
            ProfilePinScreen(profile, compact = true, busy = false, error = null, submit = {}, back = {})
        }
        assertSecure("profile PIN (desktop)") {
            ProfilePinScreen(profile, compact = false, busy = false, error = null, submit = {}, back = {})
        }
        assertSecure("parent PIN") {
            ParentPinDialog("unlock", configured = true, busy = false, failure = null, dismiss = {}, submit = { _, _ -> })
        }
        assertSecure("leave PIN") {
            LeavePinDialog(profile.name, configured = true, busy = false, failure = null, dismiss = {}, submit = { _, _ -> })
        }
    }

    @Test fun signInFlagsTheWindow() {
        assertSecure("sign in") {
            ConnectScreen(browser = true, origin = "https://watch.example", busy = false, error = null, local = null,
                connect = { _, _, _ -> }, register = { _, _, _ -> }, recover = { _, _, _ -> })
        }
    }

    @Test fun changePasswordFlagsTheWindow() {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        try {
            assertSecure("change password") { ChangePasswordScreen(remote, {}, {}) }
        } finally { remote.close() }
    }

    @Test fun xtreamCredentialFormFlagsTheWindow() {
        SecureWindowRecorder.reset()
        val scene = scene({
            XtreamConnectionsSettings(connections = emptyList(), defaultLive = null, busy = false, error = null,
                notice = null, canManage = true, manageNote = null, compact = false, back = {},
                request = {}, create = { _, _, _, _, _ -> }, update = { _, _, _ -> }, replacePassword = { _, _ -> },
                sync = {}, setDefault = {}, remove = {})
        }, width = 1280, height = 800)
        try {
            frames(scene)
            assertEquals(0, SecureWindowRecorder.active, "the connection list itself is not a credential surface")
            click(scene, "Add an Xtream connection")
            assertEquals(1, SecureWindowRecorder.active, "opening the credential form flags the window")
        } finally { scene.close() }
    }
}