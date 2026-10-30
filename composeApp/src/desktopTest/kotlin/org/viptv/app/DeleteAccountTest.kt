package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class DeleteAccountTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = SavedRemoteSession("https://watch.example", "fixture-refresh")
        var cleared = false
        override suspend fun read(): SavedRemoteSession? = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null; cleared = true }
    }

    private val identity = AccountIdentity("1", "viewer", "Viewer Name", "owner")

    private val management = ProfileManagement(
        listOf(ServerProfile("1", "Viewer")),
        ProfileCapabilities(FeatureAvailability("Available"), FeatureAvailability("Available"), FeatureAvailability("Available"),
            FeatureAvailability("Available"), FeatureAvailability("Unsupported"), FeatureAvailability("Available")),
        ParentAccess(pin_configured = true, unlocked = true, restricted = false))

    @Test fun theTypedCredentialsReachTheHostAndTheSessionEndsLocallyOnNoContent() = runBlocking<Unit> {
        val store = Store()
        val bodies = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/auth/account", request.url.encodedPath)
            assertEquals(HttpMethod.Delete, request.method)
            bodies += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            respond("", HttpStatusCode.NoContent)
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, accountIdentity = identity,
                parentSettings = management)
            controller.deleteAccount("viewer", "fixture-password", "2468")
            controller.job?.join()
            assertEquals(listOf(JsonObject(mapOf("username" to JsonPrimitive("viewer"),
                "password" to JsonPrimitive("fixture-password"), "pin" to JsonPrimitive("2468")))), bodies)
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertEquals("Your account was deleted.", controller.state.value.error)
            assertNull(controller.connection)
            assertTrue(store.cleared, "the deleted account's device credential must not survive on this machine")
            assertFalse(controller.state.value.busy)
        } finally {
            scope.cancel(); remote.close()
        }
    }

    @Test fun anOwnerWithOtherAccountsIsToldWhyAndStaysSignedIn() = runBlocking<Unit> {
        val store = Store()
        val client = HttpClient(MockEngine {
            respond("""{"error":"Other accounts still need an owner. Have them delete their accounts first.","error_code":"last_owner"}""",
                HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, accountIdentity = identity,
                parentSettings = management)
            controller.deleteAccount("viewer", "fixture-password", "2468")
            controller.job?.join()
            assertEquals("Other accounts still need an owner. Have them delete their accounts first.", controller.state.value.error)
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertSame(remote, controller.connection)
            assertFalse(store.cleared)
            assertFalse(controller.state.value.busy)
        } finally {
            scope.cancel(); remote.close()
        }
    }

    @Test fun aWrongPinDeletesNothingAndKeepsTheSession() = runBlocking<Unit> {
        val store = Store()
        val client = HttpClient(MockEngine {
            respond("""{"error":"The parent PIN is not correct.","error_code":"parent_pin_incorrect"}""",
                HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, accountIdentity = identity,
                parentSettings = management)
            controller.deleteAccount("viewer", "fixture-password", "0000")
            controller.job?.join()
            assertEquals("The parent PIN is not correct.", controller.state.value.error)
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertSame(remote, controller.connection)
            assertFalse(store.cleared)
        } finally {
            scope.cancel(); remote.close()
        }
    }

    @Test fun aWrongPinAndRejectedCredentialsKeepTheirOwnSentences() {
        assertEquals("The parent PIN is not correct.", accountDeletionFailure(RemoteFailure(400, "", "parent_pin_incorrect")))
        assertEquals("The username or password is not correct.", accountDeletionFailure(RemoteFailure(400, "", "account_delete_rejected")))
        assertEquals("This account changed. Try again.", accountDeletionFailure(RemoteFailure(409, "", "account_changed")))
        assertEquals("Manage this from a signed-in phone, computer or the web dashboard.",
            accountDeletionFailure(RemoteFailure(403, "", "account_session_required")))
    }

    @Test fun anyOtherFailurePromisesNothingChanged() {
        assertEquals("Could not delete your account. Nothing was changed. Try again.", accountDeletionFailure(RemoteFailure(500, "Database operation failed")))
        assertEquals("Could not delete your account. Nothing was changed. Try again.", accountDeletionFailure(RemoteFailure(502, "")))
        assertEquals("Too many attempts. Try again in 30 s.", accountDeletionFailure(RemoteFailure(429, "Too many attempts. Try again in 30 s.", "rate_limited")))
    }
}