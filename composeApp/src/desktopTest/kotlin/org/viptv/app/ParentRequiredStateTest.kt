package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.CatalogPage
import kotlin.test.*

class ParentRequiredStateTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handle: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handle)) { configureTransport() }

    private fun bootstrap(management: ProfileManagement, selected: String) = buildJsonObject {
        put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", selected) })
        put("profile_management", Json.encodeToJsonElement(management))
        put("library", Json.encodeToJsonElement(CatalogPage(emptyList(), null, true)))
    }.toString()

    private fun locked() = parentFixture(kids = true, unlocked = false, restricted = true)

    @Test fun aGatedProfileWriteUnlocksTheHouseholdFirstAndThenWrites() = runBlocking<Unit> {
        val writes = EngineRequests<Pair<String, JsonObject>>()
        val open = parentFixture(kids = true, unlocked = true, restricted = true)
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.url.encodedPath == "/api/parent/unlock")
                writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            if (request.method == HttpMethod.Patch)
                writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val body = when (request.url.encodedPath) {
                "/api/profiles" -> Json.encodeToString(open)
                "/api/bootstrap" -> bootstrap(open, "2")
                else -> "{}"
            }
            respond(body, headers = json)
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2")
        try {
            controller.saveProfileName("Renamed", "", "4821"); controller.job!!.join()
            val sent = writes.toList()
            assertEquals("/api/parent/unlock", sent.firstOrNull()?.first, "the household unlock runs before the write")
            assertEquals("/api/profiles/2" to buildJsonObject { put("name", "Renamed") }, sent.last())
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aRefusedWriteNamesTheHouseholdPinAndKeepsItsCode() = runBlocking<Unit> {
        val remote = RemoteConnection("https://watch.example", store, client {
            respond("""{"error":"Parent PIN required","error_code":"parent_required"}""", HttpStatusCode.Forbidden, headers = json)
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2")
        try {
            controller.saveProfileName("Renamed", "", ""); controller.job!!.join()
            assertEquals("Enter the household PIN to change this profile.", controller.state.value.error)
            assertEquals("parent_required", controller.state.value.errorCode)
        } finally { remote.close() }
    }

    @Test fun everyRefusalKeepsItsOwnCopyInsteadOfOneGenericLine() = runBlocking<Unit> {
        val copy = mapOf(
            "parent_pin_invalid" to "Incorrect PIN. Try again.",
            "owner_required" to "Only the account owner can change household settings.",
            "account_session_required" to "Manage this from a signed-in phone, computer or the web dashboard.",
            "something_else" to "This action is not available for this profile.")
        for ((code, message) in copy) {
            val remote = RemoteConnection("https://watch.example", store, client {
                respond("""{"error":"refused","error_code":"$code"}""", HttpStatusCode.Forbidden, headers = json)
            }, browserMode = false)
            try {
                val failure = runCatching { remote.profileManagement() }.exceptionOrNull()
                assertIs<RemoteFailure>(failure, code)
                assertEquals(message, failure.message, code)
                assertEquals(code, failure.code, code)
            } finally { remote.close() }
        }
    }

    @Test fun switchingOffARestrictedProfileAsksForTheHouseholdPinFirst() = runBlocking<Unit> {
        val requests = EngineRequests<String>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            val body = when (request.url.encodedPath) {
                "/api/bootstrap" -> bootstrap(parentFixture(kids = false, unlocked = true), "1")
                "/api/profiles" -> Json.encodeToString(parentFixture(kids = true, unlocked = false, restricted = true))
                else -> "{}"
            }
            respond(body, headers = json)
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Profiles, selectedProfile = "2",
            profiles = listOf(Profile("1", "Parent", null), Profile("2", "Child", null)), profileManagement = locked())
        try {
            controller.selectProfile("1"); controller.job?.join()
            assertEquals("1", controller.state.value.householdPinFor, "the picker asks for the household PIN")
            assertEquals(Screen.Profiles, controller.state.value.screen)
            assertTrue(requests.isEmpty(), "nothing is sent until the PIN arrives: ${requests.toList()}")
            controller.submitHouseholdSwitch("4821"); controller.job!!.join()
            assertEquals(listOf("POST /api/parent/unlock", "POST /api/auth/profile"), requests.toList().take(2))
            assertNull(controller.state.value.householdPinFor)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aWrongHouseholdPinKeepsTheSwitchPromptOpenWithItsOwnCopy() = runBlocking<Unit> {
        val requests = EngineRequests<String>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            requests += request.url.encodedPath
            respond("""{"error":"Incorrect parent PIN","error_code":"parent_pin_invalid"}""", HttpStatusCode.Forbidden, headers = json)
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Profiles, selectedProfile = "2",
            profiles = listOf(Profile("1", "Parent", null), Profile("2", "Child", null)), profileManagement = locked())
        try {
            controller.selectProfile("1"); controller.job?.join()
            controller.submitHouseholdSwitch("0000"); controller.job!!.join()
            assertEquals("1", controller.state.value.householdPinFor, "the prompt stays open for another try")
            assertEquals("Incorrect PIN. Try again.", controller.state.value.error)
            assertEquals(Screen.Profiles, controller.state.value.screen)
            assertEquals(listOf("/api/parent/unlock"), requests.toList())
        } finally { remote.close() }
    }

    @Test fun aSwitchRefusedForTheMissingGrantAsksForThePinInsteadOfShowingTheError() = runBlocking<Unit> {
        var unlocked = false
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/parent/unlock" -> { unlocked = true; respond("{}", headers = json) }
                path == "/api/auth/profile" && !unlocked ->
                    respond("""{"error":"Parent PIN required","error_code":"parent_required"}""", HttpStatusCode.Forbidden, headers = json)
                path == "/api/bootstrap" -> respond(bootstrap(parentFixture(kids = false, unlocked = true), "1"), headers = json)
                else -> respond("{}", headers = json)
            }
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Profiles, selectedProfile = "2",
            profiles = listOf(Profile("1", "Parent", null), Profile("2", "Child", null)))
        try {
            controller.selectProfile("1"); controller.job!!.join()
            assertNull(controller.state.value.error, "a missing household grant is not a dead end")
            assertEquals("1", controller.state.value.householdPinFor)
            assertEquals(Screen.Profiles, controller.state.value.screen)
            controller.submitHouseholdSwitch("4821"); controller.job!!.join()
            assertNull(controller.state.value.householdPinFor)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aProfilePageSaveMarksItsDialogPendingUntilItLands() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method == HttpMethod.Patch) {
                started.complete(Unit); release.await()
                respond("{}", headers = json)
            } else respond(
                when (request.url.encodedPath) {
                    "/api/profiles" -> Json.encodeToString(parentFixture(kids = false, unlocked = true))
                    "/api/bootstrap" -> bootstrap(parentFixture(kids = false, unlocked = true), "1")
                    else -> "{}"
                }, headers = json)
        }, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.saveProfileName("Renamed", "")
            started.await()
            assertTrue(controller.state.value.mutations.isPending(profileMutationTarget(PROFILE_RENAME)),
                "the dialog's Save draws its pending label while the write is in flight")
            release.complete(Unit); controller.job!!.join()
            assertFalse(controller.state.value.mutations.isPending(profileMutationTarget(PROFILE_RENAME)))
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); remote.close() }
    }
}
