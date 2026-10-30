package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.refreshPasswordChangeAvailability
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ChangePasswordTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = "https://watch.example"
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val changedFixture = """{"session_id":"session","csrf_token":"next-csrf","expires_in":900}"""
    private val CURRENT = 0
    private val NEW = 1
    private val CONFIRM = 2

    @Test fun theRouteProbeOnlyAsksWhetherTheServerHasTheEndpoint() = runBlocking {
        val methods = mutableListOf<HttpMethod>()
        val absent = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            methods += request.method
            respond("{}", HttpStatusCode.NotFound, json)
        }) { configureTransport() }, browserMode = true)
        val present = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            methods += request.method
            respond("""{"error":"Method not allowed"}""", HttpStatusCode.MethodNotAllowed, json)
        }) { configureTransport() }, browserMode = true)
        try {
            assertFalse(absent.passwordChangeAvailable(), "a 404 means the server predates the route")
            assertTrue(present.passwordChangeAvailable(), "a POST-only route answers 405 to this probe and is there")
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get), methods, "the probe must not offer a password")
        } finally { absent.close(); present.close() }
    }

    @Test fun aServerWithoutTheRouteClearsAPreviouslyOfferedRow() = runBlocking {
        assertEquals(false, probed(HttpStatusCode.NotFound, previouslyOffered = true))
        assertEquals(true, probed(HttpStatusCode.MethodNotAllowed, previouslyOffered = false))
    }

    private suspend fun probed(status: HttpStatusCode, previouslyOffered: Boolean): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val asked = AtomicBoolean(false)
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine {
            asked.set(true)
            respond("{}", status, json)
        }) { configureTransport() }, browserMode = true)
        try {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, passwordChangeAvailable = previouslyOffered)
            controller.refreshPasswordChangeAvailability()
            val deadline = System.nanoTime() + 10_000_000_000L
            while ((!asked.get() || controller.state.value.passwordChangeAvailable == previouslyOffered) && System.nanoTime() < deadline)
                delay(5)
            return controller.state.value.passwordChangeAvailable
        } finally { scope.cancel(); remote.close() }
    }

    @Test fun thePasswordRowIsAbsentWhenTheServerReportsTheRouteMissingAndPresentWhenItDoesNot() {
        assertRow(HttpStatusCode.NotFound, present = false)
        assertRow(HttpStatusCode.MethodNotAllowed, present = true)
    }

    private fun assertRow(status: HttpStatusCode, present: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val asked = AtomicBoolean(false)
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/auth/password") {
                assertEquals(HttpMethod.Get, request.method)
                asked.set(true)
                respond("{}", status, json)
            } else respond("{}", HttpStatusCode.NotFound, json)
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1",
            profiles = listOf(Profile("1", "Viewer", null)))
        val scene = ImageComposeScene(1440, 1200, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SettingsRoute(state, controller, browser = false, television = false, desktop = true)
            } }
        }
        val probe = Probe(scene)
        try {
            probe.await("the screen to ask about the route") { asked.get() }
            probe.frames()
            assertEquals(present, probe.hasPrefix("Password"), "the row follows the route the server has")
            if (present) {
                probe.click("Password")
                assertTrue(probe.has("Change password"), probe.texts().toString())
                probe.click("Cancel")
                probe.frames()
                assertFalse(probe.has("Change password"), "Cancel leaves the page without changing anything")
                assertTrue(probe.hasPrefix("Password"), "the row stays in the Account group")
            }
        } finally { scene.close(); scope.cancel(); remote.close() }
    }

    @Test fun aWrongCurrentPasswordClearsThatFieldAndKeepsTheNewOne() {
        val bodies = mutableListOf<JsonObject>()
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"error_code":"password_incorrect","error":"That password is not correct."}""", HttpStatusCode.BadRequest, json)
        }) { configureTransport() }, browserMode = true)
        val scene = page(remote)
        val probe = Probe(scene)
        try {
            probe.type(CURRENT, "current-password-1")
            probe.type(NEW, "next-password-12")
            probe.type(CONFIRM, "next-password-12")
            probe.click("Change password")
            probe.await("the wrong-current-password line") { probe.has(passwordIncorrectMessage) }
            assertTrue(probe.isDisabled("Change password"), "the cleared current field leaves nothing to submit")
            probe.type(CURRENT, "current-password-2")
            probe.click("Change password")
            assertEquals(2, bodies.size)
            assertEquals("next-password-12", bodies.last()["new_password"]?.jsonPrimitive?.content, "the new password stays typed")
        } finally { scene.close(); remote.close() }
    }

    @Test fun aMismatchedConfirmationNeverReachesTheServer() {
        val calls = AtomicInteger(0)
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine {
            calls.incrementAndGet()
            respond(changedFixture, HttpStatusCode.OK, json)
        }) { configureTransport() }, browserMode = true)
        val scene = page(remote)
        val probe = Probe(scene)
        try {
            probe.type(CURRENT, "current-password-1")
            probe.type(NEW, "next-password-12")
            probe.type(CONFIRM, "next-password-13")
            probe.frames()
            assertTrue(probe.has(passwordMismatchMessage))
            assertTrue(probe.isDisabled("Change password"), "a mismatch disables the action")
            assertEquals(0, calls.get(), "a mismatch must not post")
        } finally { scene.close(); remote.close() }
    }

    @Test fun tooManyAttemptsAsksTheViewerToWait() {
        val limited = Headers.build {
            append(HttpHeaders.ContentType, "application/json")
            append(HttpHeaders.RetryAfter, "60")
        }
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine {
            respond("""{"error_code":"rate_limited"}""", HttpStatusCode.TooManyRequests, limited)
        }) { configureTransport() }, browserMode = true)
        val scene = page(remote)
        val probe = Probe(scene)
        try {
            probe.type(CURRENT, "current-password-1")
            probe.type(NEW, "next-password-12")
            probe.type(CONFIRM, "next-password-12")
            probe.click("Change password")
            probe.await("the rate-limit line") { probe.has(passwordRateLimitedMessage) }
        } finally { scene.close(); remote.close() }
    }

    @Test fun aServerFailureChangesNothingAndSaysSo() {
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            assertEquals("/api/auth/password", request.url.encodedPath)
            respond("{}", HttpStatusCode.InternalServerError, json)
        }) { configureTransport() }, browserMode = true)
        val changed = AtomicInteger(0)
        val back = AtomicInteger(0)
        val scene = page(remote, onChanged = { changed.incrementAndGet() }, onBack = { back.incrementAndGet() })
        val probe = Probe(scene)
        try {
            probe.type(CURRENT, "current-password-1")
            probe.type(NEW, "next-password-12")
            probe.type(CONFIRM, "next-password-12")
            probe.click("Change password")
            probe.await("the failure line") { probe.has(passwordFailedMessage) }
            assertEquals(0, changed.get(), "a failed change never reports success")
            assertEquals(0, back.get(), "a failed change stays on the page")
            assertFalse(probe.isDisabled("Change password"), "the viewer can try again")
        } finally { scene.close(); remote.close() }
    }

    @Test fun aChangedPasswordLeavesThePageAndSendsBothPasswords() {
        val bodies = mutableListOf<JsonObject>()
        val remote = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(changedFixture, HttpStatusCode.OK, json)
        }) { configureTransport() }, browserMode = true)
        var changed = 0
        val scene = page(remote, onChanged = { changed++ })
        val probe = Probe(scene)
        try {
            probe.type(CURRENT, "current-password-1")
            probe.type(NEW, "next-password-12")
            probe.type(CONFIRM, "next-password-12")
            probe.click("Change password")
            probe.await("the page to close") { changed == 1 }
            assertEquals("current-password-1", bodies.single()["current_password"]?.jsonPrimitive?.content)
            assertEquals("next-password-12", bodies.single()["new_password"]?.jsonPrimitive?.content)
        } finally { scene.close(); remote.close() }
    }

    private fun page(remote: RemoteConnection, onChanged: () -> Unit = {}, onBack: () -> Unit = {}) =
        ImageComposeScene(822, 1720, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ChangePasswordScreen(remote, onBack, onChanged)
            } }
        }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        private fun nodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
        private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun texts() = nodes().map(::text)
        fun has(value: String) = value in texts()
        fun hasPrefix(value: String) = nodes().any { text(it).startsWith(value) }
        private fun action(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun isDisabled(value: String) = nodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true && node.config.contains(SemanticsProperties.Disabled)
        }
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\": ${texts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        private fun fields() = nodes().filter { it.config.contains(SemanticsActions.SetText) }
        fun type(index: Int, value: String) {
            val node = assertNotNull(fields().getOrNull(index), "No field $index: ${texts()}")
            node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            frames(4)
            val field = assertNotNull(fields().getOrNull(index))
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)), "cannot type into field $index")
            frames(4)
        }
        fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for $what: ${texts()}")
                frames(6); Thread.sleep(20)
            }
            frames()
        }
    }
}
