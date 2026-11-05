package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.app.updates.dismissUpdateRequired
import org.viptv.app.updates.updateRequiredNoticeAllowed
import kotlin.test.*

class ServerUpdateRequiredTest {
    private class Store : RemoteSessionStore {
        override suspend fun read() = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun remote(respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RemoteConnection("https://watch.example", Store(),
            HttpClient(MockEngine(respond)) { configureTransport() }, browserMode = false)

    private fun MockRequestHandleScope.updateRequiredAnswer() = respond(
        """{"error":"Update VIPTV to use this server's current catalog and playback APIs.","error_code":"client_update_required"}""",
        HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun controllerWith(remote: RemoteConnection, scope: CoroutineScope): ApplicationController {
        val controller = ApplicationController(scope, Store())
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home)
        return controller
    }

    @Test fun aServerThatRejectsThisBuildRaisesTheUpdateNotice() = runBlocking<Unit> {
        val remote = remote { updateRequiredAnswer() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controllerWith(remote, scope)
            controller.run { remote.profilePreferences("12") }
            controller.job?.join()
            assertTrue(controller.state.value.updateRequired)
            assertEquals(ServerMessages.UPDATE_APP, controller.state.value.error)
            assertTrue(controller.updateRequiredNoticeAllowed())

            controller.dismissUpdateRequired()
            assertFalse(controller.state.value.updateRequired)
        } finally { scope.cancel(); remote.close() }
    }

    @Test fun aBackgroundAnswerRaisesTheNoticeEvenWhenTheCallerSwallowsTheFailure() = runBlocking<Unit> {
        val remote = remote { updateRequiredAnswer() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controllerWith(remote, scope)
            try { remote.profilePreferences("12") } catch (_: RemoteFailure) { }
            assertTrue(controller.state.value.updateRequired)
        } finally { scope.cancel(); remote.close() }
    }

    @Test fun anotherFailureLeavesTheNoticeAlone() = runBlocking<Unit> {
        val remote = remote {
            respond("""{"error":"Title not found","error_code":"metadata_not_found"}""",
                HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controllerWith(remote, scope)
            controller.run { remote.profilePreferences("12") }
            controller.job?.join()
            assertFalse(controller.state.value.updateRequired)
        } finally { scope.cancel(); remote.close() }
    }

    @Test fun theNoticeStaysOffThePlayerButShowsOnSignIn() = runBlocking<Unit> {
        val remote = remote { updateRequiredAnswer() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = controllerWith(remote, scope)
            try { remote.profilePreferences("12") } catch (_: RemoteFailure) { }
            controller.mutable.value = controller.state.value.copy(screen = Screen.Player)
            assertFalse(controller.updateRequiredNoticeAllowed(), "Never over the player")
            controller.mutable.value = controller.state.value.copy(screen = Screen.Connect)
            assertTrue(controller.updateRequiredNoticeAllowed(), "The sign-in screen is where a stale client lands")
        } finally { scope.cancel(); remote.close() }
    }
}
