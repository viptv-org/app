package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.viptv.app.components.SecureWindow
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
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val profile = Profile("1", "Viewer", null)
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun scene(content: @Composable () -> Unit, width: Int = 390, height: Int = 844) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { Box(Modifier.fillMaxSize()) { content() } } } }

    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close() }
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

    private fun assertActivitySecure(name: String, content: @Composable () -> Unit) {
        SecureWindowRecorder.reset()
        val scene = scene(content)
        try {
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.activity), "$name must flag the activity window")
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "$name must not flag a dialog window")
            assertFalse(SecureWindowRecorder.everUsed(SecureWindowRecorder.dialog), "$name must not use a dialog window")
        } finally {
            scene.close()
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity), "$name must clear the flag when it leaves")
        }
    }

    private fun assertDialogSecure(name: String, content: @Composable () -> Unit) {
        SecureWindowRecorder.reset()
        val scene = scene(content)
        try {
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "$name must flag its own dialog window")
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity), "$name must not flag the activity window behind it")
        } finally {
            scene.close()
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "$name must clear the flag when it leaves")
        }
    }

    @Test fun homeNeverFlagsAnyWindow() {
        SecureWindowRecorder.reset()
        val scene = scene({ HomeScreen(home = null, television = false, open = {}, retry = {}) }, width = 1280, height = 800)
        try {
            frames(scene)
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity), "Home must not flag the activity window")
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "Home must not flag a dialog window")
            assertFalse(SecureWindowRecorder.everUsed(SecureWindowRecorder.activity), "Home must not invoke the hook")
            assertFalse(SecureWindowRecorder.everUsed(SecureWindowRecorder.dialog), "Home must not invoke the hook")
        } finally { scene.close() }
    }

    @Test fun activityCredentialSurfacesFlagTheActivityWindow() {
        assertActivitySecure("profile PIN (phone)") {
            ProfilePinScreen(profile, compact = true, busy = false, error = null, submit = {}, back = {})
        }
        assertActivitySecure("profile PIN (desktop)") {
            ProfilePinScreen(profile, compact = false, busy = false, error = null, submit = {}, back = {})
        }
        assertActivitySecure("sign in") {
            ConnectScreen(browser = true, origin = "https://watch.example", busy = false, error = null, local = null,
                connect = { _, _, _ -> }, register = { _, _, _ -> }, recover = { _, _, _ -> })
        }
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        try {
            assertActivitySecure("change password") { ChangePasswordScreen(remote, {}, {}) }
        } finally { remote.close() }
    }

    @Test fun pinDialogsFlagTheirOwnWindow() {
        assertDialogSecure("parent PIN") {
            ParentPinDialog("unlock", configured = true, busy = false, failure = null, dismiss = {}, submit = { _, _ -> })
        }
        assertDialogSecure("leave PIN") {
            LeavePinDialog(profile.name, configured = true, busy = false, failure = null, dismiss = {}, submit = { _, _ -> })
        }
    }

    @Test fun xtreamCredentialFormFlagsTheDialogWindow() {
        SecureWindowRecorder.reset()
        val scene = scene({
            XtreamConnectionsSettings(connections = emptyList(), defaultLive = null, busy = false, error = null,
                notice = null, canManage = true, manageNote = null, compact = false, back = {},
                request = {}, create = { _, _, _, _, _ -> }, update = { _, _, _ -> }, replacePassword = { _, _ -> },
                sync = {}, setDefault = {}, remove = {})
        }, width = 1280, height = 800)
        try {
            frames(scene)
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "the connection list itself is not a credential surface")
            click(scene, "Add an Xtream connection")
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "the desktop form is a dialog and flags its own window")
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity), "the form flags only its own dialog window")
        } finally { scene.close() }
    }

    @Test fun aDialogClosingOverASecureScreenKeepsTheScreenFlag() {
        SecureWindowRecorder.reset()
        var showDialog by mutableStateOf(true)
        val scene = scene({
            ProfilePinScreen(profile, compact = true, busy = false, error = null, submit = {}, back = {})
            if (showDialog) ParentPinDialog("unlock", configured = true, busy = false, failure = null, dismiss = {}, submit = { _, _ -> })
        })
        try {
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.activity), "the screen under the dialog keeps its activity flag")
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "the dialog flags its own window")
            showDialog = false
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.activity), "closing the dialog must not clear the screen's activity flag")
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.dialog), "the closed dialog clears its own flag")
        } finally {
            scene.close()
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity))
        }
    }

    @Test fun aWindowStaysSecureUntilTheLastHolderLeaves() {
        SecureWindowRecorder.reset()
        var first by mutableStateOf(true)
        var second by mutableStateOf(true)
        val scene = scene({
            if (first) SecureWindow()
            if (second) SecureWindow()
        })
        try {
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.activity), "two holders flag the window once")
            first = false
            frames(scene)
            assertEquals(1, SecureWindowRecorder.active(SecureWindowRecorder.activity), "one remaining holder keeps the flag")
            second = false
            frames(scene)
            assertEquals(0, SecureWindowRecorder.active(SecureWindowRecorder.activity), "the last holder clears the flag")
        } finally { scene.close() }
    }
}