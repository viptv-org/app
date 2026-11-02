package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProfileHomeSkeletonTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun scope(profile: String) = Scope("local", "device", profile, 1)
    private fun card(title: String) = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl", title = title, badges = emptyList())
    private fun home(profile: String, title: String) = HomeSnapshot(scope(profile), 1,
        rows = listOf(HomeRow(id = "saved", title = "Your titles", provider_id = "simkl", state = RowState.READY, cards = listOf(card(title)))), complete = true)

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun bootstrapBody(profile: String, snapshot: HomeSnapshot) = buildJsonObject {
        put("identity", buildJsonObject {
            put("profiles", buildJsonArray {
                add(buildJsonObject { put("id", "1"); put("name", "One") })
                add(buildJsonObject { put("id", "2"); put("name", "Two") })
            })
            put("profile_id", profile)
        })
        put("home", Json.encodeToJsonElement(HomeSnapshot.serializer(), snapshot))
    }.toString()

    @Test fun selectProfileShowsTheSkeletonBeforeBootstrapAndNeverLeaksThePreviousHome() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val bootstrapped = CompletableDeferred<Unit>()
        val body = bootstrapBody("2", home("2", "Profile two title"))
        val client = HttpClient(MockEngine { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/api/auth/profile" -> respond("{}", headers = headers)
                "/api/bootstrap" -> { bootstrapped.complete(Unit); gate.await(); respond(body, headers = headers) }
                else -> respond("{}", HttpStatusCode.NotFound, headers = headers)
            }
        }) { configureTransport() }
        val controller = ApplicationController(this, store)
        controller.connection = RemoteConnection("https://watch.example", store, client, browserMode = true)
        controller.mutable.value = ApplicationState(screen = Screen.Profiles,
            profiles = listOf(Profile("1", "One", null), Profile("2", "Two", null)),
            selectedProfile = "1", home = home("1", "Profile one title"))
        try {
            controller.selectProfile("2")
            withTimeout(2000) { bootstrapped.await() }
            val pending = controller.state.value
            assertEquals(Screen.Home, pending.screen, "the profile switch shows Home before the bootstrap returns")
            assertNull(pending.home, "the previous profile's content is dropped when Home appears")
            assertEquals("2", pending.profileToOpen)
            assertTrue(pending.busy, "the skeleton lasts until the bootstrap returns")

            gate.complete(Unit)
            withTimeout(5000) { controller.state.first { !it.busy } }
            assertEquals("2", controller.state.value.selectedProfile)
            assertNull(controller.state.value.profileToOpen)
            assertEquals(home("2", "Profile two title"), controller.state.value.home)
        } finally { gate.complete(Unit); controller.job?.cancelAndJoin(); controller.connection?.close() }
    }

    @Test fun failedSwitchRetriesTheSameProfileFromTheHomeSkeleton() = runBlocking<Unit> {
        var bootstraps = 0
        val body = bootstrapBody("2", home("2", "Profile two title"))
        val client = HttpClient(MockEngine { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/api/auth/profile" -> respond("{}", headers = headers)
                "/api/bootstrap" -> {
                    bootstraps++
                    if (bootstraps == 1) respond("{\"error\":\"boom\"}", HttpStatusCode.InternalServerError, headers = headers)
                    else respond(body, headers = headers)
                }
                else -> respond("{}", HttpStatusCode.NotFound, headers = headers)
            }
        }) { configureTransport() }
        val controller = ApplicationController(this, store)
        controller.connection = RemoteConnection("https://watch.example", store, client, browserMode = true)
        controller.mutable.value = ApplicationState(screen = Screen.Profiles,
            profiles = listOf(Profile("2", "Two", null)), selectedProfile = null)
        try {
            controller.selectProfile("2")
            withTimeout(5000) { controller.state.first { !it.busy } }
            assertEquals(Screen.Home, controller.state.value.screen)
            assertNull(controller.state.value.home)
            assertEquals("2", controller.state.value.profileToOpen)
            assertTrue(controller.state.value.error.orEmpty().isNotEmpty(), "the failed switch lands on Home with an error")

            controller.refresh()
            withTimeout(5000) { controller.state.first { !it.busy && it.home != null } }
            assertEquals("2", controller.state.value.selectedProfile)
            assertEquals(2, bootstraps, "retry re-selects the same profile and re-bootstraps")
            assertNull(controller.state.value.error)
        } finally { controller.job?.cancelAndJoin(); controller.connection?.close() }
    }

    @Test fun phoneShowsAHomeSkeletonWhileLoadingThenSwapsInContent() {
        var snapshot by mutableStateOf<HomeSnapshot?>(null)
        var busy by mutableStateOf(true)
        var frame = 0L
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            HomeScreen(home = snapshot, television = false, open = {}, retry = {}, busy = busy)
        } }
        try {
            repeat(18) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue(scene.hasLoadingHome(), "the phone Home renders a skeleton on its first frames")
            assertTrue(scene.texts().none { it == "Profile one title" || it == "Profile two title" })

            busy = false; snapshot = home("2", "Profile two title")
            repeat(24) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue(scene.texts().contains("Profile two title"))
            assertFalse(scene.hasLoadingHome(), "content replaces the skeleton")
        } finally { scene.close() }
    }

    @Test fun phoneSkeletonKeepsStaticPlaceholdersUnderReducedMotion() {
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                HomeScreen(home = null, television = false, open = {}, retry = {}, busy = true)
            }
        } }
        try {
            var frame = 0L
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue(scene.hasLoadingHome(), "reduced motion keeps the static placeholders")
        } finally { scene.close() }
    }

    private fun ImageComposeScene.hasLoadingHome() = semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.any { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == "Loading Home" } }
    private fun ImageComposeScene.texts() = semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }.takeIf { it.isNotBlank() } }
}