package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import org.junit.Rule
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.entry.DeviceApprovalPage
import org.viptv.app.state.DeviceApprovalController
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DeviceApprovalPageTest {
    @get:Rule val uiThread = UiThreadRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val pending = """{"user_code":"00428701","device":{"device_name":"Living room TV","status":"pending"},"expires_at":"1900000000"}"""

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun engine(onApprove: () -> Unit = {}, onDeny: () -> Unit = {}): MockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/api/auth/status" -> json("""{"authenticated":true}""")
            "/api/auth/me" -> json("""{"account":{"username":"approver"}}""")
            "/api/auth/device/lookup" -> json(pending)
            "/api/auth/device/approve" -> { onApprove(); json("""{"approved":true}""") }
            "/api/auth/device/deny" -> { onDeny(); json("""{"denied":true}""") }
            else -> error("unexpected ${request.url.encodedPath}")
        }
    }

    @Composable
    private fun Fixture(onApprove: () -> Unit = {}, onDeny: () -> Unit = {}) {
        val scope = rememberCoroutineScope()
        val controller = remember {
            DeviceApprovalController(scope, connectionFactory = {
                RemoteConnection("https://watch.example", store,
                    HttpClient(engine(onApprove, onDeny)) { configureTransport() }, browserMode = true)
            })
        }
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            DeviceApprovalPage(compact = true, controller = controller, initialCode = "00428701")
        } }
    }

    @Test fun deniedOffersAddAnotherDeviceAndReturnsToCodeEntry() {
        val scene = ImageComposeScene(390, 844, Density(1f)) { Fixture() }
        val probe = Probe(scene)
        try {
            probe.await("the code step") { probe.has("Enter the code on your TV") }
            probe.click("Continue")
            probe.await("the confirmation") { probe.has("Sign in to this TV?") }
            probe.click("Deny")
            probe.await("the denial") { probe.has("That device will not be signed in.") }
            assertTrue(probe.has("Add another device"), probe.texts().toString())
            assertFalse(probe.has("Back to home"), probe.texts().toString())
            probe.click("Add another device")
            probe.await("the code step again") { probe.has("Enter the code on your TV") }
        } finally { scene.close() }
    }

    @Test fun doneReadsDeviceSignedInAndOffersAddAnotherDevice() {
        val scene = ImageComposeScene(390, 844, Density(1f)) { Fixture() }
        val probe = Probe(scene)
        try {
            probe.await("the code step") { probe.has("Enter the code on your TV") }
            probe.click("Continue")
            probe.await("the confirmation") { probe.has("Sign in to this TV?") }
            probe.click("Sign in the TV")
            probe.await("the done step") { probe.has("It can now pick its profile.") }
            assertTrue(probe.has("Device signed in"), probe.texts().toString())
            assertTrue(probe.has("Add another device"), probe.texts().toString())
            assertFalse(probe.has("Back to home"), probe.texts().toString())
        } finally { scene.close() }
    }

    private class Probe(private val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 6) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun label(node: SemanticsNode): String =
            (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        private fun subtree(node: SemanticsNode): String {
            val own = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val children = node.children.flatMap { subtree(it).split(" ") }
            return (own + children).filter { it.isNotBlank() }.joinToString(" ")
        }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        fun has(value: String) = value in texts()
        fun click(value: String) {
            val target = nodes().firstOrNull { node ->
                node.config.contains(SemanticsActions.OnClick) && subtree(node).contains(value)
            } ?: fail("no clickable \"$value\" — ${texts()}")
            assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                assertTrue(System.nanoTime() < deadline, "timed out waiting for $what: ${texts()}")
                frames(); Thread.sleep(20)
            }
            frames()
        }
    }
}