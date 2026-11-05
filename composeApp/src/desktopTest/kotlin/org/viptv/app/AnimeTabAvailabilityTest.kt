package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.viptv.app.components.LocalAnimeAvailable
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.TopNavigation
import org.viptv.app.connection.Bootstrap
import org.viptv.app.connection.BrowsingConfiguration
import org.viptv.app.connection.Profile
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.accept
import org.viptv.app.state.animeAvailable
import org.viptv.app.state.profileConfigurationChanged
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.ExecutionMode
import org.viptv.engine.wire.ProfileConfiguration
import org.viptv.engine.wire.Scope
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AnimeTabAvailabilityTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val profile = Profile("12", "Parent", null)

    @Test fun onlyTheSimklProviderHasAnAnimeCatalog() {
        assertTrue(ApplicationState().animeAvailable)
        assertTrue(ApplicationState(browsingConfiguration = browsing("simkl")).animeAvailable)
        assertFalse(ApplicationState(browsingConfiguration = browsing("stremio")).animeAvailable)
    }

    @Test fun thePhoneTabRowDropsAnimeWithoutASimklProvider() {
        val hidden = scene(390, 844, anime = false) { PhoneHeader(Screen.Home, profile) {} }
        try {
            frames(hidden)
            assertEquals(listOf("All", "Movies", "Series"), tabs(hidden))
        } finally { hidden.close() }
        val shown = scene(390, 844, anime = true) { PhoneHeader(Screen.Home, profile) {} }
        try {
            frames(shown)
            assertEquals(listOf("All", "Movies", "Series", "Anime"), tabs(shown))
        } finally { shown.close() }
    }

    @Test fun theTopNavigationDropsAnimeWithoutASimklProvider() {
        val hidden = scene(1440, 900, anime = false) { TopNavigation(Screen.Home, false, {}, profile = null) }
        try {
            frames(hidden)
            assertFalse(nodes(hidden).any { text(it) == "Anime" })
        } finally { hidden.close() }
        val shown = scene(1440, 900, anime = true) { TopNavigation(Screen.Home, false, {}, profile = null) }
        try {
            frames(shown)
            assertTrue(nodes(shown).any { text(it) == "Anime" })
        } finally { shown.close() }
    }

    @Test fun selectingAProfileLoadsItsBrowsingProvider() = runBlocking<Unit> {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/profiles/12/integrations" -> respond(integrations("simkl"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("{}", HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { configureTransport() }
        val controller = ApplicationController(this)
        controller.connection = RemoteConnection("https://watch.example", store(), client, browserMode = false)
        controller.accept(Bootstrap(profiles = listOf(Profile("12", "Parent", null)), selected = "12", home = null, library = emptyList()))
        withTimeout(5_000) { while (controller.state.value.browsingConfiguration == null) delay(10) }
        assertTrue("/api/profiles/12/integrations" in paths)
        assertTrue(controller.state.value.animeAvailable)
        controller.connection?.close()
    }

    @Test fun aProfileChangeLeavesAnAnimeScreenTheProviderCannotServe() = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val controller = fixture.controller()
        controller.local = fixture.local
        controller.mutable.value = ApplicationState(screen = Screen.Anime, localMode = true, selectedProfile = "default")
        try {
            controller.profileConfigurationChanged()
            withTimeout(5_000) { while (controller.state.value.screen != Screen.Home) delay(10) }
            assertEquals(Screen.Home, controller.state.value.screen)
            assertEquals("stremio", controller.state.value.browsingConfiguration?.profile?.browsing_plugin)
            assertFalse(controller.state.value.animeAvailable)
            assertNull(controller.state.value.error)
            assertFalse(controller.state.value.busy)
        } finally { fixture.close(controller) }
    }

    private fun browsing(plugin: String) = BrowsingConfiguration(
        ProfileConfiguration(Scope("server", "1", "12", 1), ExecutionMode.SERVER, "Parent", browsing_plugin = plugin, assignments = emptyList()),
        emptyList())

    private fun integrations(plugin: String) =
        """{"configuration":{"scope":{"connection_id":"server","account_id":"1","profile_id":"12","revision":1},""" +
            """"mode":"Server","name":"Parent","browsing_plugin":"$plugin","assignments":[]},"connections":[]}"""

    private fun store() = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private var frame = 0L
    private fun scene(width: Int, height: Int, anime: Boolean, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalAnimeAvailable provides anime) {
                    Surface(Modifier.fillMaxSize(), color = Palette.background) { Column { content() } }
                }
            }
        }
    }
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun tabs(scene: ImageComposeScene) = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }.map(::text)
}