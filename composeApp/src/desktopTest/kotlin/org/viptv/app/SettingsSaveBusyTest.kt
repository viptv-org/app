package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSaveBusyTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun client(handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handle)) { configureTransport() }

    private fun bootstrapJson() = buildJsonObject {
        put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", "2") })
        put("profile_management", Json.encodeToJsonElement(parentFixture(kids = true)))
        put("library", Json.encodeToJsonElement(CatalogPage(emptyList(), null, true)))
    }.toString()

    private fun settingsController(remote: RemoteConnection, scope: CoroutineScope): ApplicationController {
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "2",
            parentSettings = parentFixture(), profileManagement = parentFixture())
        return controller
    }

    @Test fun aSavedRestrictionEndsTheBusyTheSettingsPageStarted() = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            val body = if (request.method == HttpMethod.Put) Json.encodeToString(parentKids(true, 10)) else bootstrapJson()
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = settingsController(remote, this)
        try {
            controller.changeKidsPolicy("2", parentKids(true)); controller.job!!.join()
            assertNull(controller.state.value.error)
            assertFalse(controller.state.value.busy, "the write ended, so the page it disabled must end busy")
            assertFalse(controller.state.value.mutations.running(MutationKind.Preference))
        } finally { remote.close() }
    }

    @Test fun aRejectedRestrictionSaveEndsBusyAndKeepsItsRetry() = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method == HttpMethod.Put)
                respond("{}", HttpStatusCode.InternalServerError, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(bootstrapJson(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = settingsController(remote, this)
        try {
            controller.changeKidsPolicy("2", parentKids(true)); controller.job!!.join()
            assertNotNull(controller.state.value.mutations.failure(preferenceTarget(kidsPolicyTarget("2"))))
            assertFalse(controller.state.value.busy, "a failed save must not leave the page disabled")
        } finally { remote.close() }
    }

    @Test fun aCancelledRestrictionSaveEndsBusy() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, client { request ->
            if (request.method == HttpMethod.Put) {
                started.complete(Unit); release.await()
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond(bootstrapJson(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, browserMode = true)
        val controller = settingsController(remote, this)
        try {
            controller.changeKidsPolicy("2", parentKids(true)); started.await()
            assertTrue(controller.state.value.busy)
            controller.job!!.cancelAndJoin()
            assertFalse(controller.state.value.busy, "a cancelled save must not leave the page disabled")
        } finally { release.complete(Unit); remote.close() }
    }

    @Test fun everyOutcomeOfAWriteSettlesTheBusyItClaimed() = runTest {
        val target = preferenceTarget("busy-rule")
        fun controller() = ApplicationController(this, store).also {
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", busy = true)
        }
        val saved = controller()
        saved.mutate(target, ownsControllerJob = true) { }
        advanceUntilIdle()
        assertFalse(saved.state.value.busy, "a saved write settles the busy it claimed")

        val failed = controller()
        failed.mutate(target, ownsControllerJob = true) { throw RemoteFailure(500, "boom") }
        advanceUntilIdle()
        assertFalse(failed.state.value.busy, "a failed write settles the busy it claimed")

        val cancelled = controller()
        val held = CompletableDeferred<Unit>()
        cancelled.mutate(target, ownsControllerJob = true) { held.await() }
        advanceUntilIdle()
        assertTrue(cancelled.state.value.busy)
        cancelled.job!!.cancelAndJoin()
        advanceUntilIdle()
        assertFalse(cancelled.state.value.busy, "a cancelled write settles the busy it claimed")
    }
}