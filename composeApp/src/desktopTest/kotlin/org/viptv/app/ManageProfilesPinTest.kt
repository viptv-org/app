package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.entry.ProfileEditScreen
import org.viptv.app.features.settings.HouseholdPinDialog
import org.viptv.app.features.settings.TvHouseholdPinDialog
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.CatalogPage
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ManageProfilesPinTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun thePhoneEditorAsksForTheHouseholdPinBeforeRenaming() {
        Host(compact = true, width = 390, height = 844).use { host ->
            host.waitFor({ host.shows("Edit profile") }, "the editor draws")
            host.click("Name")
            host.waitFor({ host.hasText("Save") }, "the name dialog opens")
            host.type("Renamed")
            host.click("Save")
            host.waitFor({ host.shows("Change this profile’s name") }, "the household PIN step opens")
            assertTrue(host.shows("Enter the household PIN to change this profile."), "the step names the PIN: ${host.texts()}")
            assertTrue(host.writes.isEmpty(), "nothing is written before the PIN: ${host.writes.toList()}")
            host.type("4821")
            host.click("Continue")
            host.waitFor({ host.writes.contains("PATCH /api/profiles/1") }, "the rename lands after the unlock")
            assertEquals(listOf("POST /api/parent/unlock", "PATCH /api/profiles/1"), host.writes.toList())
        }
    }

    @Test fun theDesktopEditorAsksForTheHouseholdPinBeforeRenaming() {
        Host(compact = false, width = 1280, height = 900).use { host ->
            host.waitFor({ host.shows("Edit profile") }, "the editor draws")
            host.type("Renamed")
            host.click("Save name")
            host.waitFor({ host.shows("Change this profile’s name") }, "the household PIN step opens")
            host.type("4821")
            host.click("Continue")
            host.waitFor({ host.writes.contains("PATCH /api/profiles/1") }, "the rename lands after the unlock")
            assertEquals(listOf("POST /api/parent/unlock", "PATCH /api/profiles/1"), host.writes.toList())
        }
    }

    @Test fun theTvEditorShowsTheSamePromptOverItsRows() = runBlocking<Unit> {
        Host(compact = false, width = 1920, height = 1080, television = true).use { host ->
            host.waitFor({ host.shows("Edit profile") }, "the TV editor draws")
            host.controller.renameProfile("Renamed", "")
            host.waitFor({ host.shows("Change this profile’s name") }, "the household PIN step opens over the TV rows")
            host.type("4821")
            host.click("Continue")
            host.waitFor({ host.writes.contains("PATCH /api/profiles/1") }, "the rename lands after the unlock")
        }
    }

    private object NoSavedSession : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private inner class Host(compact: Boolean, width: Int, height: Int, television: Boolean = false) : AutoCloseable {
        private val json = headersOf(HttpHeaders.ContentType, "application/json")
        val writes = EngineRequests<String>()
        private var unlocked = false
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method.value
            if (path == "/api/parent/unlock" || (path.startsWith("/api/profiles") && method != "GET"))
                writes += "$method $path"
            val body = when {
                path == "/api/parent/unlock" -> { unlocked = true; "{}" }
                path == "/api/profiles" && method == "GET" ->
                    Json.encodeToString(parentFixture(kids = true, unlocked = unlocked, restricted = true))
                path == "/api/bootstrap" -> buildJsonObjectBootstrap()
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, json)
        }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.ProfileEdit, selectedProfile = "1", profileToEdit = "1",
                profiles = listOf(Profile("1", "Viewer", null), Profile("2", "Other", null)),
                profileManagement = org.viptv.app.connection.ProfileManagement(
                    listOf(org.viptv.app.connection.ServerProfile("1", "Viewer"), org.viptv.app.connection.ServerProfile("2", "Other", kids = true, max_age = 10)),
                    org.viptv.app.connection.ProfileCapabilities(
                        org.viptv.app.connection.FeatureAvailability("Available"), org.viptv.app.connection.FeatureAvailability("Available"),
                        org.viptv.app.connection.FeatureAvailability("Available"), org.viptv.app.connection.FeatureAvailability("Available"),
                        org.viptv.app.connection.FeatureAvailability("Unsupported"), org.viptv.app.connection.FeatureAvailability("Available")),
                    org.viptv.app.connection.ParentAccess(pin_configured = true, unlocked = false, restricted = true)))
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Box(Modifier.fillMaxSize()) {
                    ProfileEditScreen(state.profiles.firstOrNull { it.id == state.profileToEdit }, state.profiles.size,
                        state.busy, state.error, compact, {}, controller::renameProfile, controller::setProfilePin,
                        controller::setProfileAvatar, controller::deleteProfile, television = television, pinAvailable = false)
                    state.householdPinTitle?.let { title ->
                        if (television) BoxWithConstraints(Modifier.fillMaxSize()) {
                            TvHouseholdPinDialog(title, state.busy, state.error, controller::cancelHouseholdPin,
                                controller::submitHouseholdPin, scale = maxWidth / 1920.dp)
                        } else HouseholdPinDialog(title, state.busy, state.error, controller::cancelHouseholdPin,
                            controller::submitHouseholdPin)
                    }
                }
            } }
        }

        private fun buildJsonObjectBootstrap() = buildJsonObject {
            put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", "1") })
            put("library", Json.encodeToJsonElement(CatalogPage(emptyList(), null, true)))
        }.toString()

        fun render(times: Int = 8) = repeat(times) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(): List<String> = nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        }
        fun shows(value: String) = texts().any { it == value || it.startsWith("$value ") }
        fun hasText(value: String) = nodes().any { it.config.contains(SemanticsActions.SetText) }
        fun waitFor(condition: () -> Boolean, what: String) {
            repeat(400) {
                if (condition()) { render(4); return }
                render(1)
            }
            fail("$what did not happen: ${texts()}")
        }
        fun type(value: String) {
            val field = nodes().lastOrNull { it.config.contains(SemanticsActions.SetText) } ?: fail("no text field in ${texts()}")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))
            render()
        }
        fun click(label: String) {
            val node = nodes().firstOrNull { node ->
                val held = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
                (held == label || held.startsWith("$label ")) && node.config.contains(SemanticsActions.OnClick)
            } ?: fail("no clickable '$label' in ${texts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        override fun close() { scene.close(); scope.cancel(); remote.close() }
    }
}