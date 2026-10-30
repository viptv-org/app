package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class LeavePinSwitchTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun bootstrap(profile: String) = buildJsonObject {
        put("identity", buildJsonObject { put("profiles", JsonArray(emptyList())); put("profile_id", profile) })
        put("profile_management", Json.encodeToJsonElement(parentFixture()))
    }.toString()

    private fun controller(scope: CoroutineScope,
        handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApplicationController {
        val client = HttpClient(MockEngine(handle)) { configureTransport() }
        return ApplicationController(scope, store).also {
            it.connection = RemoteConnection("https://watch.example", store, client, browserMode = true)
            it.mutable.value = ApplicationState(screen = Screen.Profiles, selectedProfile = "1",
                profiles = listOf(Profile("1", "Parent", null), Profile("2", "Child", null)), parentSettings = parentFixture())
        }
    }

    @Test fun switchingProfileAsksForTheLeavePinAndReportsWrongPinsAndTheLockout() = runBlocking<Unit> {
        var locked = false
        var current = "1"
        val attempts = mutableListOf<JsonObject>()
        val controller = controller(this) { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath == "/api/auth/profile") {
                val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                attempts += value
                val pin = value["pin"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                when {
                    locked -> respond("{\"error\":\"Too many PIN attempts. Try again in five minutes\"}",
                        HttpStatusCode.TooManyRequests, headers)
                    pin == null -> respond("{\"error\":\"Profile PIN required\",\"error_code\":\"leave_pin_required\"}",
                        HttpStatusCode.Forbidden, headers)
                    pin == "2468" -> { current = "2"; respond(bootstrap("2"), headers = headers) }
                    else -> respond("{\"error\":\"Incorrect profile PIN\",\"error_code\":\"leave_pin_invalid\"}",
                        HttpStatusCode.Forbidden, headers)
                }
            } else respond(bootstrap(current), headers = headers)
        }
        try {
            controller.selectProfile("2"); controller.job!!.join()
            assertEquals(Screen.ProfilePin, controller.state.value.screen)
            assertEquals("2", controller.state.value.profileToOpen)
            assertEquals("1", controller.state.value.leavePinFrom)
            assertNull(controller.state.value.error)
            assertNull(attempts.single()["pin"])

            controller.submitProfilePin("0000"); controller.job!!.join()
            assertEquals(Screen.ProfilePin, controller.state.value.screen)
            assertEquals("1", controller.state.value.leavePinFrom)
            assertEquals("That PIN was not accepted. Try again.", controller.state.value.error)
            assertEquals("0000", attempts.last()["pin"]!!.jsonPrimitive.content)

            locked = true
            controller.submitProfilePin("0000"); controller.job!!.join()
            assertEquals(Screen.ProfilePin, controller.state.value.screen)
            assertEquals("Too many PIN attempts. Try again in five minutes.", controller.state.value.error)

            locked = false
            controller.submitProfilePin("2468"); controller.job!!.join()
            assertEquals(Screen.Home, controller.state.value.screen)
            assertNull(controller.state.value.leavePinFrom)
            assertEquals("2", controller.state.value.selectedProfile)
            assertEquals("2468", attempts.last()["pin"]!!.jsonPrimitive.content)
        } finally { controller.connection?.close() }
    }

    @Test fun leavingThePinScreenForgetsTheLeavePrompt() = runBlocking<Unit> {
        val controller = controller(this) { request ->
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath == "/api/auth/profile")
                respond("{\"error\":\"Profile PIN required\",\"error_code\":\"leave_pin_required\"}", HttpStatusCode.Forbidden, headers)
            else respond(bootstrap("1"), headers = headers)
        }
        try {
            controller.selectProfile("2"); controller.job!!.join()
            assertEquals("1", controller.state.value.leavePinFrom)
            controller.navigateBack(); controller.job?.join()
            assertEquals(Screen.Profiles, controller.state.value.screen)
            assertNull(controller.state.value.leavePinFrom)
        } finally { controller.connection?.close() }
    }
}