package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import kotlin.test.*

/**
 * app#102: a pairing the account holder declines ends the wait on the paired screen within one
 * poll, with "New code" still there to start again. The fake server answers the documented OAuth
 * device-flow errors (`authorization_pending`, then `access_denied`). With
 * VIPTV_PAIRING_DENIAL_CAPTURE_DIR set it writes the PR screenshots for TV and phone.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DevicePairingDenialUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private class FakeServer {
        val codes = AtomicInteger(); val polls = AtomicInteger(); val pollsAfterDenial = AtomicInteger()
        val denied = AtomicBoolean()
        fun connection(origin: String, store: RemoteSessionStore) = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/code" -> {
                    denied.set(false)
                    val code = "ABCD-%04d".format(codes.incrementAndGet())
                    """{"device_code":"secret-$code","user_code":"$code","verification_uri":"https://watch.example/device","verification_uri_complete":"https://watch.example/device?code=$code","expires_in":600,"interval":1}""" to HttpStatusCode.OK
                }
                "/api/auth/device/token" -> {
                    polls.incrementAndGet()
                    if (denied.get()) { pollsAfterDenial.incrementAndGet(); """{"error":"access_denied"}""" to HttpStatusCode.BadRequest }
                    else """{"error":"authorization_pending"}""" to HttpStatusCode.BadRequest
                }
                else -> error("Unexpected pairing route ${request.url.encodedPath}")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
    }

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun tvShowsTheDeclinedStateWithNewCodeFocused() = declined(television = true, width = 1920, height = 1080, name = "tv",
        open = listOf("Sign in", "Use your phone"), waiting = "Waiting for you to sign in…")

    @Test fun phoneShowsTheDeclinedStateWithANewCodeAction() = declined(television = false, width = 390, height = 844, name = "phone",
        open = listOf("Use another device"), waiting = "Waiting for approval…")

    private fun declined(television: Boolean, width: Int, height: Int, name: String, open: List<String>, waiting: String) {
        val server = FakeServer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = ApplicationController(scope, store, remoteFactory = server::connection)
        controller.mutable.value = ApplicationState(screen = Screen.Connect)
        val scene = ImageComposeScene(width, height, Density(1f)) {
            App(television = television, providedController = controller, manageControllerLifecycle = false,
                defaultServer = "https://watch.example")
        }
        val probe = Probe(scene)
        try {
            probe.frames()
            open.forEach(probe::click)
            probe.await("the first code") { probe.has("ABCD-0001") && probe.has(waiting) }
            probe.await("a pending poll") { server.polls.get() >= 1 }
            assertFalse(probe.has(DECLINED), "authorization_pending must never read as a denial")

            server.denied.set(true)
            probe.await("the declined state") { probe.has(DECLINED) }
            assertEquals(1, server.pollsAfterDenial.get(), "the first poll after the denial ends the wait")
            assertFalse(probe.has(waiting), "the screen still says it is waiting")
            assertFalse(probe.has("ABCD-0001"), "the declined code is still offered")
            if (television) assertTrue(probe.focused("New code"), "New code must hold focus on the TV")
            probe.capture(name)
            Thread.sleep(2_500); probe.frames()
            assertEquals(1, server.pollsAfterDenial.get(), "polling must stop after a denial")

            probe.click("New code")
            probe.await("a fresh code") { probe.has("ABCD-0002") && probe.has(waiting) }
            assertFalse(probe.has(DECLINED), "a new code clears the declined state")
            assertEquals(2, server.codes.get())
        } finally {
            controller.cancelDevicePairing(); scene.close(); scope.cancel()
        }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun label(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun has(value: String) = nodes().any { node -> label(node).any { value in it } }
        private fun action(value: String) = nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) &&
            flatten(it).any { child -> label(child).any { text -> text == value } } }
        fun focused(value: String) = action(value)?.config?.getOrNull(SemanticsProperties.Focused) == true
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\"")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); frames(20)
        }
        fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for $what")
                frames(6); Thread.sleep(50)
            }
            frames()
        }
        fun capture(variant: String) {
            val dir = System.getenv("VIPTV_PAIRING_DENIAL_CAPTURE_DIR") ?: return
            File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) } }
        }
    }

    private companion object { const val DECLINED = "Sign-in was declined" }
}
