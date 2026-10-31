package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AddDeviceScreenTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val pending = """{"user_code":"00428701","device":{"device_name":"Living room TV","status":"pending"},"expires_at":"1900000000"}"""

    @Test fun aSignedInPhoneOpensTheNativeAddDeviceScreenFromAddADevice() {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { error("The screen makes no request until a code is entered") }) { configureTransport() }, browserMode = false)
        val scene = ImageComposeScene(822, 1720, Density(2f)) { Fixture(remote) }
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Add a device")
            assertTrue("Add a device" in probe.texts(), probe.texts().toString())
            assertFalse("Open TV sign-in" in probe.texts(), "No browser hand-off on a signed-in phone")
            assertTrue("Open VIPTV on the other device, choose Sign in, then enter the code it shows." in probe.texts(), probe.texts().toString())
            assertFalse("Scan the code on your TV" in probe.texts(), "Only Android reads the QR with a camera")
            assertTrue(probe.isDisabled("Continue"), "Continue waits for all eight digits")
            System.getenv("VIPTV_ADD_DEVICE_CAPTURE_DIR")?.let { directory ->
                File(directory).mkdirs()
                scene.render(40 * 16_666_667L).use { image -> image.encodeToData()!!.use { File(directory, "add-device-phone.png").writeBytes(it.bytes) } }
            }
        } finally { scene.close(); remote.close() }
    }

    @Test fun localModeShowsTheServerNeededNoteAndChangeConnection() {
        val scene = ImageComposeScene(822, 1720, Density(2f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { AddDeviceScreen(null, compact = true) } }
        }
        val probe = Probe(scene)
        try {
            probe.frames(12)
            assertTrue("TV sign-in uses a server account. Sign in to the same server on both devices to share profiles and progress." in probe.texts(), probe.texts().toString())
            assertTrue("Change connection" in probe.texts(), probe.texts().toString())
            assertFalse("Continue" in probe.texts(), "No code entry in local mode")
        } finally { scene.close() }
    }

    @Test fun aCodeEnteredOnThePhoneLandsOnTheApprovalStep() {
        val asked = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            asked += request.url.encodedPath
            respond(pending, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        val scene = ImageComposeScene(822, 1720, Density(2f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { AddDeviceScreen(remote, compact = true, linkedCode = "00428701") } }
        }
        val probe = Probe(scene)
        try {
            probe.await("the approval step") { probe.has("Sign in this device?") }
            assertTrue(probe.has("Living room TV"))
            assertEquals(listOf("/api/auth/device/lookup"), asked)
        } finally { scene.close(); remote.close() }
    }

    @Composable private fun Fixture(remote: RemoteConnection) {
        ViptvTheme {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                    addDevice = { compact, _ -> AddDeviceScreen(remote, compact) })
            }
        }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun has(value: String) = value in texts()
        private fun action(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun isDisabled(value: String) = nodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true && node.config.contains(SemanticsProperties.Disabled)
        }
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\"")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for $what: ${texts()}")
                frames(6); Thread.sleep(50)
            }
            frames()
        }
    }
}
