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
import org.viptv.app.features.settings.AccountIdentityScreen
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountIdentityUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val response = """{"account":{"id":"1","username":"viewer","name":"Viewer Name","role":"owner"},"csrf_token":"current"}"""

    @Test fun identityPageShowsTheHostsNameUsernameAndRoleWithNoEmailRow() = runBlocking {
        val paths = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            paths += request.url.encodedPath
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile")
        try {
            controller.refreshAccountIdentity(); controller.job!!.join()
            assertEquals(listOf("/api/auth/me"), paths)
            val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
                AccountIdentityScreen(controller.state.value.accountIdentity, false, null, {})
            } }
            try {
                repeat(16) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
                val texts = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.map(::text)
                assertTrue(texts.contains("Name Viewer Name"))
                assertTrue(texts.contains("Username @viewer"))
                assertTrue(texts.contains("Role Owner"))
                assertTrue(texts.none { it == "Email" || it.startsWith("Email ") })
                assertFalse(texts.contains("Retry account"))
            } finally { scene.close() }
        } finally { remote.close() }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
