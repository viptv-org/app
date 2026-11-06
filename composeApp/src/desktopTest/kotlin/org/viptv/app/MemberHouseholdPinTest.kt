package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.PARENT_PIN_TARGET
import org.viptv.app.state.changeParentPin
import org.viptv.app.state.Screen
import org.viptv.app.state.preferenceTarget
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class MemberHouseholdPinTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun aMemberOpensTheHouseholdPinRowAndReadsItsStatus() {
        Host(role = "member").use { ui ->
            ui.render()
            assertTrue(ui.shows("Household PIN"), "a member keeps the row: ${ui.texts()}")
            ui.click("Household PIN")
            ui.waitFor({ ui.controller.state.value.parentSettings != null }, "the household status loads")
            assertTrue(ui.shows("Use a 4–8 digit PIN to protect leaving kids profiles. Turn off kids mode on all profiles before removing the PIN."))
            assertTrue(ui.shows("A PIN is set."), "a member reads the household's PIN status")
            assertTrue(ui.shows("Only the account owner can change household settings."), "the page names why it is read only")
            for (action in listOf("Change PIN", "Set PIN", "Remove PIN", "Unlock parental controls"))
                assertFalse(ui.shows(action), "a member is offered no household write: $action")
        }
    }

    @Test fun aMemberReadsThatNoPinIsSet() {
        Host(role = "member", configured = false).use { ui ->
            ui.render()
            ui.click("Household PIN")
            ui.waitFor({ ui.controller.state.value.parentSettings != null }, "the household status loads")
            assertTrue(ui.shows("No PIN is set."))
            assertTrue(ui.shows("Only the account owner can change household settings."))
            assertFalse(ui.shows("Set PIN"))
        }
    }

    @Test fun anOwnerKeepsTheHouseholdPinWrites() {
        Host(role = "owner").use { ui ->
            ui.render()
            ui.click("Household PIN")
            ui.waitFor({ ui.controller.state.value.parentSettings != null }, "the household status loads")
            assertTrue(ui.shows("A PIN is set."))
            assertTrue(ui.shows("Change PIN"))
            assertTrue(ui.shows("Remove PIN"))
            assertFalse(ui.shows("Only the account owner can change household settings."))
        }
    }

    @Test fun aPairedTelevisionStillGetsNoHouseholdPinRow() {
        Host(role = "device").use { ui ->
            ui.render()
            assertFalse(ui.shows("Household PIN"), "a device grant cannot read or write the household PIN: ${ui.texts()}")
        }
    }

    @Test fun parentalControlsLinksAMemberToTheHouseholdPinStatus() {
        Host(role = "member").use { ui ->
            ui.render()
            ui.click("Parental controls")
            ui.waitFor({ ui.controller.state.value.parentSettings != null }, "parental controls load")
            assertTrue(ui.shows("Household PIN in Account"), "a member keeps the way to the PIN status: ${ui.texts()}")
            ui.click("Household PIN in Account")
            assertTrue(ui.shows("A PIN is set."))
            assertTrue(ui.shows("Only the account owner can change household settings."))
        }
    }

    @Test fun aMembersHouseholdWriteRefusalNamesTheOwner() = runBlocking<Unit> {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine {
            respond("""{"error":"Only the account owner can change household settings.","error_code":"owner_required"}""",
                HttpStatusCode.Forbidden, json)
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        val controller = ApplicationController(this, NoSavedSession)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1",
            parentSettings = parentFixture(), accountRole = "member",
            accountIdentity = AccountIdentity("1", "viewer", "Viewer", "member"), manageAccount = false)
        try {
            controller.changeParentPin("4321", "5678"); controller.job?.join()
            assertContains(controller.state.value.mutations.failure(preferenceTarget(PARENT_PIN_TARGET)).orEmpty(),
                "Only the account owner can change household settings.")
        } finally { remote.close() }
    }

    private object NoSavedSession : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private inner class Host(role: String, configured: Boolean = true, width: Int = 390, height: Int = 1600) : AutoCloseable {
        private val json = headersOf(HttpHeaders.ContentType, "application/json")
        private val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/profiles" -> Json.encodeToString(parentFixture(configured = configured))
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, json)
        }) { configureTransport() }
        private val remote = RemoteConnection("https://watch.example", NoSavedSession, client, browserMode = false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", accountRole = role,
                accountIdentity = AccountIdentity("1", "viewer", "Viewer", role), manageAccount = false)
        }
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme { SettingsRoute(state, controller, browser = false, television = false, compact = width < 640) }
        }

        fun render(times: Int = 16) = repeat(times) { scene.render(frame++ * 16_666_667L).close() }
        fun waitFor(condition: () -> Boolean, what: String) {
            repeat(400) {
                if (condition()) { render(4); return }
                render(1)
            }
            fail("$what did not happen in time: ${texts()}")
        }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun shows(value: String) = value in texts()
        fun click(value: String) {
            val node = nodes().firstOrNull { node -> labelled(node, value) && node.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value' in ${texts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            render()
        }
        private fun labelled(node: SemanticsNode, value: String): Boolean {
            val held = text(node)
            return held == value || held.startsWith("$value ") ||
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        override fun close() { scene.close(); scope.cancel(); remote.close() }
    }
}