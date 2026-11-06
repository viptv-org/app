package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

private object NoProfileSession : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

@OptIn(ExperimentalComposeUiApi::class)
class ProfilePageRetryTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val failure = "Could not reach your server. Check the connection and try again."

    private fun client(handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handle)) { configureTransport() }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun labelled(node: SemanticsNode, value: String) =
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true

    private inner class Harness : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoProfileSession)
        var profileReads = 0
        var offline = true
        private val remote = RemoteConnection("https://watch.example", NoProfileSession, client { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/profiles" && offline -> respond("{}", HttpStatusCode.InternalServerError, jsonHeaders)
                path == "/api/profiles" -> {
                    profileReads++
                    respond(Json.encodeToString(parentFixture()), headers = jsonHeaders)
                }
                path.endsWith("/preferences") -> respond("{}", headers = jsonHeaders)
                path.endsWith("/approvals") -> respond("{\"items\":[]}", headers = jsonHeaders)
                path.endsWith("/pin") -> respond("{\"availability\":\"Unsupported\",\"configured\":false}", headers = jsonHeaders)
                else -> respond(Json.encodeToString(parentKids(true, 10)), headers = jsonHeaders)
            }
        }, browserMode = false)
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme { SettingsRoute(state, controller, browser = false, television = false, compact = true) }
        }
        private var frame = 0L

        init {
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2",
                settingsPage = SettingsPage("Profile", subpage = true), profiles = parentFixture().cards(),
                profileManagement = parentFixture(), navBack = listOf(BackEntry(Screen.Home)))
        }

        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun render() = repeat(12) { scene.render(frame++ * 16_666_667L).close() }
        fun await(what: String, condition: () -> Boolean) {
            frame = scene.renderUntil(from = frame, what = what, condition = condition)
        }
        fun texts() = nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("|") { text -> text.text } }
        fun shows(value: String) = nodes().any { labelled(it, value) }
        fun click(value: String) {
            val node = nodes().firstOrNull { labelled(it, value) && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value' in ${nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text } }}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); render()
        }
        override fun close() { scene.close(); scope.cancel(); remote.close() }
    }

    @Test fun aFailedProfileLoadOffersARetryThatReadsThePageAgain() = Harness().use { ui ->
        ui.await("the failed load") { ui.controller.state.value.error != null }
        val message = requireNotNull(ui.controller.state.value.error)
        ui.render()
        assertTrue(ui.shows(message), "the page states the failure; it shows ${ui.texts()}")
        assertTrue(ui.shows("Retry"), "the page offers no retry")
        ui.offline = false
        ui.click("Retry")
        ui.await("the page after Retry") { ui.controller.state.value.error == null }
        assertEquals(1, ui.profileReads)
        assertFalse(ui.shows(message))
        assertTrue(ui.shows("Name"), "the page keeps its rows")
    }

    @Test fun theTvProfilePageOffersARetryAfterAFailedLoad() {
        var retries = 0
        val state = ApplicationState(screen = Screen.Settings, selectedProfile = "2", profiles = parentFixture().cards(),
            profileManagement = parentFixture())
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvProfileSettingsPage(profilePageValues(state), false, failure, 0L, retry = { retries++ },
                    ProfilePageActions(), 1f, FocusRequester(), FocusRequester()) { }
            } }
        }
        try {
            repeat(12) { scene.render(it * 16_666_667L).close() }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            assertTrue(nodes.any { labelled(it, failure) }, "the page states the failure")
            val retry = nodes.firstOrNull { labelled(it, "Retry") && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("the TV page offers no retry")
            retry.config[SemanticsActions.OnClick].action!!.invoke()
        } finally { scene.close() }
        assertEquals(1, retries)
    }
}